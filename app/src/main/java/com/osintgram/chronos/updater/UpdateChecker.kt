package com.osintgram.chronos.updater

import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val notes: String,
    /** Size of the APK on GitHub, used to check the download arrived whole. */
    val sizeBytes: Long = 0L,
)

/** What a check produced. Failures carry a reason, so the screen can say it. */
sealed interface UpdateResult {
    data class Found(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Checks the app's latest GitHub Release and reports whether a newer version
 * exists. Uses only the JDK HTTP client and org.json — no extra dependencies.
 *
 * Every failure is reported, never swallowed: a check that cannot reach GitHub
 * used to look exactly like "you are up to date", which is the worst possible
 * answer. Each branch is also written to the diagnostics log so a user report
 * can be traced to a cause.
 */
object UpdateChecker {

    const val REPO = "merry8989-uk/chronos-clock"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val USER_AGENT = "chronos-updater"

    private var lastFailure: String = ""
    private var repeated: Int = 0

    /**
     * A check that fails the same way every two seconds should not fill the log
     * with the same line. The first one is written, the repeats are counted, and
     * every tenth repeat says how many there have been.
     */
    private fun logFailure(reason: String) {
        synchronized(this) {
            if (reason == lastFailure) {
                repeated++
                if (repeated % 10 == 0) {
                    DiagnosticsLogger.warn("Updater", "still failing (${repeated}x): $reason")
                }
                return
            }
            lastFailure = reason
            repeated = 1
            DiagnosticsLogger.warn("Updater", "check failed: $reason")
        }
    }

    private fun clearFailure() {
        synchronized(this) {
            lastFailure = ""
            repeated = 0
        }
    }

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val started = System.currentTimeMillis()
            val code = conn.responseCode
            DiagnosticsLogger.log(
                "Net",
                "GET $LATEST_API -> $code in ${System.currentTimeMillis() - started} ms",
            )
            if (code != HttpURLConnection.HTTP_OK) {
                logFailure("HTTP $code")
                return@withContext UpdateResult.Failed(reasonForStatus(code))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val latest = VersionCompare.normalise(json.optString("tag_name"))
            if (latest.isEmpty()) {
                DiagnosticsLogger.log("Updater", "Check failed: no tag_name in the release")
                return@withContext UpdateResult.Failed("The latest release has no version tag.")
            }
            clearFailure()
            if (!isNewer(latest, currentVersion)) {
                DiagnosticsLogger.log("Updater", "Up to date: installed $currentVersion, latest $latest")
                return@withContext UpdateResult.UpToDate
            }

            val assets = json.optJSONArray("assets")
            val apkAsset = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            val apkUrl = apkAsset?.optString("browser_download_url").orEmpty()
            if (apkUrl.isBlank()) {
                DiagnosticsLogger.log("Updater", "Check failed: release $latest has no APK asset")
                return@withContext UpdateResult.Failed("Release $latest has no APK attached.")
            }

            DiagnosticsLogger.log("Updater", "Update available: $currentVersion -> $latest")
            UpdateResult.Found(
                UpdateInfo(
                    latestVersion = latest,
                    downloadUrl = apkUrl,
                    releaseUrl = json.optString("html_url"),
                    notes = json.optString("body"),
                    sizeBytes = apkAsset?.optLong("size", 0L) ?: 0L,
                ),
            )
        } catch (e: Exception) {
            logFailure("${e.javaClass.simpleName}: ${e.message}")
            UpdateResult.Failed(describe(e))
        } finally {
            conn?.disconnect()
        }
    }

    private fun reasonForStatus(code: Int): String = when {
        code == 403 || code == 429 ->
            "GitHub is rate-limiting the update check (HTTP $code). Try again in a few minutes."
        code == 404 -> "No release was found for this app (HTTP 404)."
        code in 500..599 -> "GitHub is having trouble (HTTP $code). Try again later."
        else -> "Update check failed (HTTP $code)."
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet connection — api.github.com could not be reached."
        is SocketTimeoutException -> "GitHub did not answer in time. Try again."
        is SSLException -> "The secure connection to GitHub failed."
        is IOException -> "Network error: ${e.message ?: "connection lost"}"
        else -> "Update check failed: ${e.message ?: e.javaClass.simpleName}"
    }

    /** True when [candidate] is a strictly higher dotted-numeric version. */
    fun isNewer(candidate: String, current: String): Boolean =
        VersionCompare.isNewer(candidate, current)
}

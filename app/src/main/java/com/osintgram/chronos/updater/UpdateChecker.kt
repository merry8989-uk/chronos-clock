package com.osintgram.chronos.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val notes: String,
)

/**
 * Checks the app's latest GitHub Release and reports whether a newer version
 * exists. Uses only the JDK HTTP client and org.json — no extra dependencies.
 */
object UpdateChecker {

    const val REPO = "merry8989-uk/chronos-clock"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"

    /** Returns update details if a newer release exists, otherwise null. */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "chronos-updater")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val latest = json.optString("tag_name").trim().removePrefix("v")
            if (latest.isEmpty() || !isNewer(latest, currentVersion)) return@withContext null

            val apkUrl = json.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?.optString("browser_download_url")
            } ?: return@withContext null

            UpdateInfo(
                latestVersion = latest,
                downloadUrl = apkUrl,
                releaseUrl = json.optString("html_url"),
                notes = json.optString("body"),
            )
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** True when [candidate] is a strictly higher dotted-numeric version. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').mapNotNull { it.toIntOrNull() }
        val b = current.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

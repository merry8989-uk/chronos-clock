package com.osintgram.chronos.updater

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Raised when Android has not been told to allow Chronos to install packages. */
class InstallPermissionRequired :
    Exception("Chronos is not allowed to install apps yet. Allow it, then update again.")

/** Raised when the APK could not be downloaded or is not a valid APK. */
class DownloadFailed(reason: String) : Exception(reason)

/**
 * Downloads the release APK and hands it to the system installer, so the
 * update installs over the current app without leaving the app (Rule 6).
 *
 * Three things are checked that were previously assumed:
 *  - the install permission, because without it the installer opens and does
 *    nothing at all;
 *  - every redirect hop, because GitHub serves the APK from a signed URL on a
 *    different host and a refused hop has to be reported, not swallowed;
 *  - the downloaded bytes, because a 5xx page or a captive portal arrives as a
 *    perfectly ordinary 200 and would otherwise be passed to the installer.
 */
object AppUpdater {

    private const val USER_AGENT = "chronos-updater"
    private const val MAX_REDIRECTS = 6

    /** Android 8+ needs the per-app "install unknown apps" switch. */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }

    /** Opens the system screen where Chronos can be allowed to install apps. */
    fun openInstallPermissionSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.onFailure {
        DiagnosticsLogger.log("Updater", "Install-permission screen unavailable: ${it.message}")
    }.getOrDefault(false)

    /** True when the active connection is Wi-Fi or Ethernet. */
    private fun onWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        DiagnosticsLogger.log("Updater", "Connection is Wi-Fi/Ethernet: $wifi")
        return wifi
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            val apk = File(context.cacheDir, "update-${info.latestVersion}.apk")
            try {
                if (!canInstallPackages(context)) throw InstallPermissionRequired()
                if (Settings.updateWifiOnly.value && !onWifi(context)) {
                    throw DownloadFailed(
                        "Update paused: \"Update over Wi-Fi only\" is on. Connect to Wi-Fi, " +
                            "or turn that option off in Settings.",
                    )
                }

                if (apk.exists()) apk.delete()
                val written = download(info.downloadUrl, apk)
                DiagnosticsLogger.log("Updater", "Downloaded ${apk.name}: $written bytes")

                if (!ApkFile.looksLikeApk(apk)) {
                    apk.delete()
                    throw DownloadFailed(
                        "The download was not an APK — probably a proxy or Wi-Fi login page. " +
                            "Try again on another connection.",
                    )
                }
                if (info.sizeBytes > 0 && apk.length() != info.sizeBytes) {
                    val actual = apk.length()
                    apk.delete()
                    throw DownloadFailed(
                        "The download was incomplete ($actual of ${info.sizeBytes} bytes). Try again.",
                    )
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apk,
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                DiagnosticsLogger.log("Updater", "Handed ${apk.name} to the installer")
                Result.success(Unit)
            } catch (e: Exception) {
                DiagnosticsLogger.log("Updater", "Update failed: ${e.javaClass.simpleName}: ${e.message}")
                if (e !is InstallPermissionRequired) apk.delete()
                Result.failure(e)
            }
        }

    /**
     * Follows redirects by hand so each hop is visible: GitHub answers with a
     * 302 to a signed URL on another host, and a proxy that refuses that hop
     * has to surface as a real error.
     */
    private fun download(url: String, target: File): Long {
        var current = url
        repeat(MAX_REDIRECTS) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 20_000
                readTimeout = 60_000
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank()) {
                        throw DownloadFailed("The download redirect had no target (HTTP $code).")
                    }
                    current = if (location.startsWith("http")) {
                        location
                    } else {
                        URL(URL(current), location).toString()
                    }
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw DownloadFailed("Download failed (HTTP $code).")
                }
                val expected = conn.contentLengthLong
                var written = 0L
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        written = input.copyTo(output)
                    }
                }
                if (expected > 0 && written != expected) {
                    throw DownloadFailed("Download stopped early ($written of $expected bytes).")
                }
                return written
            } finally {
                conn.disconnect()
            }
        }
        throw DownloadFailed("The download redirected too many times.")
    }
}

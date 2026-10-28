package com.osintgram.chronos.updater

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the release APK and hands it to the system installer, so the
 * update installs over the current app without leaving the app (Rule 6).
 */
object AppUpdater {

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
            try {
                if (Settings.updateWifiOnly.value && !onWifi(context)) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Update paused: Wi-Fi only is on. Connect to Wi-Fi, or turn off " +
                                "\"Update over Wi-Fi only\" in Settings.",
                        ),
                    )
                }
                val apk = File(context.cacheDir, "update-${info.latestVersion}.apk")

                val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "chronos-updater")
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                conn.inputStream.use { input ->
                    apk.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()

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
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

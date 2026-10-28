package com.osintgram.chronos.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the release APK and hands it to the system installer, so the
 * update installs over the current app without leaving the app (Rule 6).
 */
object AppUpdater {

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
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

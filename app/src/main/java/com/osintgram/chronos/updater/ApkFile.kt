package com.osintgram.chronos.updater

import java.io.File

/**
 * A downloaded file is only handed to the installer if it actually is an APK.
 *
 * A captive portal, a proxy error page or a 5xx body arrives as a perfectly
 * normal 200 response, so the status code alone proves nothing — the first two
 * bytes of a zip (and therefore of an APK) are "PK".
 */
object ApkFile {

    fun looksLikeApk(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            file.inputStream().use { stream ->
                val first = stream.read()
                val second = stream.read()
                first == 'P'.code && second == 'K'.code
            }
        }.getOrDefault(false)
    }
}

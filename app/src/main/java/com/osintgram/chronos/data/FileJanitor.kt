package com.osintgram.chronos.data

import android.content.Context
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import java.io.File

/**
 * Deletes files nothing points at any more: alarm wallpapers for alarms that no
 * longer exist, and snore clips for sleep sessions that were deleted. Without
 * this the app's private storage only ever grows.
 */
object FileJanitor {

    fun sweep(context: Context) {
        runCatching {
            val wallpapers = File(context.filesDir, "wallpapers")
            if (wallpapers.exists()) {
                val referenced = AlarmStore.all().flatMap { it.wallpaperPaths }.toSet()
                var freed = 0L
                wallpapers.listFiles()?.forEach { file ->
                    if (file.isFile && file.absolutePath !in referenced) {
                        freed += file.length()
                        file.delete()
                    }
                }
                if (freed > 0) {
                    DiagnosticsLogger.log("FileJanitor", "Removed ${freed / 1024} KB of unused wallpapers")
                }
            }

            val clips = File(context.filesDir, "sleep/clips")
            if (clips.exists()) {
                val referenced = SleepStore.all()
                    .flatMap { session -> session.snores.mapNotNull { it.clipPath } }
                    .toSet()
                var freed = 0L
                clips.listFiles()?.forEach { file ->
                    if (file.isFile && file.absolutePath !in referenced) {
                        freed += file.length()
                        file.delete()
                    }
                }
                if (freed > 0) {
                    DiagnosticsLogger.log("FileJanitor", "Removed ${freed / 1024} KB of old snore clips")
                }
            }
        }.onFailure {
            DiagnosticsLogger.log("FileJanitor", "Sweep failed: ${it.message}")
        }
    }
}

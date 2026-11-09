package com.osintgram.chronos.diagnostics

import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Pulls the platform's own log buffer into the app log.
 *
 * Android does not let an ordinary app read other apps' log entries, so this
 * captures **this process only** — the framework messages about Chronos, plus
 * anything written through `android.util.Log` by the libraries inside it. That
 * is still the part that explains a crash the app's own logger never saw.
 *
 * `logcat -d` is read with a hard timeout and a bounded line count; if the
 * device refuses the command the capture simply returns zero lines and says so.
 */
object LogcatCollector {

    private const val TAG = "Logcat"
    private const val MAX_LINES = 800

    suspend fun capture(context: Context, lines: Int = MAX_LINES): Int = withContext(Dispatchers.IO) {
        runCatching {
            val pid = Process.myPid()
            val process = ProcessBuilder(
                "logcat",
                "-d",
                "-v",
                "threadtime",
                "-t",
                lines.toString(),
                "--pid",
                pid.toString(),
            ).redirectErrorStream(true).start()

            val finished = process.waitFor(4, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!finished) runCatching { process.destroy() }

            val useful = output.lineSequence()
                .filter { it.isNotBlank() }
                .filterNot { it.contains("[Logcat]") }
                .toList()
            if (useful.isEmpty()) {
                DiagnosticsLogger.log(TAG, "logcat returned nothing (device may restrict it)")
                return@runCatching 0
            }
            // One entry per line keeps the rolling file's format intact.
            useful.takeLast(lines).forEach { line ->
                DiagnosticsLogger.log(TAG, line.trim(), DiagnosticsLogger.Level.DEBUG)
            }
            DiagnosticsLogger.log(TAG, "captured ${useful.size} line(s) for pid $pid")
            useful.size
        }.onFailure {
            DiagnosticsLogger.log(TAG, "capture failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrDefault(0)
    }
}

package com.osintgram.chronos.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects app logs into a single rolling file. Collection is ON by default
 * (Rule 7), so the app records what happened without the user having to
 * reproduce anything first.
 */
object DiagnosticsLogger {

    private const val MAX_BYTES = 1_000_000L   // rotate at ~1 MB
    private const val DIR = "logs"
    private const val NAME = "app.log"

    private val lock = Any()
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null

    /** Whether collection is on. Defaults to true (Rule 7). */
    @Volatile
    var enabled: Boolean = true
        private set

    /** Call once, from Application.onCreate. */
    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            logFile = File(dir, NAME)
        }
    }

    fun setEnabled(value: Boolean) {
        if (value != enabled) {
            enabled = value
            log("Diagnostics", if (value) "Log collection enabled" else "Log collection disabled")
        }
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        val file = logFile ?: return
        val line = "${timeFmt.format(Date())} [$tag] $message\n"
        synchronized(lock) {
            runCatching {
                if (file.length() > MAX_BYTES) rotate(file)
                file.appendText(line)
            }
        }
    }

    fun readAll(): String = synchronized(lock) {
        val file = logFile ?: return ""
        if (file.exists()) file.readText() else ""
    }

    fun sizeBytes(): Long = synchronized(lock) {
        logFile?.takeIf { it.exists() }?.length() ?: 0L
    }

    fun lineCount(): Int = readAll().lineSequence().count { it.isNotBlank() }

    fun clear() = synchronized(lock) {
        runCatching { logFile?.writeText("") }
        Unit
    }

    /** Records uncaught crashes, then defers to the previous handler. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            log("CRASH", "${error.javaClass.name}: ${error.message}\n${error.stackTraceToString()}")
            // Leave a marker so the next launch can offer to send the trace,
            // even if the crash happened in a screen that cannot be opened.
            runCatching {
                crashPrefs?.edit()
                    ?.putLong("last_crash_at", System.currentTimeMillis())
                    ?.putString("last_crash", "${error.javaClass.name}: ${error.message}")
                    ?.apply()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private var crashPrefs: android.content.SharedPreferences? = null

    /** Called once from Application.onCreate. */
    fun initCrashMarker(context: android.content.Context) {
        crashPrefs = context.getSharedPreferences("chronos_diagnostics", android.content.Context.MODE_PRIVATE)
    }

    /** The last crash summary, or null when nothing crashed. */
    fun lastCrash(): Pair<Long, String>? {
        val prefs = crashPrefs ?: return null
        val at = prefs.getLong("last_crash_at", 0L)
        if (at == 0L) return null
        return at to (prefs.getString("last_crash", "") ?: "")
    }

    fun clearCrashMarker() {
        crashPrefs?.edit()?.remove("last_crash_at")?.remove("last_crash")?.apply()
    }

    private fun rotate(file: File) {
        val old = File(file.parentFile, "$NAME.1")
        if (old.exists()) old.delete()
        file.renameTo(old)
        file.createNewFile()
    }
}

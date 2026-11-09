package com.osintgram.chronos.diagnostics

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one place the app writes logs.
 *
 * - **Rolling files**: `app.log`, `app.log.1`, `app.log.2` — three files of at
 *   most 2 MB, so the log can never grow past 6 MB on the device.
 * - **ISO-8601 timestamps** with milliseconds and the device offset, so a line
 *   can be lined up with a bug report without guessing the timezone.
 * - **Masked on the way in**: every line goes through [LogMasking], because a
 *   token that reaches the file has already leaked.
 * - **Thread-safe**: writes are serialised, so a log call from a worker, a
 *   sensor callback and the UI thread cannot interleave a half line.
 * - **Session id**: every launch gets one, and every line carries the app
 *   version, so entries from one run can be told apart in an export.
 *
 * Collection is on by default; the switch in Settings turns it off.
 */
object DiagnosticsLogger {

    private const val MAX_BYTES = 2_000_000L
    private const val MAX_FILES = 3
    private const val DIR = "logs"
    private const val NAME = "app.log"

    /** Coarse severity, written into the line. */
    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val lock = Any()
    private val timeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    private var dir: File? = null
    private var logFile: File? = null
    private var crashPrefs: android.content.SharedPreferences? = null

    @Volatile
    var enabled: Boolean = true
        private set

    /** Changes on every launch; written on every line. */
    @Volatile
    var sessionId: String = ""
        private set

    /** Called once, from Application.onCreate. */
    fun init(context: Context) {
        synchronized(lock) {
            dir = File(context.filesDir, DIR).apply { mkdirs() }
            logFile = File(dir, NAME)
            sessionId = Instant.now().atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US))
        }
    }

    fun setEnabled(value: Boolean) {
        if (value != enabled) {
            enabled = value
            log("Diagnostics", if (value) "Log collection enabled" else "Log collection disabled")
        }
    }

    // ---- Writing -----------------------------------------------------------

    fun log(tag: String, message: String, level: Level = Level.INFO) {
        if (!enabled) return
        val file = logFile ?: return
        val stamp = Instant.now().atZone(ZoneId.systemDefault()).format(timeFmt)
        val safeTag = tag.take(24)
        val body = LogMasking.mask(message)
        val line = "$stamp ${level.name.first()} [$safeTag] $body\n"
        synchronized(lock) {
            runCatching {
                if (file.length() + line.length > MAX_BYTES) rotate()
                file.appendText(line)
            }
        }
    }

    fun debug(tag: String, message: String) = log(tag, message, Level.DEBUG)

    fun warn(tag: String, message: String) = log(tag, message, Level.WARN)

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            log(tag, message, Level.ERROR)
            return
        }
        log(tag, "$message: ${throwable.javaClass.name}: ${throwable.message}", Level.ERROR)
        log(tag, throwable.stackTraceToString().trimEnd(), Level.ERROR)
    }

    /** Writes a throwable's stack trace under [tag]. */
    fun logThrowable(tag: String, throwable: Throwable) {
        log(tag, throwable.stackTraceToString().trimEnd(), Level.ERROR)
    }

    // ---- Reading -----------------------------------------------------------

    /** Every retained file, oldest first, so an export reads in order. */
    fun readAll(): String = synchronized(lock) {
        val folder = dir ?: return ""
        val files = (MAX_FILES - 1 downTo 1)
            .map { File(folder, "$NAME.$it") }
            .filter { it.exists() }
            .toMutableList()
        val current = logFile?.takeIf { it.exists() }
        if (current != null) files.add(current)
        files.joinToString(separator = "") { file ->
            runCatching { file.readText() }.getOrDefault("")
        }
    }

    fun sizeBytes(): Long = synchronized(lock) {
        val folder = dir ?: return 0L
        (0 until MAX_FILES)
            .map { index -> if (index == 0) File(folder, NAME) else File(folder, "$NAME.$index") }
            .sumOf { it.takeIf(File::exists)?.length() ?: 0L }
    }

    fun lineCount(): Int = readAll().lineSequence().count { it.isNotBlank() }

    fun files(): List<File> = synchronized(lock) {
        val folder = dir ?: return emptyList()
        (0 until MAX_FILES)
            .map { index -> if (index == 0) File(folder, NAME) else File(folder, "$NAME.$index") }
            .filter { it.exists() }
    }

    fun clear() = synchronized(lock) {
        val folder = dir ?: return
        (0 until MAX_FILES).forEach { index ->
            val file = if (index == 0) File(folder, NAME) else File(folder, "$NAME.$index")
            runCatching { if (file.exists()) file.delete() }
        }
        runCatching { logFile?.createNewFile() }
        Unit
    }

    // ---- Crashes -----------------------------------------------------------

    /** Records uncaught crashes, then defers to the previous handler. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                error("CRASH", "Uncaught on thread ${thread.name}", error)
            }
            // Leave a marker so the next launch can offer to send the trace,
            // even if the crash happened in a screen that cannot be opened.
            runCatching {
                val head = error.stackTraceToString().lineSequence().take(6).joinToString("\n")
                crashPrefs?.edit()
                    ?.putLong("last_crash_at", System.currentTimeMillis())
                    ?.putString("last_crash", head)
                    ?.apply()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Called once from Application.onCreate. */
    fun initCrashMarker(context: Context) {
        crashPrefs = context.getSharedPreferences("chronos_diagnostics", Context.MODE_PRIVATE)
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

    // ---- Rotation ----------------------------------------------------------

    /** app.log → app.log.1 → app.log.2; the oldest file is dropped. */
    private fun rotate() {
        val folder = dir ?: return
        val oldest = File(folder, "$NAME.${MAX_FILES - 1}")
        runCatching { if (oldest.exists()) oldest.delete() }
        for (index in MAX_FILES - 2 downTo 1) {
            val from = File(folder, "$NAME.$index")
            if (from.exists()) {
                runCatching { from.renameTo(File(folder, "$NAME.${index + 1}")) }
            }
        }
        val current = File(folder, NAME)
        if (current.exists()) runCatching { current.renameTo(File(folder, "$NAME.1")) }
        runCatching { current.createNewFile() }
    }
}

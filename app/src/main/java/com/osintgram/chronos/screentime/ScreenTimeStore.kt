package com.osintgram.chronos.screentime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import org.json.JSONObject
import java.time.LocalDate

/**
 * The two things UsageStatsManager cannot tell us: how many notifications each
 * app posted, and the daily limits the user set.
 *
 * Notification **counts only** are stored — never the title, text or sender of
 * a notification. That is the difference between a screen-time feature and a
 * notification spy.
 */
object ScreenTimeStore {

    private const val PREFS = "chronos_screentime"
    private const val KEY_COUNTS = "notification_counts"
    private const val KEY_LIMITS = "limits"
    private const val KEEP_DAYS = 14

    private var prefs: SharedPreferences? = null

    /** package → daily limit in minutes. */
    private val limits: SnapshotStateMap<String, Int> = mutableStateMapOf()

    /** date (ISO) → package → count. */
    private val counts: SnapshotStateMap<String, Map<String, Int>> = mutableStateMapOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        limits.clear()
        counts.clear()
        readLimits().forEach { (pkg, minutes) -> limits[pkg] = minutes }
        readCounts().forEach { (date, map) -> counts[date] = map }
        DiagnosticsLogger.log(
            "ScreenTime",
            "Loaded ${limits.size} limit(s) and ${counts.size} day(s) of notification counts",
        )
    }

    // ---- Notification counts ----------------------------------------------

    fun notificationsFor(date: String = todayKey()): Map<String, Int> = counts[date].orEmpty()

    fun recordNotification(packageName: String, at: Long = System.currentTimeMillis()) {
        if (packageName.isBlank()) return
        val date = java.time.Instant.ofEpochMilli(at)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(ScreenTimeLogic.ISO_DATE)
        val current = counts[date].orEmpty().toMutableMap()
        current[packageName] = (current[packageName] ?: 0) + 1
        counts[date] = current
        persistCounts()
    }

    fun clearCounts() {
        counts.clear()
        persistCounts()
    }

    // ---- Limits ------------------------------------------------------------

    fun limitFor(packageName: String): Int? = limits[packageName]

    fun setLimit(packageName: String, minutes: Int) {
        if (minutes <= 0) {
            limits.remove(packageName)
        } else {
            limits[packageName] = minutes.coerceIn(5, 12 * 60)
        }
        persistLimits()
        DiagnosticsLogger.log("ScreenTime", "Limit for $packageName = ${limits[packageName] ?: 0} min")
    }

    fun removeLimit(packageName: String) {
        limits.remove(packageName)
        persistLimits()
    }

    fun allLimits(): Map<String, Int> = limits.toMap()

    // ---- Enforcement health ------------------------------------------------
    // Read-only views over state the watcher owns. They live here rather than in
    // the screen so the Limits tab can say why a limit is not firing without
    // knowing how enforcement works, and so the answer is one function to test.

    /**
     * The SharedPreferences key the watcher must write on every poll.
     *
     * It is public on purpose: [LimitService][com.osintgram.chronos.screentime.LimitService]
     * is the writer and it lives in another file, so the key cannot be private to
     * this object. The writer must `putLong` it into the `chronos_screentime`
     * preferences (this object's [PREFS] file) on every tick; the readers below
     * treat a heartbeat older than two minutes as a watcher that is not running.
     */
    const val KEY_WATCHER_TICK = "watcher_last_tick"

    /** How stale a heartbeat may be before the watcher counts as stopped. */
    private const val WATCHER_STALE_MILLIS = 2 * 60_000L

    /** The package fragment that identifies an accessibility service as ours. */
    private const val SELF_PACKAGE_FRAGMENT = "com.osintgram.chronos"

    /**
     * Whether Chronos' own accessibility service is switched on.
     *
     * Exists because the accessibility route is the only way the app is told an
     * app came to the front at the moment it happens, rather than on the
     * watcher's next poll — so "the limit is late" and "the limit never fires"
     * both need this answer, and the screen should not be the place that knows
     * how an AccessibilityManager is asked.
     *
     * @return true when any enabled service's id belongs to this package.
     */
    fun accessibilityBlockingEnabled(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return@runCatching false
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info -> info?.id?.contains(SELF_PACKAGE_FRAGMENT, ignoreCase = true) == true }
    }.getOrDefault(false)

    /**
     * When the watcher last reported that it was alive, as an epoch millisecond.
     *
     * Exists because `ActivityManager.getRunningServices` is deprecated and was
     * never reliable across OEMs, so the app cannot ask the system whether its
     * foreground service is up. A timestamp the service writes itself is the
     * only honest answer available, and the screen needs it to explain a limit
     * that is not firing.
     *
     * @return 0L when no heartbeat has ever been written — which reads as "not
     *   running", because a watcher that has never ticked has never blocked.
     */
    fun watcherLastTick(context: Context): Long = runCatching {
        val store = prefs ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        store.getLong(KEY_WATCHER_TICK, 0L)
    }.getOrDefault(0L)

    /**
     * Whether the watcher looks alive right now.
     *
     * Exists so the Limits tab can separate "the switch is off" from "the
     * switch is on and the service was killed" — two failures with the same
     * symptom and different fixes. A clock that jumped backwards is treated as
     * alive rather than stopped, because a stale-looking heartbeat from a future
     * timestamp is not evidence that the service died.
     */
    fun watcherRunning(context: Context): Boolean {
        val last = watcherLastTick(context)
        if (last <= 0L) return false
        return System.currentTimeMillis() - last <= WATCHER_STALE_MILLIS
    }

    // ---- Helpers -----------------------------------------------------------

    /** The running day, which starts at the hour set in Settings (7 am by default). */
    fun todayKey(): String = DayBoundary.todayKey()

    private fun readLimits(): Map<String, Int> = runCatching {
        val raw = prefs?.getString(KEY_LIMITS, null) ?: return emptyMap()
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { json.optInt(it, 0) }
            .filterValues { it > 0 }
    }.getOrDefault(emptyMap())

    private fun readCounts(): Map<String, Map<String, Int>> = runCatching {
        val raw = prefs?.getString(KEY_COUNTS, null) ?: return emptyMap()
        val json = JSONObject(raw)
        val out = mutableMapOf<String, Map<String, Int>>()
        json.keys().asSequence().forEach { date ->
            val day = json.optJSONObject(date) ?: return@forEach
            out[date] = day.keys().asSequence().associateWith { pkg -> day.optInt(pkg, 0) }
        }
        // Only keep the last two weeks.
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS.toLong()).format(ScreenTimeLogic.ISO_DATE)
        out.filterKeys { it >= cutoff }
    }.getOrDefault(emptyMap())

    private fun persistLimits() {
        val json = JSONObject()
        limits.forEach { (pkg, minutes) -> json.put(pkg, minutes) }
        prefs?.edit()?.putString(KEY_LIMITS, json.toString())?.apply()
    }

    private fun persistCounts() {
        val json = JSONObject()
        counts.forEach { (date, map) ->
            val day = JSONObject()
            map.forEach { (pkg, count) -> day.put(pkg, count) }
            json.put(date, day)
        }
        prefs?.edit()?.putString(KEY_COUNTS, json.toString())?.apply()
    }

    fun clearAll() {
        limits.clear()
        counts.clear()
        prefs?.edit()?.clear()?.apply()
    }
}

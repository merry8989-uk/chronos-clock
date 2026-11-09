package com.osintgram.chronos.screentime

import android.content.Context
import android.content.SharedPreferences
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

    // ---- Helpers -----------------------------------------------------------

    fun todayKey(): String = LocalDate.now().format(ScreenTimeLogic.ISO_DATE)

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

package com.osintgram.chronos.screentime

import android.content.Context
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * The three ways out of a limit, and the numbers behind them.
 *
 * A blocked app must stay blocked until the user makes one of three deliberate
 * choices — a few more minutes now, off for the rest of today, or a higher
 * limit — so those choices live here, keyed to the day that is running (which
 * starts at [DayBoundary.startHour], not at midnight).
 *
 * This is deliberately its own small store rather than more fields in
 * [ScreenTimeStore]: the escape hatches are enforcement state, and keeping them
 * apart means a bug in one cannot corrupt the limits themselves.
 */
object LimitEnforcement {

    private const val PREFS = "chronos_limits"
    private const val KEY_BONUS_PREFIX = "bonus:"
    private const val KEY_PAUSED_PREFIX = "paused:"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Minutes the user granted themselves today, on top of the limit. */
    fun bonusMinutes(context: Context, packageName: String, day: String = DayBoundary.todayKey()): Int =
        prefs(context).getInt(KEY_BONUS_PREFIX + day + ":" + packageName, 0).coerceAtLeast(0)

    /** "10 more minutes" — the smallest escape hatch, and the one asked for. */
    fun addBonus(context: Context, packageName: String, minutes: Int = 10) {
        if (minutes <= 0) return
        val day = DayBoundary.todayKey()
        val next = bonusMinutes(context, packageName, day) + minutes
        prefs(context).edit().putInt(KEY_BONUS_PREFIX + day + ":" + packageName, next).apply()
        DiagnosticsLogger.log(
            "LimitService",
            "Granted $minutes more minute(s) for $packageName today (bonus now ${next}m)",
        )
    }

    /** True when the user turned this limit off for the rest of the day. */
    fun pausedForToday(context: Context, packageName: String, day: String = DayBoundary.todayKey()): Boolean =
        prefs(context).getString(KEY_PAUSED_PREFIX + packageName, null) == day

    /** "Off for today" — enforced until the day boundary, then it comes back. */
    fun pauseForToday(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_PAUSED_PREFIX + packageName, DayBoundary.todayKey()).apply()
        DiagnosticsLogger.log("LimitService", "$packageName paused for the rest of the day")
    }

    /** Clears both hatches, for when a limit is raised or removed. */
    fun clear(context: Context, packageName: String) {
        val day = DayBoundary.todayKey()
        prefs(context).edit()
            .remove(KEY_BONUS_PREFIX + day + ":" + packageName)
            .remove(KEY_PAUSED_PREFIX + packageName)
            .apply()
    }

    /**
     * The limit that actually applies right now: the stored limit plus today's
     * bonus, or null when this app is not limited at all.
     */
    fun effectiveLimitMinutes(context: Context, packageName: String): Int? {
        val limit = ScreenTimeStore.limitFor(packageName) ?: return null
        return limit + bonusMinutes(context, packageName)
    }

    /** True when this package must not be usable right now. */
    fun isBlocked(context: Context, packageName: String, usedMillis: Long): Boolean {
        if (pausedForToday(context, packageName)) return false
        val limit = effectiveLimitMinutes(context, packageName) ?: return false
        return ScreenTimeLogic.limitReached(usedMillis, limit)
    }
}

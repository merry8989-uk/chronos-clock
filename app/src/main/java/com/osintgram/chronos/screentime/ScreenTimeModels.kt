package com.osintgram.chronos.screentime

/** One app's usage for a day. */
data class AppUsage(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val launches: Int,
    val notifications: Int = 0,
) {
    val minutes: Long get() = foregroundMillis / 60_000L
}

/** Everything the Screen time tab shows for one day. */
data class DayScreenTime(
    val totalForegroundMillis: Long = 0L,
    /** Times the screen was switched on. */
    val screenOnCount: Int = 0,
    /** Times the keyguard was dismissed. */
    val unlockCount: Int = 0,
    val apps: List<AppUsage> = emptyList(),
) {
    val topApps: List<AppUsage> get() = apps.sortedByDescending { it.foregroundMillis }.take(5)
    val mostUsed: AppUsage? get() = apps.maxByOrNull { it.foregroundMillis }
}

/** The maths behind the screen, kept free of Android so it can be unit-tested. */
object ScreenTimeLogic {

    val ISO_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

    /** "1 h 24 m", "7 m", "0 m" — the way the cards read. */
    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0 m"
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "$minutes m"
            minutes == 0L -> "$hours h"
            else -> "$hours h $minutes m"
        }
    }

    /** True when today's use of an app is at or past its limit. */
    fun limitReached(usedMillis: Long, limitMinutes: Int): Boolean =
        limitMinutes > 0 && usedMillis >= limitMinutes * 60_000L

    /** How much of the limit is used, 0f..1f. */
    fun limitProgress(usedMillis: Long, limitMinutes: Int): Float {
        if (limitMinutes <= 0) return 0f
        return (usedMillis.toFloat() / (limitMinutes * 60_000f)).coerceIn(0f, 1f)
    }

    /** Apps with any foreground time, biggest first. */
    fun sortByUsage(apps: List<AppUsage>): List<AppUsage> =
        apps.filter { it.foregroundMillis > 0 || it.launches > 0 || it.notifications > 0 }
            .sortedByDescending { it.foregroundMillis }

    /** Share of the day's screen time an app took, 0f..1f. */
    fun share(app: AppUsage, totalMillis: Long): Float {
        if (totalMillis <= 0L) return 0f
        return (app.foregroundMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    }
}

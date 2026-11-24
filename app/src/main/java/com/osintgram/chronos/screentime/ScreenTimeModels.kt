package com.osintgram.chronos.screentime

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

/**
 * One uninterrupted stretch of one app being in front of the user.
 *
 * Kept as its own type because "how long was I in this app" is a different
 * question from "how much time did this app get today": a single 50-minute
 * stretch and ten 5-minute visits are the same total and a very different
 * habit, and only the stretch answers the first question.
 */
data class AppSession(
    val packageName: String,
    val label: String,
    val startMillis: Long,
    val durationMillis: Long,
)

/**
 * A stretch of foreground time as the usage event stream described it.
 *
 * This is the raw material for the hourly chart and the session figures. It
 * carries the package so the longest stretch can name its app, and nothing
 * else, so the maths over it stays free of Android and can be tested.
 */
data class UsageSegment(
    val startMillis: Long,
    val endMillis: Long,
    val packageName: String = "",
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/**
 * One day of the seven-day trend row.
 *
 * The letter and the short name are resolved where the date is built, so the
 * chart never has to know about calendars, and so a test can build a week out
 * of plain values.
 */
data class DayTotal(
    val dayStartMillis: Long,
    val letter: String,
    val name: String,
    val foregroundMillis: Long,
    val isToday: Boolean = false,
)

/**
 * The last seven days of screen time, with the two summaries the trend card
 * shows under the bars.
 *
 * The average deliberately counts only the days that had any use: a week with
 * two unused days should not read as a lighter week than it was, and a fresh
 * install should not read as a screen-time collapse.
 */
data class WeekUsage(
    val days: List<DayTotal> = emptyList(),
    val averageActiveMillis: Long = 0L,
    val busiest: DayTotal? = null,
)

/** Everything the Screen time tab shows for one day. */
data class DayScreenTime(
    val totalForegroundMillis: Long = 0L,
    /** Times the screen was switched on. */
    val screenOnCount: Int = 0,
    /** Times the keyguard was dismissed. */
    val unlockCount: Int = 0,
    val apps: List<AppUsage> = emptyList(),
    /**
     * Foreground millis for each hour of the day, 24 entries starting at the
     * start of the day, read off the event stream. Empty when nothing could be
     * read, which the screen treats as "no chart" rather than as zeros.
     */
    val hourlyMillis: List<Long> = emptyList(),
    /** The first keyguard dismissal today, null when the phone has not been unlocked. */
    val firstUnlockMillis: Long? = null,
    /** The most recent keyguard dismissal today. */
    val lastUnlockMillis: Long? = null,
    /** The single longest stretch in one app today, ties going to the earlier one. */
    val longestSession: AppSession? = null,
    /** Stretches longer than [ScreenTimeLogic.SESSION_THRESHOLD_MILLIS] today. */
    val sessionsOverFiveMinutes: Int = 0,
) {
    val topApps: List<AppUsage> get() = apps.sortedByDescending { it.foregroundMillis }.take(5)
    val mostUsed: AppUsage? get() = apps.maxByOrNull { it.foregroundMillis }

    /** True when the hourly read produced a usable 24-bucket day. */
    val hasHourly: Boolean get() = hourlyMillis.size == ScreenTimeLogic.HOURS_PER_DAY
}

/** The maths behind the screen, kept free of Android so it can be unit-tested. */
object ScreenTimeLogic {

    /** Buckets in the hourly chart, and hours in the day it covers. */
    const val HOURS_PER_DAY = 24

    /** One hour in millis, the width of an hourly bucket. */
    const val HOUR_MILLIS = 3_600_000L

    /** One day in millis, the span the hourly buckets cover. */
    const val DAY_MILLIS = 86_400_000L

    /**
     * What counts as a long session on the card: five minutes. It is the
     * threshold Digital Wellbeing-style screens use for "picked the phone up
     * and stayed", and it is a constant here so the card and its test cannot
     * drift apart.
     */
    const val SESSION_THRESHOLD_MILLIS = 5 * 60_000L

    val ISO_DATE: DateTimeFormatter
        get() = com.osintgram.chronos.common.Dates.ISO

    /** "1 h 24 m", "7 m", "0 m" — the way the cards read. */
    fun formatDuration(millis: Long): String =
        com.osintgram.chronos.common.Dates.hoursMinutes(millis / 60_000L)

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

    /**
     * Spreads foreground stretches into 24 hourly buckets starting at
     * [dayStartMillis], so the chart can show *when* the phone was used rather
     * than only how much.
     *
     * Stretches are clipped to the day: a stretch that began before midnight
     * contributes only its part after [dayStartMillis], one that runs past the
     * day contributes only its part before the day ends, and a stretch is split
     * across the hour boundaries it crosses. Anything with a non-positive
     * length, and any stretch lying wholly outside the day, contributes
     * nothing — so the caller can hand over a window that is not exactly a day
     * without the buckets going wrong.
     */
    fun hourlyBuckets(segments: List<UsageSegment>, dayStartMillis: Long): List<Long> {
        val buckets = LongArray(HOURS_PER_DAY)
        val dayEnd = dayStartMillis + DAY_MILLIS
        for (segment in segments) {
            var from = maxOf(segment.startMillis, dayStartMillis)
            val to = minOf(segment.endMillis, dayEnd)
            while (from < to) {
                val hour = ((from - dayStartMillis) / HOUR_MILLIS).toInt()
                    .coerceIn(0, HOURS_PER_DAY - 1)
                val hourEnd = dayStartMillis + (hour + 1) * HOUR_MILLIS
                val sliceEnd = minOf(to, hourEnd)
                buckets[hour] += sliceEnd - from
                from = sliceEnd
            }
        }
        return buckets.toList()
    }

    /**
     * Bars drawn as fractions of the tallest bar, so a quiet day and a heavy
     * day both fill the chart and the shape stays readable.
     *
     * A list with no use at all gives all zeros, which the chart draws as the
     * empty-hour stubs instead of dividing by zero.
     */
    fun barFractions(values: List<Long>): List<Float> {
        val peak = values.maxOrNull() ?: 0L
        if (peak <= 0L) return values.map { 0f }
        return values.map { (it.toFloat() / peak).coerceIn(0f, 1f) }
    }

    /**
     * The average of the days that had any use.
     *
     * Empty days are left out on purpose: averaging them in would make a week
     * with two days away from the phone look like a week of lighter use, which
     * is the opposite of what happened.
     */
    fun averageActiveDay(totals: List<Long>): Long {
        val active = totals.filter { it > 0L }
        if (active.isEmpty()) return 0L
        return active.sum() / active.size
    }

    /**
     * The heaviest day of the week, ignoring days with no use, ties going to the
     * earlier day. Null when nothing was recorded, so the card can stay quiet
     * instead of naming a busiest day of zero.
     */
    fun busiestDay(days: List<DayTotal>): DayTotal? =
        days.filter { it.foregroundMillis > 0L }.maxByOrNull { it.foregroundMillis }

    /**
     * A week of per-day totals plus the two summaries the trend card shows.
     *
     * The per-day numbers themselves come from UsageStatsManager; this is only
     * the arithmetic on top of them, which is why it lives here and not in the
     * reader.
     */
    fun week(days: List<DayTotal>): WeekUsage = WeekUsage(
        days = days,
        averageActiveMillis = averageActiveDay(days.map { it.foregroundMillis }),
        busiest = busiestDay(days),
    )

    /**
     * The longest single stretch in one app, or null when nothing was recorded.
     *
     * Ties go to the stretch that happened first, so the same day always names
     * the same session and the card does not shuffle between refreshes.
     */
    fun longestSession(segments: List<UsageSegment>): UsageSegment? {
        var best: UsageSegment? = null
        for (segment in segments) {
            if (segment.durationMillis <= 0L) continue
            val bestDuration = best?.durationMillis ?: -1L
            if (segment.durationMillis > bestDuration) best = segment
        }
        return best
    }

    /**
     * How many stretches ran longer than [thresholdMillis].
     *
     * The threshold is strict: a stretch of exactly five minutes is a normal
     * glance, not a long sit, and counting it would inflate the number on a
     * phone that is checked often.
     */
    fun sessionsLongerThan(
        segments: List<UsageSegment>,
        thresholdMillis: Long = SESSION_THRESHOLD_MILLIS,
    ): Int = segments.count { it.durationMillis > thresholdMillis }

    /**
     * "9:41 pm", or "21:41" when the app is set to a 24-hour clock.
     *
     * Pickup times follow the app's own 12/24-hour setting rather than a fixed
     * format, so they read the same as the alarms and the world clock; the
     * meridiem is lower case to match the alarm cards.
     */
    fun clockTime(
        millis: Long,
        use24Hour: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        val pattern = if (use24Hour) "HH:mm" else "h:mm"
        val text = time.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        if (use24Hour) return text
        val meridiem = if (time.hour < 12) "am" else "pm"
        return "$text $meridiem"
    }

    /** "M" — the letter under a bar in the seven-day row. */
    fun dayLetter(date: LocalDate): String = date.dayOfWeek.name.take(1)

    /** "Mon" — how the busiest day is named under the trend row. */
    fun dayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}

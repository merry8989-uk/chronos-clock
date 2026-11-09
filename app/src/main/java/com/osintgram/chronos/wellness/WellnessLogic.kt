package com.osintgram.chronos.wellness

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Every calculation the wellness feature makes, kept free of Android types so
 * it can be unit-tested on the JVM. The store and the screens call these, they
 * never do the maths themselves.
 */
object WellnessLogic {

    val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Step counter values are cumulative since boot, so a day needs a baseline. */
    const val DEFAULT_STEP_GOAL = 8000
    const val DEFAULT_WATER_GOAL_ML = 2500
    const val DEFAULT_SLEEP_GOAL_MINUTES = 450

    fun todayKey(today: LocalDate = LocalDate.now()): String = today.format(ISO_DATE)

    fun parseDate(key: String): LocalDate? =
        runCatching { LocalDate.parse(key, ISO_DATE) }.getOrNull()

    /** Whole minutes between two instants, floored, never negative. */
    fun minutesBetween(startMillis: Long, endMillis: Long): Int {
        val diff = endMillis - startMillis
        if (diff <= 0L) return 0
        return (diff / 60_000L).toInt()
    }

    /**
     * Steps taken today. The platform sensor reports the count since boot, so
     * the first reading of the day is the baseline and everything after it is
     * today's walking. A reading below the baseline means the device rebooted
     * and the counter restarted, so the baseline has to move.
     */
    fun stepDelta(cumulative: Long, baseline: Long?): Int {
        if (baseline == null) return 0
        val delta = cumulative - baseline
        return if (delta <= 0L) 0 else delta.toInt()
    }

    /** True when the sensor restarted (reboot) or the baseline is missing. */
    fun needsRebaseline(cumulative: Long, baseline: Long?): Boolean =
        baseline == null || cumulative < baseline

    /** The last [count] dates ending today, oldest first. */
    fun recentDays(today: LocalDate = LocalDate.now(), count: Int = 7): List<LocalDate> {
        if (count <= 0) return emptyList()
        return (count - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }

    /**
     * A day counts towards the streak when something was logged: steps above
     * zero, water, sleep, a habit or a finished workout.
     */
    fun weekTotals(
        days: List<DayLog>,
        workouts: List<WorkoutSession>,
        meditations: List<MeditationSession>,
        windowStart: Long,
        windowEnd: Long,
    ): WeekTotals {
        val sleepDays = days.count { it.sleepMinutes > 0 }
        val sleepTotal = days.sumOf { it.sleepMinutes }
        val finished = workouts.filter { it.endedAt != null && it.startedAt in windowStart..windowEnd }
        val meditationMinutes = meditations
            .filter { it.completedAt in windowStart..windowEnd }
            .sumOf { it.minutes }
        return WeekTotals(
            steps = days.sumOf { it.steps },
            waterMl = days.sumOf { it.waterMl },
            sleepMinutesAverage = if (sleepDays == 0) 0 else sleepTotal / sleepDays,
            workoutCount = finished.size,
            workoutMinutes = finished.sumOf { it.minutes },
            meditationMinutes = meditationMinutes,
            volumeKg = finished.sumOf { it.volumeKg },
            daysWithSteps = days.count { it.steps > 0 },
        )
    }

    /**
     * Consecutive days with activity, counting back from [today]. A day with
     * nothing logged breaks the streak; today itself may be empty without
     * breaking it yet, because the day is not over.
     */
    fun streak(activeDates: Set<String>, today: LocalDate = LocalDate.now()): Int {
        if (activeDates.isEmpty()) return 0
        var cursor = today
        var count = 0
        if (todayKey(cursor) !in activeDates) cursor = cursor.minusDays(1)
        while (todayKey(cursor) in activeDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * Folds a Health Connect reading into a day. Values only ever go up: a sync
     * must never wipe a number the user typed in by hand.
     */
    fun mergeHealthSnapshot(
        day: DayLog,
        steps: Int?,
        waterMl: Int?,
        sleepMinutes: Int?,
    ): DayLog = day.copy(
        steps = maxOf(day.steps, steps ?: 0),
        waterMl = maxOf(day.waterMl, waterMl ?: 0),
        sleepMinutes = maxOf(day.sleepMinutes, sleepMinutes ?: 0),
    )

    /** A day is "active" when any health number or habit was recorded. */
    fun isActiveDay(day: DayLog, finishedWorkoutDates: Set<String>): Boolean =
        !day.isEmpty || day.date in finishedWorkoutDates

    /** Resting-ish heart rate band used for the colour of the reading. */
    fun heartRateBand(bpm: Int): String = when {
        bpm <= 0 -> "none"
        bpm < 50 -> "low"
        bpm <= 100 -> "normal"
        bpm <= 140 -> "elevated"
        else -> "high"
    }

    /** Water progress as 0f..1f, safe when the goal is zero. */
    fun progress(value: Int, goal: Int): Float {
        if (goal <= 0) return 0f
        return (value.toFloat() / goal).coerceIn(0f, 1f)
    }

    fun progress(value: Double, goal: Double): Float {
        if (goal <= 0.0) return 0f
        return (value / goal).toFloat().coerceIn(0f, 1f)
    }

    /** Minutes as "1 h 20 m" / "45 m", the way the cards read. */
    fun formatMinutes(total: Int): String {
        if (total <= 0) return "0 m"
        val hours = total / 60
        val minutes = total % 60
        return when {
            hours == 0 -> "$minutes m"
            minutes == 0 -> "$hours h"
            else -> "$hours h $minutes m"
        }
    }

    /** Water in millilitres as litres with one decimal: 1750 → "1.8 L". */
    fun formatLitres(ml: Int): String = String.format("%.1f L", ml / 1000.0)

    /** Steps as a grouped number: 8240 → "8,240". */
    fun formatSteps(steps: Int): String {
        val s = steps.coerceAtLeast(0).toString()
        val out = StringBuilder()
        s.forEachIndexed { index, c ->
            if (index > 0 && (s.length - index) % 3 == 0) out.append(',')
            out.append(c)
        }
        return out.toString()
    }
}

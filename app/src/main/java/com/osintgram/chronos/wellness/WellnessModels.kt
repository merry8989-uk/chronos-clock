package com.osintgram.chronos.wellness

/**
 * One movement inside a workout session. Weight is stored in kilograms and is
 * zero for bodyweight work, so volume maths stays meaningful.
 */
data class WorkoutEntry(
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double = 0.0,
)

/**
 * A workout session. It is written to the store the moment it starts, so
 * leaving the tab, rotating the screen or closing the app never loses it.
 */
data class WorkoutSession(
    val id: Long,
    val planId: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val entries: List<WorkoutEntry> = emptyList(),
    val note: String = "",
) {
    val isFinished: Boolean get() = endedAt != null

    /** Whole minutes actually spent, minimum one once the session is closed. */
    val minutes: Int
        get() {
            val end = endedAt ?: return 0
            return WellnessLogic.minutesBetween(startedAt, end)
        }

    /** Sets × reps × weight, the standard way to compare sessions. */
    val volumeKg: Double
        get() = entries.sumOf { it.sets.toDouble() * it.reps * it.weightKg }
}

/** A completed meditation or breathing session. */
data class MeditationSession(
    val id: Long,
    val minutes: Int,
    val patternId: String,
    val completedAt: Long,
)

/**
 * One calendar day of health numbers, keyed by ISO date in the device
 * timezone. Steps come from the step sensor or Health Connect, water and
 * sleep are entered by hand, and [habits] holds the habit ids ticked that day.
 */
data class DayLog(
    val date: String,
    val steps: Int = 0,
    val waterMl: Int = 0,
    val sleepMinutes: Int = 0,
    val habits: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = steps == 0 && waterMl == 0 && sleepMinutes == 0 && habits.isEmpty()
}

/** Totals for the last seven days, the numbers the Progress tab shows. */
data class WeekTotals(
    val steps: Int = 0,
    val waterMl: Int = 0,
    val sleepMinutesAverage: Int = 0,
    val workoutCount: Int = 0,
    val workoutMinutes: Int = 0,
    val meditationMinutes: Int = 0,
    val volumeKg: Double = 0.0,
    val daysWithSteps: Int = 0,
)

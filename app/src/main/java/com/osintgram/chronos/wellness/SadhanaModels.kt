package com.osintgram.chronos.wellness

/**
 * The practices offered under Sadhana.
 *
 * Each one is a practice with its own way of being measured, not a different
 * skin over one timer: Trataka is measured in seconds of unbroken gaze, Japa in
 * rounds of 108, a silent sit in minutes. The list is data so the screen can be
 * one list, and so a practice can be added without touching the UI.
 */
enum class SadhanaKind(
    val id: String,
    val title: String,
    val subtitle: String,
    /** The length the practice opens with, in seconds. Zero means open-ended. */
    val defaultSeconds: Int,
    /** How the result is counted, for the history line. */
    val measure: Measure,
) {
    Trataka(
        id = "trataka",
        title = "Trataka",
        subtitle = "Steady gaze on one point, without blinking",
        defaultSeconds = 60,
        measure = Measure.Gaze,
    ),
    Japa(
        id = "japa",
        title = "Japa",
        subtitle = "Mantra repetition, 108 beads to a round",
        defaultSeconds = 0,
        measure = Measure.Rounds,
    ),
    Dhyana(
        id = "dhyana",
        title = "Dhyana",
        subtitle = "Silent sitting, eyes closed",
        defaultSeconds = 600,
        measure = Measure.Time,
    ),
    Mauna(
        id = "mauna",
        title = "Mauna",
        subtitle = "Silence, kept for the whole sit",
        defaultSeconds = 900,
        measure = Measure.Time,
    ),
    YogaNidra(
        id = "yoga_nidra",
        title = "Yoga Nidra",
        subtitle = "Resting awareness, lying down",
        defaultSeconds = 1200,
        measure = Measure.Time,
    ),
    Chakra(
        id = "chakra",
        title = "Chakra",
        subtitle = "Seven centres, one at a time",
        defaultSeconds = 420,
        measure = Measure.Time,
    ),
    Om(
        id = "om",
        title = "Om chanting",
        subtitle = "A long chant, opened and closed by the bell",
        defaultSeconds = 600,
        measure = Measure.Time,
    );

    /** How a finished session is summarised. */
    enum class Measure { Gaze, Rounds, Time }
}

/**
 * One finished practice.
 *
 * [seconds] is the time actually held, not the time planned: a Trataka session
 * that ended at the first blink records those few seconds, because the point of
 * the practice is the honest number.
 */
data class SadhanaSession(
    val id: Long,
    val kind: String,
    val startedAt: Long,
    val seconds: Int,
    /** Blinks counted during a gaze practice; zero for the others. */
    val blinks: Int = 0,
    /** Mala rounds completed during Japa; zero for the others. */
    val rounds: Int = 0,
) {
    val kindOf: SadhanaKind? get() = SadhanaKind.entries.firstOrNull { it.id == kind }

    /** A one-line result, in the units the practice is measured in. */
    val summary: String
        get() = when (kindOf?.measure) {
            SadhanaKind.Measure.Gaze -> if (blinks == 0) {
                "${formatSeconds(seconds)} unbroken"
            } else {
                "${formatSeconds(seconds)}, $blinks blink${if (blinks == 1) "" else "s"}"
            }
            SadhanaKind.Measure.Rounds -> "$rounds round${if (rounds == 1) "" else "s"}"
            else -> formatSeconds(seconds)
        }

    companion object {
        /** "4 m 20 s" / "42 s" — the way a hold reads. */
        fun formatSeconds(total: Int): String {
            val safe = total.coerceAtLeast(0)
            val minutes = safe / 60
            val seconds = safe % 60
            return when {
                minutes == 0 -> "$seconds s"
                seconds == 0 -> "$minutes m"
                else -> "$minutes m $seconds s"
            }
        }
    }
}

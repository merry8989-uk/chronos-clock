package com.osintgram.chronos.wellness

/**
 * A guided practice: a sequence of steps someone can actually follow.
 *
 * The point of this model is that a step is not a paragraph of advice — it is
 * one thing to do, for a known length of time, with a short cue that stays on
 * screen. That is what makes a session followable while your eyes are on a
 * posture or closed in meditation.
 */
data class GuidedStep(
    /** What to do, in the imperative: "Step the right foot back". */
    val title: String,
    /** One or two sentences of how, with the alignment that keeps it safe. */
    val instruction: String,
    /** How long this step lasts, in seconds. */
    val seconds: Int,
    /** A short line that stays on screen: the breath cue or the key alignment. */
    val cue: String = "",
)

/** What kind of practice a guided session is, for the library's grouping. */
enum class GuidedKind(val label: String) {
    Yoga("Yoga"),
    Meditation("Meditation"),
    Breath("Breath"),
}

/**
 * A whole practice, start to finish.
 *
 * [note] carries the caution that belongs to the practice itself — the one line
 * that should be read before starting, not buried in a step.
 */
data class GuidedSession(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: GuidedKind,
    val level: String,
    val steps: List<GuidedStep>,
    val note: String = "",
) {
    val totalSeconds: Int get() = steps.sumOf { it.seconds }

    /** "12 min" / "45 s", for the library list. */
    val lengthLabel: String get() = when {
        totalSeconds < 60 -> "$totalSeconds s"
        totalSeconds % 60 == 0 -> "${totalSeconds / 60} min"
        else -> "${totalSeconds / 60} min ${totalSeconds % 60} s"
    }
}

/**
 * Every guided practice in the app.
 *
 * The content lives in two files — [GuidedYoga] and [GuidedMeditation] — so a
 * practice can be corrected without touching the player, and the player can be
 * changed without touching a single instruction.
 */
object GuidedLibrary {

    fun all(): List<GuidedSession> = GuidedYoga.sessions + GuidedMeditation.sessions

    fun byKind(kind: GuidedKind): List<GuidedSession> = all().filter { it.kind == kind }

    fun find(id: String): GuidedSession? = all().firstOrNull { it.id == id }
}

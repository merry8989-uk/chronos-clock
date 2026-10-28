package com.osintgram.chronos.data

import android.content.Context
import android.net.Uri
import com.osintgram.chronos.R

/**
 * The bundled alarm tones. Each one is a real audio file shipped inside the
 * app, grouped by character so the picker can offer categories.
 */
object Tones {

    data class Tone(
        val id: String,
        val title: String,
        val category: String,
        val resId: Int,
        val resName: String,
    )

    const val CATEGORY_CHEERFUL = "Cheerful"
    const val CATEGORY_CALM = "Calm"
    const val CATEGORY_BELL = "Alarm bell"
    const val CATEGORY_LOUD = "Loud"

    val categories = listOf(CATEGORY_CHEERFUL, CATEGORY_CALM, CATEGORY_BELL, CATEGORY_LOUD)

    val all: List<Tone> = listOf(
        Tone("morning_chime", "Morning Chime", CATEGORY_CHEERFUL, R.raw.tone_morning_chime, "tone_morning_chime"),
        Tone("sunrise_bells", "Sunrise Bells", CATEGORY_CHEERFUL, R.raw.tone_sunrise_bells, "tone_sunrise_bells"),
        Tone("soft_pulse", "Soft Pulse", CATEGORY_CALM, R.raw.tone_soft_pulse, "tone_soft_pulse"),
        Tone("gentle_waves", "Gentle Waves", CATEGORY_CALM, R.raw.tone_gentle_waves, "tone_gentle_waves"),
        Tone("classic_bell", "Classic Bell", CATEGORY_BELL, R.raw.tone_classic_bell, "tone_classic_bell"),
        Tone("double_beep", "Double Beep", CATEGORY_BELL, R.raw.tone_double_beep, "tone_double_beep"),
        Tone("siren_rise", "Siren Rise", CATEGORY_LOUD, R.raw.tone_siren_rise, "tone_siren_rise"),
        Tone("hard_alarm", "Hard Alarm", CATEGORY_LOUD, R.raw.tone_hard_alarm, "tone_hard_alarm"),
    )

    fun byId(id: String): Tone? = all.firstOrNull { it.id == id }

    fun inCategory(category: String): List<Tone> = all.filter { it.category == category }

    /** The URI stored on an alarm for a bundled tone. */
    fun uriFor(context: Context, tone: Tone): String =
        "android.resource://${context.packageName}/raw/${tone.resName}"

    fun toneForUri(uri: String?): Tone? {
        if (uri == null) return null
        return all.firstOrNull { uri.endsWith("/raw/${it.resName}") }
    }
}

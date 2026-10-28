package com.osintgram.chronos.timer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * One-tap timer presets. The four starter presets are ordinary durations the
 * user can delete; anything they save themselves is added to the same list.
 */
object TimerPresets {

    data class Preset(val name: String, val seconds: Long)

    private const val PREFS = "chronos_timer_presets"
    private const val KEY = "presets"

    private val starters = listOf(
        Preset("Meeting", 20 * 60),
        Preset("Sleep", 10 * 60),
        Preset("Exercise", 15 * 60),
        Preset("Tea", 4 * 60),
    )

    private var prefs: SharedPreferences? = null
    private val presets: SnapshotStateList<Preset> = mutableStateListOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs?.getString(KEY, null)
        presets.clear()
        if (stored == null) {
            presets.addAll(starters)
        } else {
            presets.addAll(
                stored.split(";").mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val seconds = parts[1].toLongOrNull() ?: return@mapNotNull null
                        Preset(parts[0], seconds)
                    } else null
                }.filter { it.name.isNotBlank() && it.seconds > 0 },
            )
        }
    }

    fun all(): List<Preset> = presets.toList()

    fun add(preset: Preset) {
        if (preset.seconds <= 0) return
        presets.removeAll { it.name.equals(preset.name, ignoreCase = true) }
        presets.add(preset)
        persist()
    }

    fun remove(preset: Preset) {
        presets.remove(preset)
        persist()
    }

    private fun persist() {
        prefs?.edit()
            ?.putString(KEY, presets.joinToString(";") { "${it.name}:${it.seconds}" })
            ?.apply()
    }
}

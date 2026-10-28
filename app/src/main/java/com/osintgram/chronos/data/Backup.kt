package com.osintgram.chronos.data

import android.content.Context
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backup and restore of everything the app holds: alarms, settings, world
 * clock cities and custom holiday dates. Written as a single readable JSON
 * file the user picks with the system file picker.
 */
object Backup {

    private const val FORMAT_VERSION = 1

    fun export(context: Context, versionName: String): File {
        val root = JSONObject()
            .put("app", "Chronos")
            .put("formatVersion", FORMAT_VERSION)
            .put("appVersion", versionName)
            .put("exportedAt", System.currentTimeMillis())
            .put("alarms", AlarmStore.asJson())

        val settingsJson = JSONObject()
        Settings.snapshot().forEach { (k, v) -> settingsJson.put(k, v) }
        root.put("settings", settingsJson)

        root.put("worldClock", JSONArray(WorldClockStore.all()))
        root.put("holidays", JSONArray(HolidayStore.customDates()))

        val presets = JSONArray()
        com.osintgram.chronos.timer.TimerPresets.all().forEach { preset ->
            presets.put(
                JSONObject().put("name", preset.name).put("seconds", preset.seconds),
            )
        }
        root.put("timerPresets", presets)


        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "chronos-backup.json")
        file.writeText(root.toString(2))
        DiagnosticsLogger.log("Backup", "Exported backup to ${file.name}")
        return file
    }

    /** Returns a short summary of what was restored, or null on failure. */
    fun restore(context: Context, text: String): String? {
        return runCatching {
            val root = JSONObject(text)
            val alarms = root.optJSONArray("alarms") ?: JSONArray()
            AlarmStore.importJson(alarms)

            root.optJSONObject("settings")?.let { s ->
                val map = mutableMapOf<String, Any>()
                s.keys().forEach { key -> map[key] = s.get(key) }
                Settings.restore(map)
            }

            root.optJSONArray("worldClock")?.let { arr ->
                val zones = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { z -> z.isNotBlank() } }
                if (zones.isNotEmpty()) {
                    WorldClockStore.all().forEach { WorldClockStore.remove(it) }
                    zones.forEach { WorldClockStore.add(it) }
                }
            }

            root.optJSONArray("holidays")?.let { arr ->
                HolidayStore.customDates().forEach { HolidayStore.removeCustom(it) }
                for (i in 0 until arr.length()) {
                    val iso = arr.optString(i)
                    runCatching { java.time.LocalDate.parse(iso) }.getOrNull()
                        ?.let { HolidayStore.addCustom(it) }
                }
            }

            root.optJSONArray("timerPresets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    val seconds = o.optLong("seconds")
                    if (name.isNotBlank() && seconds > 0) {
                        com.osintgram.chronos.timer.TimerPresets.add(
                            com.osintgram.chronos.timer.TimerPresets.Preset(name, seconds),
                        )
                    }
                }
            }

            DiagnosticsLogger.log("Backup", "Restored ${alarms.length()} alarm(s) from backup")
            "${alarms.length()} alarm(s) and all settings restored"
        }.getOrElse {
            DiagnosticsLogger.log("Backup", "Restore failed: ${it.message}")
            null
        }
    }
}

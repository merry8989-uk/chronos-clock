package com.osintgram.chronos.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One alarm. [repeatDays] uses ISO day numbers (1 = Monday ... 7 = Sunday);
 * an empty set means the alarm fires once and then switches itself off.
 */
data class Alarm(
    val id: Long = 0L,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val skipHolidays: Boolean = false,
    val deleteAfterRing: Boolean = false,
    val soundUri: String? = null,
    val volumeRamp: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {

    /** "Every day", "Mon to Fri", "Weekends", "Mon, Wed, Fri" or "Once". */
    fun repeatSummary(): String {
        if (repeatDays.isEmpty()) return "Once"
        if (repeatDays.size == 7) return "Every day"
        if (repeatDays == setOf(1, 2, 3, 4, 5)) return "Mon to Fri"
        if (repeatDays == setOf(6, 7)) return "Weekends"
        return repeatDays.sorted().joinToString(", ") { dayShort(it) }
    }

    fun timeText(use24Hour: Boolean): String =
        if (use24Hour) String.format("%02d:%02d", hour, minute)
        else {
            val h = if (hour % 12 == 0) 12 else hour % 12
            String.format("%02d:%02d", h, minute)
        }

    fun meridiem(use24Hour: Boolean): String = when {
        use24Hour -> ""
        hour < 12 -> "am"
        else -> "pm"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("label", label)
        .put("enabled", enabled)
        .put("repeatDays", JSONArray(repeatDays.sorted()))
        .put("vibrate", vibrate)
        .put("snoozeMinutes", snoozeMinutes)
        .put("skipHolidays", skipHolidays)
        .put("deleteAfterRing", deleteAfterRing)
        .put("soundUri", soundUri ?: JSONObject.NULL)
        .put("volumeRamp", volumeRamp)
        .put("createdAt", createdAt)

    companion object {

        fun dayShort(iso: Int): String = when (iso) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            else -> "Sun"
        }

        fun fromJson(o: JSONObject): Alarm {
            val days = mutableSetOf<Int>()
            o.optJSONArray("repeatDays")?.let { arr ->
                for (i in 0 until arr.length()) days.add(arr.optInt(i))
            }
            return Alarm(
                id = o.optLong("id"),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                label = o.optString("label", ""),
                enabled = o.optBoolean("enabled", true),
                repeatDays = days,
                vibrate = o.optBoolean("vibrate", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                skipHolidays = o.optBoolean("skipHolidays", false),
                deleteAfterRing = o.optBoolean("deleteAfterRing", false),
                soundUri = if (o.isNull("soundUri")) null else o.optString("soundUri").takeIf { it.isNotEmpty() },
                volumeRamp = o.optBoolean("volumeRamp", true),
                createdAt = o.optLong("createdAt"),
            )
        }

        /**
         * The next moment this alarm should ring, as epoch millis, or null when
         * it can never fire. Holidays are skipped when the alarm asks for it.
         */
        fun nextTriggerMillis(
            alarm: Alarm,
            now: ZonedDateTime = ZonedDateTime.now(),
            holidays: Set<LocalDate> = emptySet(),
        ): Long? {
            val zone: ZoneId = now.zone
            for (offset in 0..400) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (alarm.repeatDays.isNotEmpty()) {
                    val dow: DayOfWeek = date.dayOfWeek
                    if (dow.value !in alarm.repeatDays) continue
                }
                if (alarm.skipHolidays && date in holidays) continue
                val candidate: LocalDateTime = date.atTime(alarm.hour, alarm.minute)
                val zoned = candidate.atZone(zone)
                if (zoned.isAfter(now)) return zoned.toInstant().toEpochMilli()
            }
            return null
        }
    }
}

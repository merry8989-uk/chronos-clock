package com.osintgram.chronos.wellness

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON for the wellness records. Kept apart from the store so it holds no
 * Android dependency and can be round-trip tested on the JVM. Unknown or
 * damaged records are skipped rather than crashing the app on launch.
 */
object WellnessCodec {

    private const val FORMAT_VERSION = 1

    fun workoutsToJson(list: List<WorkoutSession>): JSONArray {
        val array = JSONArray()
        list.forEach { session ->
            val entries = JSONArray()
            session.entries.forEach { entry ->
                entries.put(
                    JSONObject()
                        .put("name", entry.name)
                        .put("sets", entry.sets)
                        .put("reps", entry.reps)
                        .put("weightKg", entry.weightKg),
                )
            }
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("planId", session.planId)
                    .put("title", session.title)
                    .put("startedAt", session.startedAt)
                    .put("endedAt", session.endedAt ?: JSONObject.NULL)
                    .put("note", session.note)
                    .put("entries", entries),
            )
        }
        return array
    }

    fun workoutsFromJson(array: JSONArray?): List<WorkoutSession> {
        if (array == null) return emptyList()
        val out = mutableListOf<WorkoutSession>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val title = o.optString("title")
            if (id <= 0L || title.isBlank()) continue
            val entries = mutableListOf<WorkoutEntry>()
            val raw = o.optJSONArray("entries")
            if (raw != null) {
                for (j in 0 until raw.length()) {
                    val e = raw.optJSONObject(j) ?: continue
                    val name = e.optString("name")
                    if (name.isBlank()) continue
                    entries.add(
                        WorkoutEntry(
                            name = name,
                            sets = e.optInt("sets", 0).coerceAtLeast(0),
                            reps = e.optInt("reps", 0).coerceAtLeast(0),
                            weightKg = e.optDouble("weightKg", 0.0).coerceAtLeast(0.0),
                        ),
                    )
                }
            }
            out.add(
                WorkoutSession(
                    id = id,
                    planId = o.optString("planId", "custom"),
                    title = title,
                    startedAt = o.optLong("startedAt", 0L),
                    endedAt = if (o.isNull("endedAt")) null else o.optLong("endedAt", 0L),
                    entries = entries,
                    note = o.optString("note", ""),
                ),
            )
        }
        return out
    }

    fun meditationsToJson(list: List<MeditationSession>): JSONArray {
        val array = JSONArray()
        list.forEach { session ->
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("minutes", session.minutes)
                    .put("patternId", session.patternId)
                    .put("completedAt", session.completedAt),
            )
        }
        return array
    }

    fun meditationsFromJson(array: JSONArray?): List<MeditationSession> {
        if (array == null) return emptyList()
        val out = mutableListOf<MeditationSession>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val minutes = o.optInt("minutes", 0)
            if (id <= 0L || minutes <= 0) continue
            out.add(
                MeditationSession(
                    id = id,
                    minutes = minutes,
                    patternId = o.optString("patternId", "calm"),
                    completedAt = o.optLong("completedAt", 0L),
                ),
            )
        }
        return out
    }

    fun daysToJson(list: List<DayLog>): JSONArray {
        val array = JSONArray()
        list.forEach { day ->
            array.put(
                JSONObject()
                    .put("date", day.date)
                    .put("steps", day.steps)
                    .put("waterMl", day.waterMl)
                    .put("sleepMinutes", day.sleepMinutes)
                    .put("habits", JSONArray(day.habits.toList()))
                    .put("heartRateBpm", day.heartRateBpm)
                    .put("heartRateAt", day.heartRateAt),
            )
        }
        return array
    }

    fun daysFromJson(array: JSONArray?): List<DayLog> {
        if (array == null) return emptyList()
        val out = mutableListOf<DayLog>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val date = o.optString("date")
            if (WellnessLogic.parseDate(date) == null) continue
            val habits = mutableSetOf<String>()
            o.optJSONArray("habits")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val habit = arr.optString(j)
                    if (habit.isNotBlank()) habits.add(habit)
                }
            }
            out.add(
                DayLog(
                    date = date,
                    steps = o.optInt("steps", 0).coerceAtLeast(0),
                    waterMl = o.optInt("waterMl", 0).coerceAtLeast(0),
                    sleepMinutes = o.optInt("sleepMinutes", 0).coerceAtLeast(0),
                    habits = habits,
                    heartRateBpm = o.optInt("heartRateBpm", 0).coerceAtLeast(0),
                    heartRateAt = o.optLong("heartRateAt", 0L).coerceAtLeast(0L),
                ),
            )
        }
        return out
    }

    /** One object holding everything, used by the backup file. */
    fun toJson(
        workouts: List<WorkoutSession>,
        meditations: List<MeditationSession>,
        days: List<DayLog>,
    ): JSONObject = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("workouts", workoutsToJson(workouts))
        .put("meditations", meditationsToJson(meditations))
        .put("days", daysToJson(days))
}

package com.osintgram.chronos.wellness

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON the wellness store writes and reads back.
 *
 * A fully populated record must come back identical, and a record damaged by a
 * half-written file, a sensor bug or an older version must be skipped (or
 * clamped) rather than throwing, because this runs on launch.
 */
class WellnessCodecTest {

    @Test
    fun `a fully populated record survives the round trip`() {
        val workout = WorkoutSession(
            id = 42,
            planId = "full_body_basics",
            title = "Full Body Basics",
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_001_800_000L,
            entries = listOf(
                WorkoutEntry(name = "Squat", sets = 3, reps = 12, weightKg = 25.5),
                WorkoutEntry(name = "Push-up", sets = 3, reps = 15, weightKg = 0.0),
            ),
            note = "felt good",
        )
        val meditation = MeditationSession(id = 43, minutes = 10, patternId = "box", completedAt = 1_700_002_000_000L)
        val day = DayLog(
            date = "2026-11-09",
            steps = 5100,
            waterMl = 1750,
            sleepMinutes = 450,
            habits = setOf("water", "walk"),
            heartRateBpm = 68,
            heartRateAt = 1_700_003_000_000L,
        )

        // Read back through the text form, so the in-memory objects cannot leak through.
        val reparsed = JSONObject(WellnessCodec.toJson(listOf(workout), listOf(meditation), listOf(day)).toString())

        val workouts = WellnessCodec.workoutsFromJson(reparsed.optJSONArray("workouts"))
        assertEquals(listOf(workout), workouts)
        assertEquals(listOf(meditation), WellnessCodec.meditationsFromJson(reparsed.optJSONArray("meditations")))
        assertEquals(listOf(day), WellnessCodec.daysFromJson(reparsed.optJSONArray("days")))

        assertEquals(30, workouts.first().minutes) // 1,800,000 ms
        assertEquals(918.0, workouts.first().volumeKg, 0.001) // 3×12×25.5 + 3×15×0
    }

    @Test
    fun `a workout with no keys at all is skipped`() {
        val array = JSONArray()
            .put(JSONObject())
            .put(JSONObject().put("id", 0).put("title", "No id"))
            .put(JSONObject().put("id", -2).put("title", "Negative id"))
            .put(JSONObject().put("id", 4).put("title", "   "))
            .put(JSONObject().put("id", 5).put("title", "Kept"))

        assertEquals(listOf(5L), WellnessCodec.workoutsFromJson(array).map { it.id })
    }

    @Test
    fun `nulls and non-objects inside the array are skipped`() {
        val array = JSONArray()
            .put(JSONObject.NULL)
            .put(JSONObject().put("id", 1).put("title", "First"))
            .put("junk")
            .put(JSONArray().put("nested"))
            .put(JSONObject().put("id", 2).put("title", "Second"))

        assertEquals(listOf(1L, 2L), WellnessCodec.workoutsFromJson(array).map { it.id })
    }

    @Test
    fun `negative numbers in a workout entry are clamped to zero`() {
        val array = JSONArray().put(
            JSONObject().put("id", 1).put("title", "Odd data").put(
                "entries",
                JSONArray()
                    .put(JSONObject().put("name", "Squat").put("sets", -3).put("reps", -12).put("weightKg", -20.5))
                    .put(JSONObject().put("name", "Plank")), // every number missing
            ),
        )

        val session = WellnessCodec.workoutsFromJson(array).single()
        assertEquals(2, session.entries.size)
        assertEquals(WorkoutEntry(name = "Squat", sets = 0, reps = 0, weightKg = 0.0), session.entries[0])
        assertEquals(WorkoutEntry(name = "Plank", sets = 0, reps = 0, weightKg = 0.0), session.entries[1])
        assertEquals(0.0, session.volumeKg, 0.0001)
    }

    @Test
    fun `a damaged entry is skipped and the session is kept`() {
        val array = JSONArray().put(
            JSONObject().put("id", 9).put("title", "Mixed").put(
                "entries",
                JSONArray()
                    .put(JSONObject.NULL)
                    .put("junk")
                    .put(JSONObject().put("sets", 3)) // no name
                    .put(JSONObject().put("name", "Row").put("sets", 3).put("reps", 10).put("weightKg", 30.0)),
            ),
        )

        val session = WellnessCodec.workoutsFromJson(array).single()
        assertEquals(listOf(WorkoutEntry(name = "Row", sets = 3, reps = 10, weightKg = 30.0)), session.entries)
        assertEquals("custom", session.planId) // planId falls back when the key is absent
    }

    @Test
    fun `a meditation with a zero or negative length is skipped`() {
        val array = JSONArray()
            .put(JSONObject().put("id", 1).put("minutes", 0))
            .put(JSONObject().put("id", 2).put("minutes", -5))
            .put(JSONObject().put("id", 3).put("minutes", 10).put("patternId", "calm"))
            .put(JSONObject().put("id", 4).put("minutes", 5)) // no patternId

        val sessions = WellnessCodec.meditationsFromJson(array)
        assertEquals(listOf(3L, 4L), sessions.map { it.id })
        assertEquals("calm", sessions[0].patternId)
        assertEquals("calm", sessions[1].patternId) // default pattern
    }

    @Test
    fun `an unknown pattern id is kept as it is`() {
        // patternId and planId are free-form strings, not an enum: a record written
        // by a newer build keeps its value instead of being rewritten to the default.
        val array = JSONArray()
            .put(JSONObject().put("id", 1).put("minutes", 5).put("patternId", "box_4_4_4_4"))
            .put(JSONObject().put("id", 2).put("title", "Weird plan").put("planId", "future_plan"))

        assertEquals("box_4_4_4_4", WellnessCodec.meditationsFromJson(array).single().patternId)
        assertEquals("future_plan", WellnessCodec.workoutsFromJson(array).single().planId)
    }

    @Test
    fun `a day with an unreadable date is skipped`() {
        val array = JSONArray()
            .put(JSONObject().put("date", "not-a-date").put("steps", 10))
            .put(JSONObject().put("date", "2026-13-40").put("steps", 10))
            .put(JSONObject().put("steps", 10)) // no date at all
            .put(JSONObject().put("date", "2026-11-09").put("steps", 10))

        val days = WellnessCodec.daysFromJson(array)
        assertEquals(listOf("2026-11-09"), days.map { it.date })
        assertEquals(10, days.single().steps)
    }

    @Test
    fun `negative day numbers are clamped and the day is kept`() {
        val array = JSONArray().put(
            JSONObject().put("date", "2026-11-09")
                .put("steps", -500)
                .put("waterMl", -1)
                .put("sleepMinutes", -30)
                .put("heartRateBpm", -10)
                .put("heartRateAt", -1),
        )

        val day = WellnessCodec.daysFromJson(array).single()
        assertEquals(0, day.steps)
        assertEquals(0, day.waterMl)
        assertEquals(0, day.sleepMinutes)
        assertEquals(0, day.heartRateBpm)
        assertEquals(0L, day.heartRateAt)
        assertTrue(day.isEmpty)
    }

    @Test
    fun `damaged habit ids are dropped and duplicates collapse`() {
        val array = JSONArray().put(
            JSONObject().put("date", "2026-11-09").put(
                "habits",
                JSONArray().put(JSONObject.NULL).put("").put("walk").put("walk").put("water"),
            ),
        )

        val day = WellnessCodec.daysFromJson(array).single()
        assertEquals(setOf("walk", "water"), day.habits)
    }

    @Test
    fun `a missing array reads as no records`() {
        assertTrue(WellnessCodec.workoutsFromJson(null).isEmpty())
        assertTrue(WellnessCodec.meditationsFromJson(null).isEmpty())
        assertTrue(WellnessCodec.daysFromJson(null).isEmpty())
    }
}

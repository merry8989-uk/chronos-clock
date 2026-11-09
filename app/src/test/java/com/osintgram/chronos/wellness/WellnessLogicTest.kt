package com.osintgram.chronos.wellness

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the wellness feature: the step-counter baseline, week totals,
 * the streak rule and the JSON round-trip that keeps records across launches.
 * These run on the JVM with no device.
 */
class WellnessLogicTest {

    private val today = LocalDate.of(2026, 11, 9)

    @Test
    fun `step delta is zero before the first reading of the day`() {
        assertEquals(0, WellnessLogic.stepDelta(cumulative = 1200, baseline = null))
        assertTrue(WellnessLogic.needsRebaseline(cumulative = 1200, baseline = null))
    }

    @Test
    fun `step delta counts from the day baseline`() {
        assertEquals(340, WellnessLogic.stepDelta(cumulative = 5340, baseline = 5000))
        assertFalse(WellnessLogic.needsRebaseline(cumulative = 5340, baseline = 5000))
    }

    @Test
    fun `a reboot restarts the counter and moves the baseline`() {
        // The phone rebooted: the sensor is back near zero while the baseline is high.
        assertEquals(0, WellnessLogic.stepDelta(cumulative = 120, baseline = 5000))
        assertTrue(WellnessLogic.needsRebaseline(cumulative = 120, baseline = 5000))
    }

    @Test
    fun `the day baseline uses the stored value, the reboot, or now`() {
        // A stored baseline is the answer.
        assertEquals(5000L, WellnessLogic.resolveStepBaseline(5400, 5000, bootedSinceMidnight = false))
        // No baseline and the phone rebooted after midnight: everything since boot is today.
        assertEquals(0L, WellnessLogic.resolveStepBaseline(3200, null, bootedSinceMidnight = true))
        // No baseline and the counter has been running since before midnight: start at now.
        assertEquals(4200L, WellnessLogic.resolveStepBaseline(4200, null, bootedSinceMidnight = false))
        // A reading below the stored baseline means the counter restarted.
        assertEquals(0L, WellnessLogic.resolveStepBaseline(120, 9000, bootedSinceMidnight = true))
    }

    @Test
    fun `steps today count from the resolved baseline`() {
        val baseline = WellnessLogic.resolveStepBaseline(3200, null, bootedSinceMidnight = true)
        assertEquals(3200, WellnessLogic.stepDelta(3200, baseline))
        val stored = WellnessLogic.resolveStepBaseline(5400, 5000, bootedSinceMidnight = false)
        assertEquals(400, WellnessLogic.stepDelta(5400, stored))
        assertTrue(WellnessLogic.countedFromNow(null, bootedSinceMidnight = false))
        assertFalse(WellnessLogic.countedFromNow(5000, bootedSinceMidnight = false))
    }

    @Test
    fun `minutes between instants floor and never go negative`() {
        val start = 1_000_000L
        assertEquals(5, WellnessLogic.minutesBetween(start, start + 5 * 60_000L + 59_000L))
        assertEquals(0, WellnessLogic.minutesBetween(start, start - 60_000L))
    }

    @Test
    fun `recent days are oldest first and end today`() {
        val days = WellnessLogic.recentDays(today, 7)
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first())
        assertEquals(today, days.last())
    }

    @Test
    fun `week totals sum only finished workouts inside the window`() {
        val start = today.minusDays(6).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val inside = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val outside = today.minusDays(20).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val workouts = listOf(
            WorkoutSession(
                id = 1,
                planId = "full_body_basics",
                title = "Full Body Basics",
                startedAt = inside,
                endedAt = inside + 30 * 60_000L,
                entries = listOf(WorkoutEntry("Squat", 3, 10, 20.0)),
            ),
            WorkoutSession(
                id = 2,
                planId = "upper_body",
                title = "Upper Body",
                startedAt = outside,
                endedAt = outside + 20 * 60_000L,
            ),
            // Still running: must not be counted.
            WorkoutSession(
                id = 3,
                planId = "mobility_stretch",
                title = "Mobility",
                startedAt = inside,
                endedAt = null,
            ),
        )
        val meditations = listOf(
            MeditationSession(10, 10, "calm", inside),
            MeditationSession(11, 5, "box", outside),
        )
        val days = listOf(
            DayLog(WellnessLogic.todayKey(today), steps = 4000, waterMl = 1500, sleepMinutes = 420),
            DayLog(WellnessLogic.todayKey(today.minusDays(1)), steps = 2000, sleepMinutes = 480),
        )

        val totals = WellnessLogic.weekTotals(days, workouts, meditations, start, end)

        assertEquals(6000, totals.steps)
        assertEquals(1500, totals.waterMl)
        assertEquals(450, totals.sleepMinutesAverage) // (420 + 480) / 2
        assertEquals(1, totals.workoutCount)
        assertEquals(30, totals.workoutMinutes)
        assertEquals(10, totals.meditationMinutes)
        assertEquals(600.0, totals.volumeKg, 0.001) // 3 × 10 × 20 kg
        assertEquals(2, totals.daysWithSteps)
    }

    @Test
    fun `streak counts back from today and allows today to be empty`() {
        val active = setOf(
            WellnessLogic.todayKey(today.minusDays(1)),
            WellnessLogic.todayKey(today.minusDays(2)),
            WellnessLogic.todayKey(today.minusDays(3)),
        )
        assertEquals(3, WellnessLogic.streak(active, today))

        val withToday = active + WellnessLogic.todayKey(today)
        assertEquals(4, WellnessLogic.streak(withToday, today))
        assertEquals(0, WellnessLogic.streak(emptySet(), today))
    }

    @Test
    fun `a gap breaks the streak`() {
        val active = setOf(
            WellnessLogic.todayKey(today),
            WellnessLogic.todayKey(today.minusDays(1)),
            // day 2 missing
            WellnessLogic.todayKey(today.minusDays(3)),
        )
        assertEquals(2, WellnessLogic.streak(active, today))
    }

    @Test
    fun `a health snapshot only ever raises a day and never wipes a manual entry`() {
        val typed = DayLog("2026-11-09", steps = 5000, waterMl = 2000, sleepMinutes = 420)
        val merged = WellnessLogic.mergeHealthSnapshot(typed, steps = 3000, waterMl = 2500, sleepMinutes = null)
        assertEquals(5000, merged.steps)      // lower sensor value ignored
        assertEquals(2500, merged.waterMl)    // higher value taken
        assertEquals(420, merged.sleepMinutes) // missing value keeps what was typed

        val empty = DayLog("2026-11-09")
        val fromSensor = WellnessLogic.mergeHealthSnapshot(empty, steps = 1200, waterMl = null, sleepMinutes = null)
        assertEquals(1200, fromSensor.steps)
        assertEquals(0, fromSensor.waterMl)
    }

    @Test
    fun `heart rate bands split at the usual thresholds`() {
        assertEquals("none", WellnessLogic.heartRateBand(0))
        assertEquals("low", WellnessLogic.heartRateBand(45))
        assertEquals("normal", WellnessLogic.heartRateBand(72))
        assertEquals("elevated", WellnessLogic.heartRateBand(120))
        assertEquals("high", WellnessLogic.heartRateBand(160))
    }

    @Test
    fun `heart rate survives the json round trip`() {
        val days = listOf(
            DayLog("2026-11-09", steps = 4200, heartRateBpm = 68, heartRateAt = 1_700_003_000_000L),
            DayLog("2026-11-08", steps = 100), // older record without a reading
        )
        val json = WellnessCodec.toJson(emptyList(), emptyList(), days)
        val read = WellnessCodec.daysFromJson(JSONObject(json.toString()).optJSONArray("days"))
        assertEquals(days, read)
        assertEquals(68, read.first().heartRateBpm)
        assertEquals(0, read[1].heartRateBpm)
        assertEquals(0L, read[1].heartRateAt)
    }

    @Test
    fun `progress is clamped and safe with a zero goal`() {
        assertEquals(0f, WellnessLogic.progress(500, 0), 0.0001f)
        assertEquals(0.5f, WellnessLogic.progress(4000, 8000), 0.0001f)
        assertEquals(1f, WellnessLogic.progress(20_000, 8000), 0.0001f)
    }

    @Test
    fun `formatters read the way the cards show them`() {
        assertEquals("8,240", WellnessLogic.formatSteps(8240))
        assertEquals("0", WellnessLogic.formatSteps(-5))
        assertEquals("1 h 20 m", WellnessLogic.formatMinutes(80))
        assertEquals("45 m", WellnessLogic.formatMinutes(45))
        assertEquals("2 h", WellnessLogic.formatMinutes(120))
        assertEquals("1.8 L", WellnessLogic.formatLitres(1750))
    }

    @Test
    fun `records survive a json round trip`() {
        val workouts = listOf(
            WorkoutSession(
                id = 42,
                planId = "full_body_basics",
                title = "Full Body Basics",
                startedAt = 1_700_000_000_000L,
                endedAt = 1_700_001_800_000L,
                entries = listOf(WorkoutEntry("Squat", 3, 12, 25.5)),
                note = "felt good",
            ),
        )
        val meditations = listOf(MeditationSession(43, 10, "box", 1_700_002_000_000L))
        val days = listOf(DayLog("2026-11-09", 5100, 1750, 450, setOf("water", "walk")))

        val json = WellnessCodec.toJson(workouts, meditations, days)
        val reparsed = JSONObject(json.toString())

        val readWorkouts = WellnessCodec.workoutsFromJson(reparsed.optJSONArray("workouts"))
        val readMeditations = WellnessCodec.meditationsFromJson(reparsed.optJSONArray("meditations"))
        val readDays = WellnessCodec.daysFromJson(reparsed.optJSONArray("days"))

        assertEquals(workouts, readWorkouts)
        assertEquals(meditations, readMeditations)
        assertEquals(days, readDays)
        assertEquals(30, readWorkouts.first().minutes) // 1,800,000 ms
        assertEquals(918.0, readWorkouts.first().volumeKg, 0.001)
    }

    @Test
    fun `damaged records are skipped instead of crashing the launch`() {
        val array = org.json.JSONArray()
            .put(JSONObject().put("id", 0).put("title", ""))          // no id, no title
            .put(JSONObject().put("id", 7).put("title", "Walk"))      // valid, no entries
            .put("not an object")

        val parsed = WellnessCodec.workoutsFromJson(array)
        assertEquals(1, parsed.size)
        assertEquals(7L, parsed.first().id)
        assertTrue(parsed.first().entries.isEmpty())

        val days = WellnessCodec.daysFromJson(
            org.json.JSONArray()
                .put(JSONObject().put("date", "not-a-date").put("steps", 10))
                .put(JSONObject().put("date", "2026-11-09").put("steps", 10)),
        )
        assertEquals(1, days.size)
        assertEquals("2026-11-09", days.first().date)
        assertNotNull(WellnessLogic.parseDate(days.first().date))
    }
}

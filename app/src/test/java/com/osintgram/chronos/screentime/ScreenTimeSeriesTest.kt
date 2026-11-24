package com.osintgram.chronos.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The series maths behind the hourly chart, the seven-day row and the session
 * tiles: buckets, clipping, averages and the longest stretch.
 */
class ScreenTimeSeriesTest {

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        /** An arbitrary but fixed day start, so the tests never depend on "now". */
        const val DAY_START = 1_700_000_000_000L

        fun day(offset: Int, millis: Long): DayTotal = DayTotal(
            dayStartMillis = DAY_START + offset * DAY,
            letter = "M",
            name = "Mon",
            foregroundMillis = millis,
            isToday = offset == 6,
        )
    }

    @Test
    fun `a day of segments fills 24 buckets and splits at the hour`() {
        val buckets = ScreenTimeLogic.hourlyBuckets(
            listOf(
                // 09:00 to 09:20, then 09:45 to 10:15 — one stretch inside the hour,
                // one that crosses it.
                UsageSegment(DAY_START + 9 * HOUR, DAY_START + 9 * HOUR + 20 * MINUTE, "a"),
                UsageSegment(DAY_START + 9 * HOUR + 45 * MINUTE, DAY_START + 10 * HOUR + 15 * MINUTE, "b"),
            ),
            DAY_START,
        )
        assertEquals(24, buckets.size)
        assertEquals(35 * MINUTE, buckets[9])
        assertEquals(15 * MINUTE, buckets[10])
        assertEquals(0L, buckets[8])
        assertEquals(0L, buckets[11])
        assertEquals(50 * MINUTE, buckets.sum())
    }

    @Test
    fun `a quiet day is 24 empty buckets, not an empty list`() {
        val buckets = ScreenTimeLogic.hourlyBuckets(emptyList(), DAY_START)
        assertEquals(24, buckets.size)
        assertEquals(0L, buckets.sum())
    }

    @Test
    fun `a segment that crosses midnight is clipped to the day`() {
        // 23:40 the night before to 00:25 today: only the 25 minutes after
        // midnight belong to today, and they belong to the first hour.
        val buckets = ScreenTimeLogic.hourlyBuckets(
            listOf(UsageSegment(DAY_START - 20 * MINUTE, DAY_START + 25 * MINUTE, "a")),
            DAY_START,
        )
        assertEquals(25 * MINUTE, buckets[0])
        assertEquals(0L, buckets[23])
        assertEquals(25 * MINUTE, buckets.sum())
    }

    @Test
    fun `a segment that runs past midnight keeps only today's part`() {
        // 23:00 today to 01:00 tomorrow.
        val buckets = ScreenTimeLogic.hourlyBuckets(
            listOf(UsageSegment(DAY_START + 23 * HOUR, DAY_START + 25 * HOUR, "a")),
            DAY_START,
        )
        assertEquals(HOUR, buckets[23])
        assertEquals(0L, buckets[0])
        assertEquals(HOUR, buckets.sum())
    }

    @Test
    fun `a segment ending exactly at the day boundary stays in the last hour`() {
        val buckets = ScreenTimeLogic.hourlyBuckets(
            listOf(UsageSegment(DAY_START + 23 * HOUR, DAY_START + DAY, "a")),
            DAY_START,
        )
        assertEquals(HOUR, buckets[23])
        assertEquals(0L, buckets[0])
        assertEquals(HOUR, buckets.sum())
    }

    @Test
    fun `a segment ending exactly on the hour does not spill into the next one`() {
        val buckets = ScreenTimeLogic.hourlyBuckets(
            listOf(UsageSegment(DAY_START + 9 * HOUR, DAY_START + 10 * HOUR, "a")),
            DAY_START,
        )
        assertEquals(HOUR, buckets[9])
        assertEquals(0L, buckets[10])
    }

    @Test
    fun `bars scale to the tallest bar and a flat day stays flat`() {
        val fractions = ScreenTimeLogic.barFractions(listOf(0L, 30 * MINUTE, HOUR, 15 * MINUTE))
        assertEquals(0f, fractions[0], 0.001f)
        assertEquals(0.5f, fractions[1], 0.001f)
        assertEquals(1f, fractions[2], 0.001f)
        assertEquals(0.25f, fractions[3], 0.001f)
        assertEquals(listOf(0f, 0f, 0f), ScreenTimeLogic.barFractions(listOf(0L, 0L, 0L)))
    }

    @Test
    fun `the seven-day series keeps an empty day and leaves it out of the average`() {
        val days = listOf(
            day(0, 2 * HOUR),
            day(1, 0L),
            day(2, 4 * HOUR),
            day(3, 0L),
            day(4, 1 * HOUR),
            day(5, 3 * HOUR),
            day(6, 5 * HOUR),
        )
        val week = ScreenTimeLogic.week(days)
        assertEquals(7, week.days.size)
        assertEquals(0L, week.days[1].foregroundMillis)
        // 15 hours of use over the five days that had any: 3 hours a day.
        assertEquals(3 * HOUR, week.averageActiveMillis)
        assertEquals(5 * HOUR, week.busiest?.foregroundMillis)
        assertTrue(week.days[6].isToday)
    }

    @Test
    fun `the average ignores days with no use`() {
        assertEquals(0L, ScreenTimeLogic.averageActiveDay(listOf(0L, 0L)))
        assertEquals(3 * HOUR, ScreenTimeLogic.averageActiveDay(listOf(2 * HOUR, 0L, 4 * HOUR, 0L)))
        assertEquals(HOUR, ScreenTimeLogic.averageActiveDay(listOf(HOUR, HOUR, HOUR)))
        assertEquals(0L, ScreenTimeLogic.averageActiveDay(emptyList()))
    }

    @Test
    fun `a week with no use has no busiest day`() {
        val week = ScreenTimeLogic.week(listOf(day(0, 0L), day(1, 0L)))
        assertNull(week.busiest)
        assertEquals(0L, week.averageActiveMillis)
    }

    @Test
    fun `the longest session wins and ties go to the earlier one`() {
        val first = UsageSegment(DAY_START, DAY_START + 10 * MINUTE, "a")
        val tie = UsageSegment(DAY_START + HOUR, DAY_START + HOUR + 10 * MINUTE, "b")
        val longer = UsageSegment(DAY_START + 2 * HOUR, DAY_START + 2 * HOUR + 40 * MINUTE, "c")

        assertEquals("a", ScreenTimeLogic.longestSession(listOf(first, tie))?.packageName)
        assertEquals("c", ScreenTimeLogic.longestSession(listOf(first, tie, longer))?.packageName)
        assertEquals(10 * MINUTE, ScreenTimeLogic.longestSession(listOf(first, tie))?.durationMillis)
        assertNull(ScreenTimeLogic.longestSession(emptyList()))
        assertNull(ScreenTimeLogic.longestSession(listOf(UsageSegment(DAY_START, DAY_START, "a"))))
    }

    @Test
    fun `sessions over five minutes are counted strictly`() {
        val segments = listOf(
            UsageSegment(DAY_START, DAY_START + 5 * MINUTE, "a"),
            UsageSegment(DAY_START + HOUR, DAY_START + HOUR + 5 * MINUTE + 1, "b"),
            UsageSegment(DAY_START + 2 * HOUR, DAY_START + 2 * HOUR + 30 * MINUTE, "c"),
        )
        assertEquals(2, ScreenTimeLogic.sessionsLongerThan(segments))
        assertEquals(0, ScreenTimeLogic.sessionsLongerThan(emptyList()))
    }
}

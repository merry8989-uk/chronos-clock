package com.osintgram.chronos.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screen-time maths: durations, limits and ordering. */
class ScreenTimeLogicTest {

    @Test
    fun `durations read the way the cards show them`() {
        assertEquals("0 m", ScreenTimeLogic.formatDuration(0))
        assertEquals("0 m", ScreenTimeLogic.formatDuration(59_000))
        assertEquals("1 m", ScreenTimeLogic.formatDuration(60_000))
        assertEquals("1 h", ScreenTimeLogic.formatDuration(60 * 60_000L))
        assertEquals("1 h 24 m", ScreenTimeLogic.formatDuration(84 * 60_000L))
        assertEquals("2 h 5 m", ScreenTimeLogic.formatDuration(125 * 60_000L))
    }

    @Test
    fun `a limit is only a limit when it is set`() {
        assertFalse(ScreenTimeLogic.limitReached(10 * 60_000L, limitMinutes = 0))
        assertFalse(ScreenTimeLogic.limitReached(59 * 60_000L, limitMinutes = 60))
        assertTrue(ScreenTimeLogic.limitReached(60 * 60_000L, limitMinutes = 60))
        assertTrue(ScreenTimeLogic.limitReached(61 * 60_000L, limitMinutes = 60))
    }

    @Test
    fun `limit progress is clamped`() {
        assertEquals(0f, ScreenTimeLogic.limitProgress(0, 0), 0.001f)
        assertEquals(0.5f, ScreenTimeLogic.limitProgress(30 * 60_000L, 60), 0.001f)
        assertEquals(1f, ScreenTimeLogic.limitProgress(120 * 60_000L, 60), 0.001f)
    }

    @Test
    fun `apps are ordered by time and empty ones dropped`() {
        val apps = listOf(
            AppUsage("a", "A", 5 * 60_000L, 2),
            AppUsage("b", "B", 0L, 0),
            AppUsage("c", "C", 30 * 60_000L, 5),
            AppUsage("d", "D", 0L, 0, notifications = 3),
        )
        val sorted = ScreenTimeLogic.sortByUsage(apps)
        assertEquals(listOf("c", "a", "d"), sorted.map { it.packageName })
    }

    @Test
    fun `share of the day is a fraction and never above one`() {
        val app = AppUsage("a", "A", 30 * 60_000L, 1)
        assertEquals(0f, ScreenTimeLogic.share(app, 0), 0.001f)
        assertEquals(0.5f, ScreenTimeLogic.share(app, 60 * 60_000L), 0.001f)
        assertEquals(1f, ScreenTimeLogic.share(app, 10 * 60_000L), 0.001f)
    }

    @Test
    fun `the most used app is the one with the most time`() {
        val day = DayScreenTime(
            totalForegroundMillis = 60 * 60_000L,
            apps = listOf(
                AppUsage("a", "A", 10 * 60_000L, 1),
                AppUsage("b", "B", 40 * 60_000L, 9),
            ),
        )
        assertEquals("b", day.mostUsed?.packageName)
        assertEquals(listOf("b", "a"), day.topApps.map { it.packageName })
    }
}

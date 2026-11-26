package com.osintgram.chronos.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The day a limit belongs to. A midnight reset gives a fresh allowance to
 * someone still awake at 1 am, so the boundary is configurable — and these are
 * the cases that decide whether a limit is enforced on the right day.
 */
class DayBoundaryTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun millis(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(kolkata).toInstant().toEpochMilli()

    @Test
    fun `before the start hour the day is still yesterday`() {
        val at1am = millis(LocalDate.of(2026, 11, 27), 1)
        assertEquals(
            millis(LocalDate.of(2026, 11, 26), 7),
            DayBoundary.startMillis(at1am, startHour = 7, zone = kolkata),
        )
        assertEquals("2026-11-26", DayBoundary.key(at1am, startHour = 7, zone = kolkata))
    }

    @Test
    fun `at and after the start hour the day is today`() {
        val at7 = millis(LocalDate.of(2026, 11, 27), 7)
        val at11pm = millis(LocalDate.of(2026, 11, 27), 23, 59)
        assertEquals(at7, DayBoundary.startMillis(at7, startHour = 7, zone = kolkata))
        assertEquals("2026-11-27", DayBoundary.key(at11pm, startHour = 7, zone = kolkata))
    }

    @Test
    fun `a midnight start behaves like the old calendar day`() {
        val at1am = millis(LocalDate.of(2026, 11, 27), 1)
        assertEquals("2026-11-27", DayBoundary.key(at1am, startHour = 0, zone = kolkata))
    }

    @Test
    fun `the day ends exactly twenty four hours after it starts`() {
        val at1am = millis(LocalDate.of(2026, 11, 27), 1)
        val start = DayBoundary.startMillis(at1am, startHour = 7, zone = kolkata)
        val next = millis(LocalDate.of(2026, 11, 27), 7)
        assertEquals(next - start, 24L * 60 * 60 * 1000)
    }

    @Test
    fun `a moment belongs to the running day or it does not`() {
        val at1am = millis(LocalDate.of(2026, 11, 27), 1)
        assertTrue(DayBoundary.isToday(millis(LocalDate.of(2026, 11, 26), 20), now = at1am))
        assertFalse(DayBoundary.isToday(millis(LocalDate.of(2026, 11, 27), 9), now = at1am))
    }
}

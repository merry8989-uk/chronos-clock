package com.osintgram.chronos.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** One shared helper now backs wellness, tasks and screen time. */
class DatesTest {

    @Test
    fun `a day becomes an ISO key and comes back`() {
        val date = LocalDate.of(2026, 11, 12)
        assertEquals("2026-11-12", Dates.key(date))
        assertEquals(date, Dates.parse("2026-11-12"))
        assertNull(Dates.parse("not-a-date"))
        assertNull(Dates.parse("2026-13-40"))
    }

    @Test
    fun `an instant is keyed in the device zone`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        // 2026-11-12 00:30 IST is still 2026-11-11 in UTC.
        val millis = LocalDate.of(2026, 11, 12).atTime(0, 30).atZone(kolkata).toInstant().toEpochMilli()
        assertEquals("2026-11-12", Dates.keyOf(millis, kolkata))
        assertEquals("2026-11-11", Dates.keyOf(millis, ZoneId.of("UTC")))
    }

    @Test
    fun `durations read the way the cards show them`() {
        assertEquals("0 m", Dates.hoursMinutes(0))
        assertEquals("45 m", Dates.hoursMinutes(45))
        assertEquals("1 h", Dates.hoursMinutes(60))
        assertEquals("1 h 20 m", Dates.hoursMinutes(80))
        assertEquals("2 h 5 m", Dates.hoursMinutes(125))
    }
}

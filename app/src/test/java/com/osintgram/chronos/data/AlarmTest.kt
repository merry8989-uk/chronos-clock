package com.osintgram.chronos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The alarm scheduling maths — the one part of the app that decides whether
 * somebody wakes up — plus the strings the alarm list shows.
 *
 * Every instant is built from a fixed Asia/Kolkata [ZonedDateTime], so nothing
 * here depends on the machine's default timezone or on the day the tests run.
 * 2026-11-12 is a Thursday, 2026-11-13 a Friday, 2026-11-16 the Monday after.
 */
class AlarmTest {

    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")

    private val thursday: LocalDate = LocalDate.of(2026, 11, 12)
    private val friday: LocalDate = LocalDate.of(2026, 11, 13)
    private val monday: LocalDate = LocalDate.of(2026, 11, 16)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime =
        date.atTime(hour, minute).atZone(kolkata)

    private fun millisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        at(date, hour, minute).toInstant().toEpochMilli()

    private fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(kolkata).toLocalDate()

    /** Fails loudly rather than with a bare null comparison when no trigger is found. */
    private fun trigger(
        alarm: Alarm,
        now: ZonedDateTime,
        holidays: Set<LocalDate> = emptySet(),
    ): Long = Alarm.nextTriggerMillis(alarm, now, holidays)
        ?: throw AssertionError("expected a next trigger for $alarm from $now, got null")

    // ---- nextTriggerMillis -------------------------------------------------

    @Test
    fun `a one-shot alarm later today rings today`() {
        val alarm = Alarm(hour = 7, minute = 30) // no repeat days: fires once
        assertEquals(millisAt(thursday, 7, 30), trigger(alarm, now = at(thursday, 6, 0)))
    }

    @Test
    fun `a one-shot alarm whose time has passed today rings tomorrow`() {
        val alarm = Alarm(hour = 7, minute = 30)
        assertEquals(millisAt(friday, 7, 30), trigger(alarm, now = at(thursday, 9, 0)))
    }

    @Test
    fun `an alarm set for exactly now waits for the next day`() {
        // The candidate has to be strictly after now, so a ring time that has
        // arrived this very minute belongs to tomorrow.
        val alarm = Alarm(hour = 7, minute = 30)
        assertEquals(millisAt(friday, 7, 30), trigger(alarm, now = at(thursday, 7, 30)))
    }

    @Test
    fun `a repeating alarm only fires on its own weekdays`() {
        val alarm = Alarm(hour = 7, minute = 30, repeatDays = setOf(1, 3, 5)) // Mon, Wed, Fri

        // Thursday morning: the next selected weekday is Friday.
        val first = trigger(alarm, now = at(thursday, 9, 0))
        assertEquals(millisAt(friday, 7, 30), first)
        assertEquals(DayOfWeek.FRIDAY, Instant.ofEpochMilli(first).atZone(kolkata).dayOfWeek)

        // From that ring the weekend is skipped and the alarm lands on Monday.
        val second = trigger(alarm, now = Instant.ofEpochMilli(first).atZone(kolkata))
        assertEquals(millisAt(monday, 7, 30), second)
        assertEquals(DayOfWeek.MONDAY, Instant.ofEpochMilli(second).atZone(kolkata).dayOfWeek)
    }

    @Test
    fun `a holiday is skipped only when the alarm asks for it`() {
        val holidays = setOf(thursday)
        val skipping = Alarm(hour = 7, minute = 30, skipHolidays = true)
        assertEquals(millisAt(friday, 7, 30), trigger(skipping, at(thursday, 6, 0), holidays))

        // Same calendar, same hour: an alarm that does not skip holidays still rings today.
        val plain = Alarm(hour = 7, minute = 30, skipHolidays = false)
        assertEquals(millisAt(thursday, 7, 30), trigger(plain, at(thursday, 6, 0), holidays))
    }

    @Test
    fun `a repeating alarm whose weekday is a holiday waits a whole week`() {
        val alarm = Alarm(hour = 7, minute = 30, repeatDays = setOf(4), skipHolidays = true) // Thursdays
        val next = trigger(alarm, now = at(thursday, 6, 0), holidays = setOf(thursday))
        assertEquals(millisAt(thursday.plusDays(7), 7, 30), next) // 2026-11-19, the next Thursday
        assertEquals(DayOfWeek.THURSDAY, dateOf(next).dayOfWeek)
    }

    @Test
    fun `there is no next trigger when every day in the search window is a holiday`() {
        // The search looks at today plus the next 400 days. A one-shot alarm that
        // skips holidays has nowhere left to land, so the answer is null — the
        // only way this function returns null for a well formed alarm.
        val alarm = Alarm(hour = 7, minute = 30, skipHolidays = true)
        val everyDayInWindow = (0L..400L).map { thursday.plusDays(it) }.toSet()
        assertNull(Alarm.nextTriggerMillis(alarm, at(thursday, 6, 0), everyDayInWindow))

        // The same holidays are irrelevant to an alarm that does not skip them.
        val plain = alarm.copy(skipHolidays = false)
        assertEquals(millisAt(thursday, 7, 30), trigger(plain, at(thursday, 6, 0), everyDayInWindow))
    }

    // ---- the time text the list shows --------------------------------------

    @Test
    fun `the time text pads the hour and minute in both clock styles`() {
        assertEquals("07:05", Alarm(hour = 7, minute = 5).timeText(use24Hour = true))
        assertEquals("07:05", Alarm(hour = 7, minute = 5).timeText(use24Hour = false))
        assertEquals("00:00", Alarm(hour = 0, minute = 0).timeText(use24Hour = true))
        assertEquals("12:00", Alarm(hour = 0, minute = 0).timeText(use24Hour = false)) // midnight is 12 am
        assertEquals("13:00", Alarm(hour = 13, minute = 0).timeText(use24Hour = true))
        assertEquals("01:00", Alarm(hour = 13, minute = 0).timeText(use24Hour = false))
        assertEquals("11:59", Alarm(hour = 23, minute = 59).timeText(use24Hour = false))
    }

    @Test
    fun `the meridiem is empty on the 24 hour clock`() {
        assertEquals("am", Alarm(hour = 0, minute = 0).meridiem(use24Hour = false))
        assertEquals("am", Alarm(hour = 11, minute = 59).meridiem(use24Hour = false))
        assertEquals("pm", Alarm(hour = 12, minute = 0).meridiem(use24Hour = false))
        assertEquals("pm", Alarm(hour = 23, minute = 59).meridiem(use24Hour = false))
        assertEquals("", Alarm(hour = 7, minute = 0).meridiem(use24Hour = true))
        assertEquals("", Alarm(hour = 19, minute = 0).meridiem(use24Hour = true))
    }

    @Test
    fun `an out of range hour or minute is clamped when the time is shown`() {
        // A stored hour of 25 must never make the formatter throw.
        assertEquals("23:59", Alarm(hour = 25, minute = 75).timeText(use24Hour = true))
        // meridiem() reads the raw hour, so 25 still reads as pm.
        assertEquals("pm", Alarm(hour = 25, minute = 75).meridiem(use24Hour = false))
    }

    @Test
    fun `the repeat summary names the common patterns and lists the rest`() {
        assertEquals("Once", Alarm(repeatDays = emptySet()).repeatSummary())
        assertEquals("Every day", Alarm(repeatDays = setOf(1, 2, 3, 4, 5, 6, 7)).repeatSummary())
        assertEquals("Mon to Fri", Alarm(repeatDays = setOf(1, 2, 3, 4, 5)).repeatSummary())
        assertEquals("Weekends", Alarm(repeatDays = setOf(6, 7)).repeatSummary())
        // Days are listed in ISO order, not insertion order.
        assertEquals("Mon, Wed, Fri", Alarm(repeatDays = setOf(5, 1, 3)).repeatSummary())
        assertEquals("Sun", Alarm(repeatDays = setOf(7)).repeatSummary())
    }

    @Test
    fun `the weekday abbreviations are English and never throw`() {
        assertEquals("Mon", Alarm.dayShort(1))
        assertEquals("Thu", Alarm.dayShort(4))
        assertEquals("Sun", Alarm.dayShort(7))
        // Out of range days are coerced into 1..7 rather than throwing.
        assertEquals("Mon", Alarm.dayShort(0))
        assertEquals("Sun", Alarm.dayShort(9))
    }
}

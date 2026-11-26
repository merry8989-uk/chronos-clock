package com.osintgram.chronos.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sadhana model: how a hold is written down, and what a practice means. */
class SadhanaTest {

    @Test
    fun `seconds read as minutes and seconds`() {
        assertEquals("0 s", SadhanaSession.formatSeconds(0))
        assertEquals("42 s", SadhanaSession.formatSeconds(42))
        assertEquals("1 m", SadhanaSession.formatSeconds(60))
        assertEquals("4 m 20 s", SadhanaSession.formatSeconds(260))
        assertEquals("0 s", SadhanaSession.formatSeconds(-5))
    }

    @Test
    fun `a gaze hold with no blink says unbroken`() {
        val session = SadhanaSession(id = 1, kind = "trataka", startedAt = 0, seconds = 95, blinks = 0)
        assertEquals("1 m 35 s unbroken", session.summary)
    }

    @Test
    fun `a gaze hold with blinks counts them`() {
        val one = SadhanaSession(id = 1, kind = "trataka", startedAt = 0, seconds = 30, blinks = 1)
        val many = SadhanaSession(id = 2, kind = "trataka", startedAt = 0, seconds = 30, blinks = 4)
        assertEquals("30 s, 1 blink", one.summary)
        assertEquals("30 s, 4 blinks", many.summary)
    }

    @Test
    fun `japa is measured in rounds`() {
        val one = SadhanaSession(id = 1, kind = "japa", startedAt = 0, seconds = 400, rounds = 1)
        val three = SadhanaSession(id = 2, kind = "japa", startedAt = 0, seconds = 900, rounds = 3)
        assertEquals("1 round", one.summary)
        assertEquals("3 rounds", three.summary)
    }

    @Test
    fun `a timed sit is measured in time`() {
        val session = SadhanaSession(id = 1, kind = "dhyana", startedAt = 0, seconds = 600)
        assertEquals("10 m", session.summary)
    }

    @Test
    fun `every kind has an id, a title and a measure`() {
        SadhanaKind.entries.forEach { kind ->
            assertTrue(kind.id.isNotBlank())
            assertTrue(kind.title.isNotBlank())
            assertTrue(kind.subtitle.isNotBlank())
        }
        assertEquals(SadhanaKind.Measure.Gaze, SadhanaKind.Trataka.measure)
        assertEquals(SadhanaKind.Measure.Rounds, SadhanaKind.Japa.measure)
        assertEquals(SadhanaKind.Measure.Time, SadhanaKind.Dhyana.measure)
    }

    @Test
    fun `an unknown kind has no kind and falls back to time`() {
        val session = SadhanaSession(id = 1, kind = "not_a_practice", startedAt = 0, seconds = 60)
        assertNull(session.kindOf)
        assertEquals("1 m", session.summary)
    }
}

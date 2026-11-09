package com.osintgram.chronos.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Masking is the one part of the logging system that must never be wrong: a
 * token that reaches the file has already leaked. These tests pin the shapes
 * that appear in real log lines.
 */
class LogMaskingTest {

    @Test
    fun `key value credentials are redacted and the key is kept`() {
        assertEquals("token=[redacted]", LogMasking.mask("token=abc123"))
        assertEquals("password: [redacted]", LogMasking.mask("password: hunter2"))
        assertEquals("api_key=[redacted]", LogMasking.mask("api_key=sk-live-9999"))
        assertEquals(
            "url=https://api.github.com token=[redacted]",
            LogMasking.mask("url=https://api.github.com token=ghp_secretvalue"),
        )
    }

    @Test
    fun `authorization headers lose the whole value`() {
        val masked = LogMasking.mask("Authorization: Bearer abcdef123456789xyz")
        assertTrue(masked.contains("[redacted]"))
        assertFalse(masked.contains("abcdef123456789xyz"))
    }

    @Test
    fun `known token shapes are redacted`() {
        assertFalse(LogMasking.mask("ghp_abcdefghijklmnopqrstuvwxyz0123").contains("ghp_"))
        assertFalse(
            LogMasking.mask("jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijkl")
                .contains("eyJhbGciOiJIUzI1NiJ9"),
        )
    }

    @Test
    fun `personal data is redacted`() {
        assertEquals("user [redacted] logged in", LogMasking.mask("user amit.kumar@example.com logged in"))
        assertFalse(LogMasking.mask("call 9876543210 now").contains("9876543210"))
        assertFalse(LogMasking.mask("card 4111111111111111").contains("4111111111111111"))
    }

    @Test
    fun `ordinary diagnostics are left alone`() {
        val line = "2026-11-09T09:26:51.123+05:30 I [HealthConnect] status=Ready granted=5"
        assertEquals(line, LogMasking.mask(line))
        assertFalse(LogMasking.containsSecret(line))

        val steps = "Sensor counted from midnight, steps=8240 goal=8000"
        assertEquals(steps, LogMasking.mask(steps))
    }

    @Test
    fun `masking is idempotent`() {
        val once = LogMasking.mask("token=abc123 email a@b.co")
        assertEquals(once, LogMasking.mask(once))
    }
}

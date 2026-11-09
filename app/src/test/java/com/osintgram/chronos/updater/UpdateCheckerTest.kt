package com.osintgram.chronos.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for the two parts of the updater that can be checked without a device:
 * the version comparison and the APK sniffing that stops a proxy error page
 * from being handed to the installer.
 */
class UpdateCheckerTest {

    @Test
    fun `the v prefix and surrounding spaces are ignored`() {
        assertEquals("6.2.0", VersionCompare.normalise(" v6.2.0 "))
        assertEquals("6.2.0", VersionCompare.normalise("V6.2.0"))
        assertTrue(VersionCompare.isNewer("v6.2.0", "6.1.0"))
    }

    @Test
    fun `a higher release tag is an update`() {
        assertTrue(VersionCompare.isNewer("6.2.0", "6.1.0"))
        assertTrue(VersionCompare.isNewer("7.0.0", "6.99.99"))
        assertTrue(VersionCompare.isNewer("6.10.0", "6.9.0")) // numeric, not lexicographic
        assertTrue(VersionCompare.isNewer("6.2.1", "6.2.0"))
    }

    @Test
    fun `the same or an older tag is not an update`() {
        assertFalse(VersionCompare.isNewer("6.2.0", "6.2.0"))
        assertFalse(VersionCompare.isNewer("6.1.0", "6.2.0"))
        assertFalse(VersionCompare.isNewer("6.1", "6.1.0")) // missing parts count as zero
        assertFalse(VersionCompare.isNewer("6.0.0", "6.0.0.1"))
    }

    @Test
    fun `a real apk starts with PK and is accepted`() {
        val apk = File.createTempFile("chronos", ".apk")
        try {
            apk.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))
            assertTrue(ApkFile.looksLikeApk(apk))
        } finally {
            apk.delete()
        }
    }

    @Test
    fun `an html error page is rejected before it reaches the installer`() {
        val page = File.createTempFile("proxy", ".apk")
        try {
            page.writeText("<!DOCTYPE html><html><body>504 Gateway Time-out</body></html>")
            assertFalse(ApkFile.looksLikeApk(page))
        } finally {
            page.delete()
        }
    }

    @Test
    fun `download progress is monotonic, ends at the full size and is throttled`() {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() } // 4 MB
        val updates = mutableListOf<Pair<Long, Long>>()
        val out = java.io.ByteArrayOutputStream()
        val written = StreamCopy.copy(
            input = java.io.ByteArrayInputStream(payload),
            output = out,
            total = payload.size.toLong(),
        ) { done, total -> updates.add(done to total) }

        assertEquals(payload.size.toLong(), written)
        assertEquals(payload.size, out.size())
        assertEquals(0L, updates.first().first)                       // starts at zero
        assertEquals(payload.size.toLong(), updates.last().first)     // ends at the full size
        assertTrue(updates.all { it.second == payload.size.toLong() })
        assertTrue(updates.size <= 105)                               // throttled, not per chunk
        assertTrue(updates.zipWithNext().all { (a, b) -> b.first >= a.first }) // never goes back
    }

    @Test
    fun `progress still reports when the total is unknown`() {
        val payload = ByteArray(1024 * 1024)
        val updates = mutableListOf<Long>()
        StreamCopy.copy(
            input = java.io.ByteArrayInputStream(payload),
            output = java.io.ByteArrayOutputStream(),
            total = 0L,
        ) { done, _ -> updates.add(done) }
        assertEquals(0L, updates.first())
        assertEquals(payload.size.toLong(), updates.last())
        assertTrue(updates.size >= 2)
    }

    @Test
    fun `a missing or empty file is rejected`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "chronos-missing-${System.nanoTime()}.apk")
        assertFalse(ApkFile.looksLikeApk(missing))
        val empty = File.createTempFile("empty", ".apk")
        try {
            assertFalse(ApkFile.looksLikeApk(empty))
        } finally {
            empty.delete()
        }
    }
}

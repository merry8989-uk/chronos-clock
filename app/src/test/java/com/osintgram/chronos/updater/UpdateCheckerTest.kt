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

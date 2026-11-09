package com.osintgram.chronos.updater

/**
 * Dotted-numeric version comparison, kept free of Android types so it can be
 * unit-tested on the JVM. Missing parts count as zero, so "6.1" equals "6.1.0",
 * and the comparison is numeric — "6.10.0" is newer than "6.9.0", which a
 * plain string compare gets wrong.
 */
object VersionCompare {

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** "v6.2.0" and "6.2.0" mean the same release. */
    fun normalise(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    private fun parts(version: String): List<Int> =
        normalise(version).split('.').mapNotNull { it.toIntOrNull() }
}

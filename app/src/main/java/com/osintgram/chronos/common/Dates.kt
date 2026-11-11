package com.osintgram.chronos.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The date keys and the duration text, in one place.
 *
 * The app had four copies of "today as an ISO date" (wellness, tasks, screen
 * time, the wellness store) and two copies of the "1 h 20 m" formatter. This is
 * that code, once.
 */
object Dates {

    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** A day as "2026-11-12", the key every store uses. */
    fun key(date: LocalDate = LocalDate.now()): String = date.format(ISO)

    /** The day an instant falls on, in the device's zone, as a key. */
    fun keyOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(ISO)

    /** A key back to a date, or null when it is not one. */
    fun parse(key: String): LocalDate? = runCatching { LocalDate.parse(key, ISO) }.getOrNull()

    /** "1 h 20 m", "45 m", "0 m" — the way every card reads a duration. */
    fun hoursMinutes(totalMinutes: Long): String {
        if (totalMinutes <= 0L) return "0 m"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "$minutes m"
            minutes == 0L -> "$hours h"
            else -> "$hours h $minutes m"
        }
    }
}

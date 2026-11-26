package com.osintgram.chronos.screentime

import android.content.Context
import com.osintgram.chronos.common.Dates
import com.osintgram.chronos.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Where a "day" starts for the limits.
 *
 * A limit that resets at midnight is useless to someone who is still up at 1 am:
 * the new day hands them a fresh allowance in the middle of the night. The day
 * therefore starts at the hour the user chooses — 7 am by default — and every
 * limit, every counter and every screen-time total uses the same boundary.
 */
object DayBoundary {

    /** The hour the day starts, from Settings. */
    fun startHour(): Int = Settings.dayStartHour.value.coerceIn(0, 23)

    /** The instant the current day began. */
    fun startMillis(
        now: Long = System.currentTimeMillis(),
        startHour: Int = startHour(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayStart = today.atTime(LocalTime.of(startHour, 0)).atZone(zone).toInstant().toEpochMilli()
        return if (now >= todayStart) {
            todayStart
        } else {
            today.minusDays(1).atTime(LocalTime.of(startHour, 0)).atZone(zone).toInstant().toEpochMilli()
        }
    }

    /** The instant the current day ends — the next start boundary. */
    fun endMillis(now: Long = System.currentTimeMillis()): Long =
        startMillis(now).let { start ->
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().plusDays(1)
            date.atTime(LocalTime.of(startHour(), 0)).atZone(zone).toInstant().toEpochMilli()
        }

    /**
     * The key a moment belongs to: the date of the day that began at the start
     * hour. 1 am on the 27th still belongs to the 26th when the day starts at 7.
     */
    fun key(
        now: Long = System.currentTimeMillis(),
        startHour: Int = startHour(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val start = startMillis(now, startHour, zone)
        return Dates.key(Instant.ofEpochMilli(start).atZone(zone).toLocalDate())
    }

    /** Today's key, for callers that do not want to think about millis. */
    fun todayKey(): String = key()

    /** True when [millis] falls inside the day that is running now. */
    fun isToday(millis: Long, now: Long = System.currentTimeMillis()): Boolean =
        millis >= startMillis(now) && millis < endMillis(now)

    /** The date a day key refers to, or null when it is not a date. */
    fun dateOf(key: String): LocalDate? = Dates.parse(key)
}

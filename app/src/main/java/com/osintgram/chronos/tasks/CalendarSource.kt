package com.osintgram.chronos.tasks

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * The phone's calendar, read and written through the system CalendarContract.
 *
 * Reading powers the Calendar view; writing is what "Add to phone calendar"
 * does on a task, so the same task exists in the user's own calendar app too.
 * Both are guarded: no permission, no calendar app or a read-only account
 * means the feature reports itself as unavailable instead of crashing.
 */
object CalendarSource {

    private const val CONTRIBUTOR = 500 // CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

    fun hasRead(context: Context): Boolean = granted(context, Manifest.permission.READ_CALENDAR)

    fun hasWrite(context: Context): Boolean = granted(context, Manifest.permission.WRITE_CALENDAR)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Events overlapping the given days, from every visible calendar. */
    fun eventsBetween(context: Context, from: LocalDate, to: LocalDate): List<CalendarEvent> {
        if (!hasRead(context)) return emptyList()
        val start = TaskLogic.startOfDay(from)
        val end = TaskLogic.endOfDay(to)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR,
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    val out = mutableListOf<CalendarEvent>()
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(1) ?: continue
                        out.add(
                            CalendarEvent(
                                id = cursor.getLong(0),
                                title = title,
                                begin = cursor.getLong(2),
                                end = cursor.getLong(3),
                                allDay = cursor.getInt(4) == 1,
                                calendarName = cursor.getString(5) ?: "",
                                color = cursor.getInt(6),
                            ),
                        )
                    }
                    out
                }
                .orEmpty()
        }.onFailure {
            DiagnosticsLogger.log("Calendar", "Read failed: ${it.message}")
        }.getOrDefault(emptyList())
    }

    /** The first calendar the user can write to, or null when there is none. */
    fun writableCalendarId(context: Context): Long? {
        if (!hasWrite(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE}=1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?"
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                arrayOf(CONTRIBUTOR.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.onFailure {
            DiagnosticsLogger.log("Calendar", "Calendar list failed: ${it.message}")
        }.getOrNull()
    }

    /** Writes a task into the phone calendar and returns the event id. */
    fun insertTask(context: Context, task: Task): Long? {
        if (!hasWrite(context)) return null
        val calendarId = writableCalendarId(context) ?: return null
        val zone = ZoneId.systemDefault()
        val start = if (task.allDay) {
            // All-day events are stored as UTC midnight of that date.
            TaskLogic.dateOf(task.dueAt).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            task.dueAt
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, task.title)
            if (task.notes.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, task.notes)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (task.allDay) {
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.DTEND, start + 24L * 60 * 60 * 1000)
            } else {
                put(CalendarContract.Events.ALL_DAY, 0)
                put(CalendarContract.Events.DTEND, start + 30L * 60 * 1000)
            }
        }
        return runCatching {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        }.onFailure {
            DiagnosticsLogger.log("Calendar", "Write failed: ${it.message}")
        }.getOrNull()
    }

    fun deleteEvent(context: Context, eventId: Long): Boolean {
        if (!hasWrite(context)) return false
        return runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null) > 0
        }.onFailure {
            DiagnosticsLogger.log("Calendar", "Delete failed: ${it.message}")
        }.getOrDefault(false)
    }

    /** Local time label for an event, "09:30" or "All day". */
    fun timeLabel(event: CalendarEvent): String {
        if (event.allDay) return "All day"
        val time = java.time.Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).toLocalTime()
        return String.format("%02d:%02d", time.hour, time.minute)
    }
}

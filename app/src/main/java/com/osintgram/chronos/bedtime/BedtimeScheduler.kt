package com.osintgram.chronos.bedtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Schedules the two moments bedtime mode needs: when it starts, and when it
 * ends. Both are exact alarms, re-armed on boot and after any settings change.
 */
object BedtimeScheduler {

    private const val REQUEST_START = 7001
    private const val REQUEST_END = 7002

    fun reschedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pending(context, BedtimeReceiver.ACTION_BEDTIME_START, REQUEST_START))
        manager.cancel(pending(context, BedtimeReceiver.ACTION_BEDTIME_END, REQUEST_END))

        if (!Settings.bedtimeEnabled.value) {
            DiagnosticsLogger.log("Bedtime", "Bedtime mode is off — nothing scheduled")
            return
        }

        val now = ZonedDateTime.now()
        val holidays = if (Settings.holidaySkipping.value) HolidayStore.schedulingDates()
        else emptySet<LocalDate>()

        val startAt = nextOccurrence(Settings.bedtimeStart.value, now, holidays)
        val endAt = nextOccurrence(
            Settings.bedtimeEnd.value,
            startAt.plusMinutes(1),
            holidays,
        )

        schedule(
            context,
            BedtimeReceiver.ACTION_BEDTIME_START,
            REQUEST_START,
            startAt.toInstant().toEpochMilli(),
        )
        schedule(
            context,
            BedtimeReceiver.ACTION_BEDTIME_END,
            REQUEST_END,
            endAt.toInstant().toEpochMilli(),
        )
        DiagnosticsLogger.log(
            "Bedtime",
            "Bedtime scheduled for $startAt, ends $endAt",
        )
    }

    /** The first moment at [minutesOfDay] strictly after [after]. */
    private fun nextOccurrence(
        minutesOfDay: Int,
        after: ZonedDateTime,
        holidays: Set<LocalDate>,
    ): ZonedDateTime {
        val hour = (minutesOfDay / 60).coerceIn(0, 23)
        val minute = (minutesOfDay % 60).coerceIn(0, 59)
        for (offset in 0..8) {
            val date = after.toLocalDate().plusDays(offset.toLong())
            if (date in holidays) continue
            val candidate = date.atTime(hour, minute).atZone(after.zone)
            if (candidate.isAfter(after)) return candidate
        }
        return after.plusDays(1)
    }

    private fun schedule(
        context: Context,
        actionName: String,
        requestCode: Int,
        triggerAt: Long,
    ) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = pending(context, actionName, requestCode)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        }.onFailure {
            DiagnosticsLogger.log("Bedtime", "Could not schedule $actionName: ${it.message}")
        }
    }

    private fun pending(context: Context, actionName: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, BedtimeReceiver::class.java).apply { action = actionName },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The alarm that will wake the user, for the bedtime screen. */
    fun nextWakeAlarmLabel(): String {
        val now = ZonedDateTime.now()
        val holidays = HolidayStore.schedulingDates()
        val next = AlarmStore.all()
            .filter { it.enabled }
            .mapNotNull { alarm ->
                Alarm.nextTriggerMillis(alarm, now, holidays)?.let { alarm to it }
            }
            .minByOrNull { it.second } ?: return "No alarm set"

        val (alarm, triggerAt) = next
        val use24 = Settings.use24Hour.value
        val time = alarm.timeText(use24) + if (use24) "" else " " + alarm.meridiem(false)
        val label = alarm.label.takeIf { it.isNotBlank() }
        val inMinutes = ((triggerAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        val hours = inMinutes / 60
        val mins = inMinutes % 60
        val remaining = if (hours > 0) "in ${hours}h ${mins}m" else "in ${mins}m"
        return buildString {
            append(time)
            if (label != null) append(" · ").append(label)
            append(" — ").append(remaining)
        }
    }
}

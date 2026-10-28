package com.osintgram.chronos.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import java.time.ZonedDateTime

/**
 * Schedules alarms with [AlarmManager.setAlarmClock], which is the only API
 * that reliably wakes the device and shows the status-bar alarm icon.
 */
object AlarmScheduler {

    private const val SNOOZE_REQUEST_OFFSET = 100_000

    fun rescheduleAll(context: Context) {
        val holidays = HolidayStore.schedulingDates()
        val now = ZonedDateTime.now()
        AlarmStore.all().forEach { alarm ->
            if (alarm.enabled) schedule(context, alarm, holidays, now) else cancel(context, alarm.id)
        }
        DiagnosticsLogger.log("AlarmScheduler", "Rescheduled all enabled alarms")
    }

    fun schedule(
        context: Context,
        alarm: Alarm,
        holidays: Set<java.time.LocalDate> = HolidayStore.schedulingDates(),
        now: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val trigger = Alarm.nextTriggerMillis(alarm, now, holidays) ?: run {
            cancel(context, alarm.id)
            DiagnosticsLogger.log("AlarmScheduler", "Alarm ${alarm.id} has no future trigger")
            return
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = broadcastIntent(context, alarm.id, snoozeCount = 0)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent), operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
                DiagnosticsLogger.log(
                    "AlarmScheduler",
                    "Exact alarms not permitted — alarm ${alarm.id} scheduled inexactly",
                )
            }
            DiagnosticsLogger.log("AlarmScheduler", "Alarm ${alarm.id} scheduled for $trigger")
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            DiagnosticsLogger.log("AlarmScheduler", "Fell back to inexact alarm: ${e.message}")
        }
    }

    /** Re-arms a ringing alarm [minutes] from now, up to the user's snooze limit. */
    fun snooze(context: Context, alarm: Alarm, minutes: Int, snoozeCount: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + minutes * 60_000L
        val operation = broadcastIntent(context, alarm.id, snoozeCount)
        try {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    trigger,
                    PendingIntent.getActivity(
                        context,
                        alarm.id.toInt(),
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ),
                operation,
            )
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        }
        DiagnosticsLogger.log(
            "AlarmScheduler",
            "Alarm ${alarm.id} snoozed $minutes min (snooze #$snoozeCount)",
        )
    }

    fun cancel(context: Context, alarmId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(broadcastIntent(context, alarmId, snoozeCount = 0))
        manager.cancel(broadcastIntent(context, alarmId, snoozeCount = 1))
    }

    private fun broadcastIntent(context: Context, alarmId: Long, snoozeCount: Int): PendingIntent {
        val requestCode = (alarmId + if (snoozeCount > 0) SNOOZE_REQUEST_OFFSET else 0).toInt()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** True when the user has granted exact-alarm scheduling (Android 12+). */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    /** Volume the ringing service should target, honouring the user's setting. */
    fun targetVolume(): Float = Settings.alarmVolume.value.coerceIn(0.05f, 1f)
}

package com.osintgram.chronos.tasks

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.R
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import java.time.LocalTime

/**
 * Reminders for tasks that have a due time. Uses the same exact-alarm path the
 * clock itself relies on, so a reminder fires on the minute instead of "some
 * time around then"; when the exact-alarm permission is refused it falls back
 * to an inexact alarm rather than dropping the reminder.
 */
object TaskReminders {

    const val CHANNEL_ID = "task_reminders"

    /** All-day tasks remind at this local time on their day. */
    private val ALL_DAY_TIME: LocalTime = LocalTime.of(9, 0)

    /** The instant a reminder should fire for [task], or null when it is not worth one. */
    fun reminderAt(task: Task): Long? {
        if (task.done) return null
        val now = System.currentTimeMillis()
        val base = if (task.allDay) {
            TaskLogic.dateOf(task.dueAt)
                .atTime(ALL_DAY_TIME)
                .atZone(TaskLogic.zone())
                .toInstant()
                .toEpochMilli()
        } else {
            task.dueAt
        }
        return base.takeIf { it > now }
    }

    fun schedule(context: Context, task: Task) {
        val at = reminderAt(task) ?: run {
            cancel(context, task.id)
            return
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskReminderReceiver.EXTRA_TITLE, task.title)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(task.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            val exact = manager.canScheduleExactAlarms()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
            DiagnosticsLogger.log("TaskReminders", "Reminder for ${task.id} at $at (exact=$exact)")
        }.onFailure {
            DiagnosticsLogger.log("TaskReminders", "Could not schedule ${task.id}: ${it.message}")
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { manager.cancel(pending) }
    }

    /** Re-arms every future reminder, called after boot and after an app update. */
    fun rescheduleAll(context: Context) {
        TaskStore.allTasks().forEach { schedule(context, it) }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Task reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Reminders for tasks with a due time" },
        )
    }

    private fun requestCode(taskId: Long): Int = (taskId % Int.MAX_VALUE).toInt()
}

/** Posts the reminder notification for one task. */
class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Task due" }
        DiagnosticsLogger.log("TaskReminders", "Reminder fired for $taskId")

        TaskReminders.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, TaskReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Due now")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        }.onFailure {
            DiagnosticsLogger.log("TaskReminders", "Could not post: ${it.message}")
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "task_title"
    }
}

package com.osintgram.chronos.bedtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.osintgram.chronos.R
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * Fires at bedtime and at wake-up. Turns Do Not Disturb on and off (only when
 * the user granted notification-policy access and switched the option on) and
 * posts a quiet reminder.
 */
class BedtimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_BEDTIME_START -> startBedtime(context)
            ACTION_BEDTIME_END -> endBedtime(context)
            else -> return
        }
        // Chain the next night's two alarms.
        BedtimeScheduler.reschedule(context)
    }

    private fun startBedtime(context: Context) {
        DiagnosticsLogger.log("Bedtime", "Bedtime started")

        if (Settings.bedtimeDnd.value) {
            if (BedtimeDnd.enable(context)) {
                prefs(context).edit().putBoolean(KEY_DND_SET_BY_US, true).apply()
            } else {
                DiagnosticsLogger.log(
                    "Bedtime",
                    "Do Not Disturb not switched: notification policy access missing",
                )
            }
        }

        if (Settings.bedtimeNotify.value) {
            notify(
                context = context,
                title = "Time to wind down",
                text = "Bedtime mode is on. Your alarm is still set.",
                channelId = CHANNEL_BEDTIME,
            )
        }
    }

    private fun endBedtime(context: Context) {
        DiagnosticsLogger.log("Bedtime", "Bedtime ended")
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_DND_SET_BY_US, false)) {
            BedtimeDnd.disable(context)
            prefs.edit().putBoolean(KEY_DND_SET_BY_US, false).apply()
        }
    }

    private fun notify(
        context: Context,
        title: String,
        text: String,
        channelId: String,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Bedtime",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Wind-down reminder and wake-up" },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            7100,
            Intent(context, BedtimeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, "Open bedtime screen", open)
                .build(),
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("chronos_bedtime", Context.MODE_PRIVATE)

    companion object {
        const val ACTION_BEDTIME_START = "com.osintgram.chronos.bedtime.START"
        const val ACTION_BEDTIME_END = "com.osintgram.chronos.bedtime.END"
        const val CHANNEL_BEDTIME = "chronos_bedtime"
        const val NOTIFICATION_ID = 4501
        const val KEY_DND_SET_BY_US = "dnd_set_by_us"
    }
}

/** Do Not Disturb control, which needs the notification-policy permission. */
object BedtimeDnd {

    fun canControl(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.isNotificationPolicyAccessGranted
    }

    fun enable(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false
        return runCatching {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        }.getOrDefault(false)
    }

    fun disable(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false
        return runCatching {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            true
        }.getOrDefault(false)
    }

    fun isOn(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false
        return manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }
}

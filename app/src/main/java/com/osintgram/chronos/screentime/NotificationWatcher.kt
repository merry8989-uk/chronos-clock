package com.osintgram.chronos.screentime

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * Counts notifications per app — and nothing else.
 *
 * A notification listener can read every notification on the phone, which is
 * why this one stores a **number** per package and day, never the title, text,
 * sender or reply action. The counts feed the Screen time tab; nothing is sent
 * anywhere.
 */
class NotificationWatcher : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        DiagnosticsLogger.log("ScreenTime", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        DiagnosticsLogger.log("ScreenTime", "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        if (notification.isOngoing) return
        if (!isCountable(notification.notification)) return
        ScreenTimeStore.recordNotification(notification.packageName, notification.postTime)
    }

    /** Group summaries and silent updates would double-count the same alert. */
    private fun isCountable(notification: Notification?): Boolean {
        val n = notification ?: return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return true
    }

    companion object {

        /** True when the user has switched Chronos on under Notification access. */
        fun isEnabled(context: Context): Boolean = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)

        fun openSettings(context: Context): Boolean = runCatching {
            context.startActivity(
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.onFailure {
            DiagnosticsLogger.log("ScreenTime", "Notification access screen unavailable: ${it.message}")
        }.getOrDefault(false)

        /** The component the user must enable, for the message on screen. */
        fun component(context: Context): ComponentName =
            ComponentName(context.packageName, NotificationWatcher::class.java.name)
    }
}

package com.osintgram.chronos.permissions

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * One row on the Special access screen.
 *
 * [usedBy] says what the app actually does with the access. Where it says
 * "not used", the row exists only so the switch can be found and explained —
 * the app does not read that data.
 */
data class AccessItem(
    val title: String,
    val state: String,
    val granted: Boolean?,
    val usedBy: String,
    val openSettings: (() -> Unit)?,
)

/**
 * The special accesses Android keeps outside the normal permission dialog.
 * Each one is a user toggle in system settings, so the app can only report the
 * state and deep-link to the screen that changes it.
 */
object SpecialAccess {

    fun items(context: Context): List<AccessItem> = listOf(
        AccessItem(
            title = "Install unknown apps",
            state = if (canInstall(context)) "Allowed" else "Not allowed",
            granted = canInstall(context),
            usedBy = "In-app update: installing the downloaded APK",
            openSettings = {
                start(context, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri(context)))
            },
        ),
        AccessItem(
            title = "Alarms and reminders",
            state = if (canScheduleExactAlarms(context)) "Exact alarms allowed" else "Inexact alarms only",
            granted = canScheduleExactAlarms(context),
            usedBy = "Alarms, timers and task reminders firing on the minute",
            openSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))
                }
            },
        ),
        AccessItem(
            title = "Do Not Disturb access",
            state = if (canChangeDnd(context)) "Allowed" else "Not allowed",
            granted = canChangeDnd(context),
            usedBy = "Bedtime mode, when you switch Do Not Disturb on",
            openSettings = {
                start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
        ),
        AccessItem(
            title = "Battery optimisation",
            state = if (ignoringBattery(context)) "Unrestricted" else "Optimised",
            granted = ignoringBattery(context),
            usedBy = "Alarms and the six-hourly health sync surviving Doze",
            openSettings = {
                start(
                    context,
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
                )
            },
        ),
        AccessItem(
            title = "Notifications",
            state = if (notificationsEnabled(context)) "Allowed" else "Blocked",
            granted = notificationsEnabled(context),
            usedBy = "Alarm ringing, timer finished, task reminders",
            openSettings = {
                start(
                    context,
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        ),
        AccessItem(
            title = "Usage access",
            state = if (hasUsageAccess(context)) "Allowed" else "Not allowed",
            granted = hasUsageAccess(context),
            usedBy = "Screen time: app usage, launch counts and the limit watcher",
            openSettings = { start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        ),
        AccessItem(
            title = "All files access",
            state = if (hasAllFiles(context)) "Allowed" else "Not allowed",
            granted = hasAllFiles(context),
            usedBy = "Not used by any feature — backups go through the system file picker",
            openSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    start(
                        context,
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            packageUri(context),
                        ),
                    )
                }
            },
        ),
        AccessItem(
            title = "Modify system settings",
            state = if (canWriteSettings(context)) "Allowed" else "Not allowed",
            granted = canWriteSettings(context),
            usedBy = "Not used by any feature — Chronos keeps its own settings",
            openSettings = {
                start(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context)))
            },
        ),
        AccessItem(
            title = "Notification access",
            state = if (notificationAccess(context)) "Allowed" else "Not allowed",
            granted = notificationAccess(context),
            usedBy = "Screen time: counts notifications per app — the text is never read or stored",
            openSettings = {
                start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        ),
        AccessItem(
            title = "Wi-Fi control",
            state = "Granted at install",
            granted = true,
            usedBy = "Not used by any feature — the update check only reads the connection type",
            openSettings = null,
        ),
        AccessItem(
            title = "Turn screen on",
            state = "Granted at install",
            granted = true,
            usedBy = "The alarm ring screen waking the display",
            openSettings = null,
        ),
    )

    // ---- State readers -----------------------------------------------------

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun canChangeDnd(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }

    fun ignoringBattery(context: Context): Boolean {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching {
            manager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    fun notificationsEnabled(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(false)

    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun hasAllFiles(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            false
        }

    fun canWriteSettings(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    fun notificationAccess(context: Context): Boolean =
        com.osintgram.chronos.screentime.NotificationWatcher.isEnabled(context)

    fun hasCalendarRead(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_CALENDAR,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // ---- Internals ---------------------------------------------------------

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun start(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            DiagnosticsLogger.log("SpecialAccess", "Could not open settings: ${it.message}")
        }
    }
}

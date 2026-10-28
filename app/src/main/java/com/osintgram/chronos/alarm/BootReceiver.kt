package com.osintgram.chronos.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * Re-arms every enabled alarm after a reboot, app update, clock change or
 * timezone change — AlarmManager forgets all of its alarms otherwise.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                DiagnosticsLogger.log("BootReceiver", "Re-arming alarms after ${intent.action}")
                AlarmScheduler.rescheduleAll(context)
                com.osintgram.chronos.bedtime.BedtimeScheduler.reschedule(context)
                // Touch the store so a fresh install also seeds defaults.
                AlarmStore.all()
            }
        }
    }
}

package com.osintgram.chronos.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/** Fires when an alarm's scheduled moment arrives. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        DiagnosticsLogger.log("AlarmReceiver", "Alarm $alarmId fired (snooze #$snoozeCount)")

        val alarm = AlarmStore.byId(alarmId)
        if (alarm == null) {
            DiagnosticsLogger.log("AlarmReceiver", "Alarm $alarmId no longer exists — ignoring")
            return
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // A one-shot alarm switches itself off once it has rung.
        if (alarm.repeatDays.isEmpty() && !alarm.deleteAfterRing) {
            AlarmStore.setEnabled(alarm.id, false)
        }
    }

    companion object {
        const val ACTION_ALARM = "com.osintgram.chronos.ACTION_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
    }
}

package com.osintgram.chronos.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.stopwatch.StopwatchEngine
import com.osintgram.chronos.timer.TimerEngine

/** Handles every tap on a Chronos widget, plus the once-a-minute refresh. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ACTION_TICK -> WidgetUpdater.refreshAll(context)

            ACTION_TOGGLE_ALARM -> {
                val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val alarm = AlarmStore.byId(id)
                if (alarm != null) {
                    val next = !alarm.enabled
                    AlarmStore.setEnabled(id, next)
                    if (next) {
                        AlarmScheduler.schedule(context, alarm.copy(enabled = true))
                    } else {
                        AlarmScheduler.cancel(context, id)
                    }
                    DiagnosticsLogger.log("WidgetAction", "Alarm $id -> enabled=$next")
                }
                WidgetUpdater.refreshAll(context)
            }

            ACTION_TIMER_START -> {
                if (TimerEngine.state.value.remainingMs <= 0L) {
                    TimerEngine.setDuration(5 * 60_000L)
                }
                TimerEngine.start(context)
                WidgetUpdater.refreshAll(context)
            }

            ACTION_TIMER_PAUSE -> {
                TimerEngine.pause(context)
                WidgetUpdater.refreshAll(context)
            }

            ACTION_TIMER_RESET -> {
                TimerEngine.reset(context)
                WidgetUpdater.refreshAll(context)
            }

            ACTION_TIMER_PRESET -> {
                val seconds = intent.getLongExtra(EXTRA_SECONDS, 0L)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Timer"
                if (seconds > 0) {
                    TimerEngine.setDuration(seconds * 1000L)
                    TimerEngine.setLabel(label)
                    TimerEngine.start(context)
                }
                WidgetUpdater.refreshAll(context)
            }

            ACTION_STOPWATCH_START -> {
                StopwatchEngine.start()
                WidgetUpdater.refreshAll(context)
            }

            ACTION_STOPWATCH_PAUSE -> {
                StopwatchEngine.pause()
                WidgetUpdater.refreshAll(context)
            }

            ACTION_STOPWATCH_RESET -> {
                StopwatchEngine.reset()
                WidgetUpdater.refreshAll(context)
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.osintgram.chronos.widget.TICK"
        const val ACTION_TOGGLE_ALARM = "com.osintgram.chronos.widget.TOGGLE_ALARM"
        const val ACTION_TIMER_START = "com.osintgram.chronos.widget.TIMER_START"
        const val ACTION_TIMER_PAUSE = "com.osintgram.chronos.widget.TIMER_PAUSE"
        const val ACTION_TIMER_RESET = "com.osintgram.chronos.widget.TIMER_RESET"
        const val ACTION_TIMER_PRESET = "com.osintgram.chronos.widget.TIMER_PRESET"
        const val ACTION_STOPWATCH_START = "com.osintgram.chronos.widget.STOPWATCH_START"
        const val ACTION_STOPWATCH_PAUSE = "com.osintgram.chronos.widget.STOPWATCH_PAUSE"
        const val ACTION_STOPWATCH_RESET = "com.osintgram.chronos.widget.STOPWATCH_RESET"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_LABEL = "label"
    }
}

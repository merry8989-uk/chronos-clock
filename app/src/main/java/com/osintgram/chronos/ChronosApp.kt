package com.osintgram.chronos

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.WorldClockStore
import com.osintgram.chronos.diagnostics.DeviceMetrics
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.diagnostics.LogcatCollector
import kotlinx.coroutines.launch
import com.osintgram.chronos.stopwatch.StopwatchEngine
import com.osintgram.chronos.timer.TimerEngine
import com.osintgram.chronos.timer.TimerPresets

class ChronosApp : Application() {

    override fun onCreate() {
        super.onCreate()

        registerLifecycleLogging()

        DiagnosticsLogger.init(this)
        DiagnosticsLogger.initCrashMarker(this)
        DiagnosticsLogger.installCrashHandler()

        Settings.init(this)
        DiagnosticsLogger.setEnabled(Settings.diagnosticsEnabled.value)

        AlarmStore.init(this)
        HolidayStore.init(this)
        WorldClockStore.init(this)
        StopwatchEngine.init(this)
        TimerPresets.init(this)
        TimerEngine.init(this)
        com.osintgram.chronos.wellness.WellnessStore.init(this)
        com.osintgram.chronos.wellness.WellnessSync.scheduleIfEnabled(this)
        com.osintgram.chronos.tasks.TaskStore.init(this)
        com.osintgram.chronos.tasks.TaskReminders.ensureChannel(this)
        com.osintgram.chronos.tasks.TaskReminders.rescheduleAll(this)

        AlarmScheduler.rescheduleAll(this)
        com.osintgram.chronos.bedtime.BedtimeScheduler.reschedule(this)
        com.osintgram.chronos.data.FileJanitor.sweep(this)
        com.osintgram.chronos.widget.WidgetUpdater.refreshAll(this)
        DiagnosticsLogger.log(
            "ChronosApp",
            "Started version ${BuildConfig.VERSION_NAME}, session ${DiagnosticsLogger.sessionId}",
        )
        DeviceMetrics.log(this, BuildConfig.VERSION_NAME, "launch")

        // The platform buffer explains anything the app's own logger missed.
        if (Settings.logcatCapture.value) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                kotlinx.coroutines.delay(3_000)
                LogcatCollector.capture(this@ChronosApp)
            }
        }
    }

    /**
     * Activity lifecycle into the log: which screen came to the front, when it
     * paused, and whether it was recreated. Cheap, and the first thing that
     * explains "the app closed by itself".
     */
    private fun registerLifecycleLogging() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    DiagnosticsLogger.log(
                        "Activity",
                        "created ${activity.javaClass.simpleName} " +
                            if (savedInstanceState == null) "(fresh)" else "(restored)",
                    )
                }

                override fun onActivityStarted(activity: Activity) {
                    DiagnosticsLogger.log("Activity", "started ${activity.javaClass.simpleName}")
                }

                override fun onActivityResumed(activity: Activity) {
                    DiagnosticsLogger.log("Activity", "resumed ${activity.javaClass.simpleName}")
                }

                override fun onActivityPaused(activity: Activity) {
                    DiagnosticsLogger.log("Activity", "paused ${activity.javaClass.simpleName}")
                }

                override fun onActivityStopped(activity: Activity) {
                    DiagnosticsLogger.log("Activity", "stopped ${activity.javaClass.simpleName}")
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    DiagnosticsLogger.log(
                        "Activity",
                        "destroyed ${activity.javaClass.simpleName} " +
                            if (activity.isFinishing) "(finishing)" else "(recreated)",
                    )
                }
            },
        )
    }
}

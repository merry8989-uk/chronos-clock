package com.osintgram.chronos

import android.app.Application
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.SleepStore
import com.osintgram.chronos.data.WorldClockStore
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.stopwatch.StopwatchEngine
import com.osintgram.chronos.timer.TimerPresets

class ChronosApp : Application() {

    override fun onCreate() {
        super.onCreate()

        DiagnosticsLogger.init(this)
        DiagnosticsLogger.installCrashHandler()

        Settings.init(this)
        DiagnosticsLogger.setEnabled(Settings.diagnosticsEnabled.value)

        AlarmStore.init(this)
        HolidayStore.init(this)
        WorldClockStore.init(this)
        StopwatchEngine.init(this)
        TimerPresets.init(this)
        SleepStore.init(this)

        AlarmScheduler.rescheduleAll(this)
        DiagnosticsLogger.log("ChronosApp", "Started, version ${BuildConfig.VERSION_NAME}")
    }
}

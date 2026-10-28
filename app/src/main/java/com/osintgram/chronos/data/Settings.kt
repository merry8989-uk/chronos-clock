package com.osintgram.chronos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * One persisted, observable setting. Reading [value] inside a composable
 * subscribes it, so a change takes effect on screen immediately.
 */
class Setting<T>(val key: String, val default: T) {

    private val state = mutableStateOf(default)

    val value: T get() = state.value

    fun set(v: T) {
        state.value = v
        Settings.write(key, v)
    }

    fun load(v: T) {
        state.value = v
    }

    fun resetToDefault() = set(default)
}

/**
 * Every user-facing option in the app. All of them persist and all of them are
 * read by the screens that they control — there are no dead toggles here.
 */
object Settings {

    private const val PREFS = "chronos_settings"
    private var prefs: SharedPreferences? = null

    // ---- Appearance -------------------------------------------------------
    val themeMode = Setting("theme_mode", "system")        // system | light | dark
    val accent = Setting("accent", "blue")                 // blue | violet | teal | amber | rose | green
    val use24Hour = Setting("use_24_hour", false)
    val amoledBlack = Setting("amoled_black", true)
    val showSeconds = Setting("show_seconds", true)

    // ---- Logo / live clock ------------------------------------------------
    val logoStyle = Setting("logo_style", "analog")          // analog | digital
    val clockSecondHand = Setting("clock_second_hand", true)
    val clockSmooth = Setting("clock_smooth", true)
    val textScale = Setting("text_scale", 1.0f)

    // ---- Alarms -----------------------------------------------------------
    val defaultSnooze = Setting("default_snooze", 5)
    val defaultVibrate = Setting("default_vibrate", true)
    val defaultVolumeRamp = Setting("default_volume_ramp", true)
    val defaultSkipHolidays = Setting("default_skip_holidays", false)
    val defaultDeleteAfterRing = Setting("default_delete_after_ring", false)
    val holidaySkipping = Setting("holiday_skipping", true)
    val alarmVolume = Setting("alarm_volume", 0.8f)
    val snoozeLimit = Setting("snooze_limit", 3)
    val showCountdown = Setting("show_countdown", true)
    val fullScreenAlarm = Setting("full_screen_alarm", true)

    // ---- Timer ------------------------------------------------------------
    val timerRunningSound = Setting("timer_running_sound", true)
    val timerDoneSound = Setting("timer_done_sound", true)
    val timerVibrate = Setting("timer_vibrate", true)
    val keepScreenOnTimer = Setting("keep_screen_on_timer", true)
    val timerStepMinutes = Setting("timer_step_minutes", 1)
    val timerBubble = Setting("timer_bubble", false)

    // ---- Stopwatch --------------------------------------------------------
    val stopwatchSound = Setting("stopwatch_sound", true)
    val haptics = Setting("haptics", true)

    // ---- Sleep tracking ---------------------------------------------------
    val sleepSensitivity = Setting("sleep_sensitivity", "medium")   // low | medium | high
    val sleepClips = Setting("sleep_clips", true)

    // ---- General / diagnostics -------------------------------------------
    val keepScreenOn = Setting("keep_screen_on", false)
    val confirmDelete = Setting("confirm_delete", true)
    val diagnosticsEnabled = Setting("diagnostics_enabled", true)
    val updateWifiOnly = Setting("update_wifi_only", true)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs ?: return

        themeMode.load(p.getString(themeMode.key, themeMode.default) ?: "system")
        accent.load(p.getString(accent.key, accent.default) ?: "blue")
        use24Hour.load(p.getBoolean(use24Hour.key, use24Hour.default))
        amoledBlack.load(p.getBoolean(amoledBlack.key, amoledBlack.default))
        showSeconds.load(p.getBoolean(showSeconds.key, showSeconds.default))
        logoStyle.load(p.getString(logoStyle.key, logoStyle.default) ?: "analog")
        clockSecondHand.load(p.getBoolean(clockSecondHand.key, clockSecondHand.default))
        clockSmooth.load(p.getBoolean(clockSmooth.key, clockSmooth.default))
        textScale.load(p.getFloat(textScale.key, textScale.default))

        defaultSnooze.load(p.getInt(defaultSnooze.key, defaultSnooze.default))
        defaultVibrate.load(p.getBoolean(defaultVibrate.key, defaultVibrate.default))
        defaultVolumeRamp.load(p.getBoolean(defaultVolumeRamp.key, defaultVolumeRamp.default))
        defaultSkipHolidays.load(p.getBoolean(defaultSkipHolidays.key, defaultSkipHolidays.default))
        defaultDeleteAfterRing.load(
            p.getBoolean(defaultDeleteAfterRing.key, defaultDeleteAfterRing.default)
        )
        holidaySkipping.load(p.getBoolean(holidaySkipping.key, holidaySkipping.default))
        alarmVolume.load(p.getFloat(alarmVolume.key, alarmVolume.default))
        snoozeLimit.load(p.getInt(snoozeLimit.key, snoozeLimit.default))
        showCountdown.load(p.getBoolean(showCountdown.key, showCountdown.default))
        fullScreenAlarm.load(p.getBoolean(fullScreenAlarm.key, fullScreenAlarm.default))

        timerRunningSound.load(p.getBoolean(timerRunningSound.key, timerRunningSound.default))
        timerDoneSound.load(p.getBoolean(timerDoneSound.key, timerDoneSound.default))
        timerVibrate.load(p.getBoolean(timerVibrate.key, timerVibrate.default))
        keepScreenOnTimer.load(p.getBoolean(keepScreenOnTimer.key, keepScreenOnTimer.default))
        timerStepMinutes.load(p.getInt(timerStepMinutes.key, timerStepMinutes.default))
        timerBubble.load(p.getBoolean(timerBubble.key, timerBubble.default))

        stopwatchSound.load(p.getBoolean(stopwatchSound.key, stopwatchSound.default))
        haptics.load(p.getBoolean(haptics.key, haptics.default))
        sleepSensitivity.load(p.getString(sleepSensitivity.key, sleepSensitivity.default) ?: "medium")
        sleepClips.load(p.getBoolean(sleepClips.key, sleepClips.default))

        keepScreenOn.load(p.getBoolean(keepScreenOn.key, keepScreenOn.default))
        confirmDelete.load(p.getBoolean(confirmDelete.key, confirmDelete.default))
        diagnosticsEnabled.load(p.getBoolean(diagnosticsEnabled.key, diagnosticsEnabled.default))
        updateWifiOnly.load(p.getBoolean(updateWifiOnly.key, updateWifiOnly.default))
    }

    internal fun write(key: String, value: Any?) {
        val editor = prefs?.edit() ?: return
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            else -> return
        }
        editor.apply()
    }

    /** Puts every option back to its factory value. */
    fun resetAll() {
        themeMode.resetToDefault()
        accent.resetToDefault()
        use24Hour.resetToDefault()
        amoledBlack.resetToDefault()
        showSeconds.resetToDefault()
        logoStyle.resetToDefault()
        clockSecondHand.resetToDefault()
        clockSmooth.resetToDefault()
        textScale.resetToDefault()
        defaultSnooze.resetToDefault()
        defaultVibrate.resetToDefault()
        defaultVolumeRamp.resetToDefault()
        defaultSkipHolidays.resetToDefault()
        defaultDeleteAfterRing.resetToDefault()
        holidaySkipping.resetToDefault()
        alarmVolume.resetToDefault()
        snoozeLimit.resetToDefault()
        showCountdown.resetToDefault()
        fullScreenAlarm.resetToDefault()
        timerRunningSound.resetToDefault()
        timerDoneSound.resetToDefault()
        timerVibrate.resetToDefault()
        keepScreenOnTimer.resetToDefault()
        timerStepMinutes.resetToDefault()
        timerBubble.resetToDefault()
        stopwatchSound.resetToDefault()
        haptics.resetToDefault()
        sleepSensitivity.resetToDefault()
        sleepClips.resetToDefault()
        keepScreenOn.resetToDefault()
        confirmDelete.resetToDefault()
        diagnosticsEnabled.resetToDefault()
        updateWifiOnly.resetToDefault()
    }

    /** Every option as key/value pairs, for the settings backup file. */
    fun snapshot(): Map<String, Any> = mapOf(
        themeMode.key to themeMode.value,
        accent.key to accent.value,
        use24Hour.key to use24Hour.value,
        amoledBlack.key to amoledBlack.value,
        showSeconds.key to showSeconds.value,
        logoStyle.key to logoStyle.value,
        clockSecondHand.key to clockSecondHand.value,
        clockSmooth.key to clockSmooth.value,
        textScale.key to textScale.value,
        defaultSnooze.key to defaultSnooze.value,
        defaultVibrate.key to defaultVibrate.value,
        defaultVolumeRamp.key to defaultVolumeRamp.value,
        defaultSkipHolidays.key to defaultSkipHolidays.value,
        defaultDeleteAfterRing.key to defaultDeleteAfterRing.value,
        holidaySkipping.key to holidaySkipping.value,
        alarmVolume.key to alarmVolume.value,
        snoozeLimit.key to snoozeLimit.value,
        showCountdown.key to showCountdown.value,
        fullScreenAlarm.key to fullScreenAlarm.value,
        timerRunningSound.key to timerRunningSound.value,
        timerDoneSound.key to timerDoneSound.value,
        timerVibrate.key to timerVibrate.value,
        keepScreenOnTimer.key to keepScreenOnTimer.value,
        timerStepMinutes.key to timerStepMinutes.value,
        timerBubble.key to timerBubble.value,
        stopwatchSound.key to stopwatchSound.value,
        haptics.key to haptics.value,
        sleepSensitivity.key to sleepSensitivity.value,
        sleepClips.key to sleepClips.value,
        keepScreenOn.key to keepScreenOn.value,
        confirmDelete.key to confirmDelete.value,
        diagnosticsEnabled.key to diagnosticsEnabled.value,
        updateWifiOnly.key to updateWifiOnly.value,
    )

    /** Applies a settings backup. Unknown or wrong-typed keys are ignored. */
    fun restore(values: Map<String, Any>) {
        values.forEach { (key, raw) ->
            val text = raw.toString()
            val asBool = text.toBooleanStrictOrNull()
            when (key) {
                themeMode.key -> themeMode.set(text)
                accent.key -> accent.set(text)
                use24Hour.key -> asBool?.let { use24Hour.set(it) }
                amoledBlack.key -> asBool?.let { amoledBlack.set(it) }
                showSeconds.key -> asBool?.let { showSeconds.set(it) }
                logoStyle.key -> logoStyle.set(text)
                clockSecondHand.key -> asBool?.let { clockSecondHand.set(it) }
                clockSmooth.key -> asBool?.let { clockSmooth.set(it) }
                textScale.key -> text.toFloatOrNull()?.let { textScale.set(it) }
                defaultSnooze.key -> text.toIntOrNull()?.let { defaultSnooze.set(it) }
                defaultVibrate.key -> asBool?.let { defaultVibrate.set(it) }
                defaultVolumeRamp.key -> asBool?.let { defaultVolumeRamp.set(it) }
                defaultSkipHolidays.key -> asBool?.let { defaultSkipHolidays.set(it) }
                defaultDeleteAfterRing.key -> asBool?.let { defaultDeleteAfterRing.set(it) }
                holidaySkipping.key -> asBool?.let { holidaySkipping.set(it) }
                alarmVolume.key -> text.toFloatOrNull()?.let { alarmVolume.set(it) }
                snoozeLimit.key -> text.toIntOrNull()?.let { snoozeLimit.set(it) }
                showCountdown.key -> asBool?.let { showCountdown.set(it) }
                fullScreenAlarm.key -> asBool?.let { fullScreenAlarm.set(it) }
                timerRunningSound.key -> asBool?.let { timerRunningSound.set(it) }
                timerDoneSound.key -> asBool?.let { timerDoneSound.set(it) }
                timerVibrate.key -> asBool?.let { timerVibrate.set(it) }
                keepScreenOnTimer.key -> asBool?.let { keepScreenOnTimer.set(it) }
                timerStepMinutes.key -> text.toIntOrNull()?.let { timerStepMinutes.set(it) }
                timerBubble.key -> asBool?.let { timerBubble.set(it) }
                stopwatchSound.key -> asBool?.let { stopwatchSound.set(it) }
                haptics.key -> asBool?.let { haptics.set(it) }
                sleepSensitivity.key -> sleepSensitivity.set(text)
                sleepClips.key -> asBool?.let { sleepClips.set(it) }
                keepScreenOn.key -> asBool?.let { keepScreenOn.set(it) }
                confirmDelete.key -> asBool?.let { confirmDelete.set(it) }
                diagnosticsEnabled.key -> asBool?.let { diagnosticsEnabled.set(it) }
                updateWifiOnly.key -> asBool?.let { updateWifiOnly.set(it) }
            }
        }
    }
}

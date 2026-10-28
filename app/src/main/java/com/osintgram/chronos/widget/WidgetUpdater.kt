package com.osintgram.chronos.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import com.osintgram.chronos.MainActivity
import com.osintgram.chronos.R
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.Tones
import com.osintgram.chronos.stopwatch.StopwatchEngine
import com.osintgram.chronos.timer.TimerEngine
import com.osintgram.chronos.timer.TimerPresets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds every Chronos home-screen widget.
 *
 * Where the platform can animate by itself it does: [android.widget.AnalogClock]
 * redraws its own hour and minute hands, and [android.widget.Chronometer] counts
 * on its own, so the clock, timer and stopwatch widgets move without any
 * background work. The text widgets are refreshed by a once-a-minute alarm.
 */
object WidgetUpdater {

    const val TYPE_CLOCK = "clock"
    const val TYPE_CLOCK_DIGITAL = "clock_digital"
    const val TYPE_NEXT_ALARM = "next_alarm"
    const val TYPE_ALARM_LIST = "alarm_list"
    const val TYPE_TIMER = "timer"
    const val TYPE_STOPWATCH = "stopwatch"
    const val TYPE_HOLIDAYS = "holidays"
    const val TYPE_TIMER_PRESETS = "timer_presets"

    /** Updates every widget on the home screen. */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        listOf(
            TYPE_CLOCK to ClockWidgetProvider::class.java,
            TYPE_CLOCK_DIGITAL to DigitalClockWidgetProvider::class.java,
            TYPE_NEXT_ALARM to NextAlarmWidgetProvider::class.java,
            TYPE_ALARM_LIST to AlarmListWidgetProvider::class.java,
            TYPE_TIMER to TimerWidgetProvider::class.java,
            TYPE_STOPWATCH to StopwatchWidgetProvider::class.java,
            TYPE_HOLIDAYS to HolidaysWidgetProvider::class.java,
            TYPE_TIMER_PRESETS to TimerPresetsWidgetProvider::class.java,
        ).forEach { (type, provider) ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids != null && ids.isNotEmpty()) {
                ids.forEach { id -> manager.updateAppWidget(id, build(context, type)) }
            }
        }
        scheduleMinuteTicks(context)
    }

    fun build(context: Context, type: String): RemoteViews = when (type) {
        TYPE_CLOCK -> buildClock(context)
        TYPE_CLOCK_DIGITAL -> buildDigitalClock(context)
        TYPE_NEXT_ALARM -> buildNextAlarm(context)
        TYPE_ALARM_LIST -> buildAlarmList(context)
        TYPE_TIMER -> buildTimer(context)
        TYPE_STOPWATCH -> buildStopwatch(context)
        TYPE_HOLIDAYS -> buildHolidays(context)
        else -> buildTimerPresets(context)
    }

    // ------------------------------------------------------------------ clock

    /** Accent colour per theme choice, kept here so widget code stays lean. */
    private fun accentColor(): Int = when (Settings.accent.value) {
        "violet" -> 0xFF8B7CFF.toInt()
        "teal" -> 0xFF22C7A9.toInt()
        "amber" -> 0xFFFFB020.toInt()
        "rose" -> 0xFFFF6E8A.toInt()
        "green" -> 0xFF4CD07D.toInt()
        else -> 0xFF4C8DFF.toInt()
    }

    /** Background colour for the chosen style. */
    private fun backgroundColor(): Int = when (Settings.widgetBgStyle.value) {
        "transparent" -> 0x00000000
        "white" -> 0xFFF2F4F8.toInt()
        "blue" -> 0xFF1E3A66.toInt()
        "custom" -> Settings.widgetBgColor.value
        else -> 0xCC171B23.toInt()
    }

    /** Rounded preset drawable, or 0 when the user picked a custom colour. */
    private fun backgroundResource(): Int = when (Settings.widgetBgStyle.value) {
        "transparent" -> R.drawable.widget_bg_transparent
        "white" -> R.drawable.widget_bg_white
        "blue" -> R.drawable.widget_bg_blue
        "custom" -> 0
        else -> R.drawable.widget_bg
    }

    private fun buildClock(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        val textColor = Settings.widgetTextColor.value
        val accent = accentColor()
        val bitmap = ClockFaceRenderer.face(
            context = context,
            sizePx = 512,
            background = backgroundColor(),
            rimColor = accent,
            tickColor = textColor,
            hourHandColor = textColor,
            minuteHandColor = textColor,
            secondHandColor = accent,
            // Hour and minute hands only: the widget refreshes on the minute, so
            // a second hand would be visibly wrong between refreshes.
            showSecondHand = false,
        )
        views.setImageViewBitmap(R.id.analog_image, bitmap)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 100))
        return views
    }

    private fun buildDigitalClock(context: Context): RemoteViews {
        val layout = when (Settings.widgetClockFormat.value) {
            "12" -> R.layout.widget_clock_digital_12
            "24s" -> R.layout.widget_clock_digital_24s
            "24" -> R.layout.widget_clock_digital_24
            else -> R.layout.widget_clock_digital_12s
        }
        val views = RemoteViews(context.packageName, layout)
        val textColor = Settings.widgetTextColor.value

        val resource = backgroundResource()
        if (resource != 0) {
            views.setInt(R.id.widget_root, "setBackgroundResource", resource)
        } else {
            views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor())
        }

        views.setTextColor(R.id.text_clock, textColor)
        views.setTextColor(R.id.text_clock_date, accentColor())
        views.setTextViewTextSize(
            R.id.text_clock,
            TypedValue.COMPLEX_UNIT_SP,
            Settings.widgetTextSize.value.coerceIn(12, 48).toFloat(),
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 106))
        return views
    }

    private fun buildNextAlarm(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
        val use24 = Settings.use24Hour.value
        val now = ZonedDateTime.now()
        val next = AlarmStore.all()
            .filter { it.enabled }
            .mapNotNull { alarm ->
                Alarm.nextTriggerMillis(alarm, now, HolidayStore.schedulingDates())
                    ?.let { alarm to it }
            }
            .minByOrNull { it.second }

        if (next == null) {
            views.setTextViewText(R.id.next_time, "--:--")
            views.setTextViewText(R.id.next_label, "No alarm set")
            views.setTextViewText(R.id.next_countdown, "Tap to add one")
        } else {
            val (alarm, triggerAt) = next
            views.setTextViewText(
                R.id.next_time,
                alarm.timeText(use24) + if (use24) "" else " " + alarm.meridiem(false),
            )
            views.setTextViewText(
                R.id.next_label,
                alarm.label.ifBlank { alarm.repeatSummary() },
            )
            views.setTextViewText(R.id.next_countdown, countdown(triggerAt - System.currentTimeMillis()))
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 101))
        return views
    }

    private fun buildAlarmList(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alarm_list)
        val use24 = Settings.use24Hour.value
        val alarms = AlarmStore.all().take(3)
        val rowIds = listOf(
            Triple(R.id.row1_time, R.id.row1_label, R.id.row1_toggle),
            Triple(R.id.row2_time, R.id.row2_label, R.id.row2_toggle),
            Triple(R.id.row3_time, R.id.row3_label, R.id.row3_toggle),
        )
        val rowContainers = listOf(R.id.row1, R.id.row2, R.id.row3)

        rowContainers.forEachIndexed { index, containerId ->
            val alarm = alarms.getOrNull(index)
            if (alarm == null) {
                views.setViewVisibility(containerId, android.view.View.GONE)
            } else {
                views.setViewVisibility(containerId, android.view.View.VISIBLE)
                val (timeId, labelId, toggleId) = rowIds[index]
                views.setTextViewText(
                    timeId,
                    alarm.timeText(use24) + if (use24) "" else " " + alarm.meridiem(false),
                )
                views.setTextViewText(
                    labelId,
                    alarm.label.ifBlank { alarm.repeatSummary() },
                )
                views.setImageViewResource(
                    toggleId,
                    if (alarm.enabled) R.drawable.widget_toggle_on else R.drawable.widget_toggle_off,
                )
                views.setOnClickPendingIntent(
                    toggleId,
                    action(context, WidgetActionReceiver.ACTION_TOGGLE_ALARM, 200 + index,
                        alarm.id),
                )
            }
        }
        if (alarms.isEmpty()) {
            views.setTextViewText(R.id.list_header, "No alarms")
        } else {
            views.setTextViewText(R.id.list_header, "ALARMS")
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 102))
        return views
    }

    // ------------------------------------------------------------------ timer

    private fun buildTimer(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val state = TimerEngine.state.value

        if (state.running && state.remainingMs > 0) {
            views.setChronometerCountDown(R.id.timer_chrono, true)
            views.setChronometer(
                R.id.timer_chrono,
                SystemClock.elapsedRealtime() + state.remainingMs,
                null,
                true,
            )
            views.setTextViewText(R.id.timer_state, "Running · ${state.label}")
        } else {
            views.setChronometerCountDown(R.id.timer_chrono, true)
            views.setChronometer(
                R.id.timer_chrono,
                SystemClock.elapsedRealtime() + state.remainingMs,
                null,
                false,
            )
            views.setTextViewText(
                R.id.timer_state,
                if (state.finished) "Finished" else "Ready · ${state.label}",
            )
        }

        views.setOnClickPendingIntent(
            R.id.timer_start,
            action(context, WidgetActionReceiver.ACTION_TIMER_START, 300),
        )
        views.setOnClickPendingIntent(
            R.id.timer_pause,
            action(context, WidgetActionReceiver.ACTION_TIMER_PAUSE, 301),
        )
        views.setOnClickPendingIntent(
            R.id.timer_reset,
            action(context, WidgetActionReceiver.ACTION_TIMER_RESET, 302),
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 103))
        return views
    }

    private fun buildTimerPresets(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer_presets)
        val presets = TimerPresets.all().take(4)
        val buttonIds = listOf(
            R.id.preset1, R.id.preset2, R.id.preset3, R.id.preset4,
        )
        buttonIds.forEachIndexed { index, id ->
            val preset = presets.getOrNull(index)
            if (preset == null) {
                views.setViewVisibility(id, android.view.View.GONE)
            } else {
                views.setViewVisibility(id, android.view.View.VISIBLE)
                views.setTextViewText(id, preset.name)
                views.setOnClickPendingIntent(
                    id,
                    action(
                        context,
                        WidgetActionReceiver.ACTION_TIMER_PRESET,
                        310 + index,
                        seconds = preset.seconds,
                        label = preset.name,
                    ),
                )
            }
        }
        return views
    }

    private fun buildStopwatch(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_stopwatch)
        val state = StopwatchEngine.state.value
        val base = SystemClock.elapsedRealtime() - state.elapsedMs
        views.setChronometer(R.id.stopwatch_chrono, base, null, state.running)
        views.setTextViewText(
            R.id.stopwatch_state,
            if (state.running) "Running" else "Paused",
        )
        views.setOnClickPendingIntent(
            R.id.stopwatch_toggle,
            action(
                context,
                if (state.running) WidgetActionReceiver.ACTION_STOPWATCH_PAUSE
                else WidgetActionReceiver.ACTION_STOPWATCH_START,
                320,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.stopwatch_reset,
            action(context, WidgetActionReceiver.ACTION_STOPWATCH_RESET, 321),
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 104))
        return views
    }

    private fun buildHolidays(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_holidays)
        val today = LocalDate.now()
        val upcoming = mutableListOf<Pair<LocalDate, String>>()

        for (year in intArrayOf(today.year, today.year + 1)) {
            HolidayStore.builtIn.forEach { (name, md) ->
                val date = md.atYear(year)
                if (!date.isBefore(today)) upcoming.add(date to name)
            }
            HolidayStore.customDates().forEach { iso ->
                runCatching { LocalDate.parse(iso) }.getOrNull()?.let { date ->
                    if (!date.isBefore(today)) upcoming.add(date to "Custom holiday")
                }
            }
        }
        val next = upcoming.sortedBy { it.first }.take(3)

        val rows = listOf(
            R.id.hol1 to R.id.hol1_name,
            R.id.hol2 to R.id.hol2_name,
            R.id.hol3 to R.id.hol3_name,
        )
        val containers = listOf(R.id.hol1_row, R.id.hol2_row, R.id.hol3_row)
        containers.forEachIndexed { index, containerId ->
            val entry = next.getOrNull(index)
            if (entry == null) {
                views.setViewVisibility(containerId, android.view.View.GONE)
            } else {
                views.setViewVisibility(containerId, android.view.View.VISIBLE)
                val (dateId, nameId) = rows[index]
                views.setTextViewText(
                    dateId,
                    entry.first.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)),
                )
                views.setTextViewText(nameId, entry.second)
            }
        }
        if (next.isEmpty()) views.setTextViewText(R.id.hol_header, "No holidays ahead")
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 105))
        return views
    }

    // ------------------------------------------------------------- scheduling

    /**
     * A once-a-minute alarm keeps the text widgets current. AnalogClock and
     * Chronometer animate themselves, so nothing here runs per second.
     */
    fun scheduleMinuteTicks(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_TICK
        }
        val pending = PendingIntent.getBroadcast(
            context,
            900,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextMinute = System.currentTimeMillis() / 60_000 * 60_000 + 60_000
        runCatching {
            manager.setRepeating(AlarmManager.RTC, nextMinute, 60_000L, pending)
        }
    }

    fun cancelTicks(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_TICK
        }
        val pending = PendingIntent.getBroadcast(
            context,
            900,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(pending)
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(
        context: Context,
        actionName: String,
        requestCode: Int,
        alarmId: Long = -1L,
        seconds: Long = 0L,
        label: String = "",
    ): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = actionName
            putExtra(WidgetActionReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(WidgetActionReceiver.EXTRA_SECONDS, seconds)
            putExtra(WidgetActionReceiver.EXTRA_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun countdown(remainingMs: Long): String {
        if (remainingMs <= 0) return "now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
        val days = TimeUnit.MILLISECONDS.toDays(remainingMs)
        return when {
            minutes < 1 -> "in under a minute"
            minutes < 60 -> "in $minutes min"
            hours < 24 -> "in $hours h ${minutes % 60} min"
            else -> "in $days days"
        }
    }

    /** Sound name for the alarm-list widget subtitle, if ever needed. */
    fun soundLabel(context: Context, uri: String?): String =
        Tones.toneForUri(uri)?.title ?: "Device default"

    /** Used by the tick receiver to decide whether to keep ticking. */
    fun anyWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return listOf(
            ClockWidgetProvider::class.java,
            DigitalClockWidgetProvider::class.java,
            NextAlarmWidgetProvider::class.java,
            AlarmListWidgetProvider::class.java,
            TimerWidgetProvider::class.java,
            StopwatchWidgetProvider::class.java,
            HolidaysWidgetProvider::class.java,
            TimerPresetsWidgetProvider::class.java,
        ).any { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider))?.isNotEmpty() == true
        }
    }

    fun todayLabel(): String =
        Instant.now().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US))
}

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
 * Where the platform can animate by itself it does: [android.widget.Chronometer]
 * counts on its own, so the timer and stopwatch widgets move without any
 * background work. The analog clock is a bitmap drawn by ClockFaceRenderer and
 * redrawn on the minute, and every text widget is refreshed by the same
 * once-a-minute alarm.
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
    const val TYPE_SCREEN_TIME = "screen_time"

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
            TYPE_SCREEN_TIME to ScreenTimeWidgetProvider::class.java,
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
        TYPE_SCREEN_TIME -> buildScreenTime(context)
        else -> buildTimerPresets(context)
    }

    // ------------------------------------------------------------------ clock

    /**
     * Colours for the user's background choice. [WidgetTheme] owns the accent
     * and the contrast checks, so this file never holds a colour of its own.
     */
    private fun palette(): WidgetTheme.Palette = WidgetTheme.palette()

    private fun buildClock(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        val colors = palette()
        val bitmap = ClockFaceRenderer.face(
            context = context,
            sizePx = 320,
            background = colors.surface,
            // Rim and second hand carry the accent, checked against the face
            // background so they read on every background choice.
            rimColor = colors.accentInk,
            tickColor = colors.ink,
            hourHandColor = colors.ink,
            minuteHandColor = colors.ink,
            secondHandColor = colors.accentInk,
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
        val colors = palette()

        WidgetTheme.applyBackground(views, R.id.widget_root)

        views.setTextColor(R.id.text_clock, colors.ink)
        views.setTextColor(R.id.text_clock_date, colors.accentInk)
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
        val colors = palette()
        val use24 = Settings.use24Hour.value
        val now = ZonedDateTime.now()
        val next = AlarmStore.all()
            .filter { it.enabled }
            .mapNotNull { alarm ->
                Alarm.nextTriggerMillis(alarm, now, HolidayStore.schedulingDates())
                    ?.let { alarm to it }
            }
            .minByOrNull { it.second }

        views.setTextColor(R.id.next_header, colors.accentInk)
        views.setTextColor(R.id.next_time, colors.ink)
        views.setTextColor(R.id.next_label, colors.mutedInk)
        views.setTextColor(R.id.next_countdown, colors.accentInk)

        if (next == null) {
            views.setTextViewText(R.id.next_time, context.getString(R.string.widget_no_time))
            views.setTextViewText(R.id.next_label, context.getString(R.string.widget_no_alarm_set))
            views.setTextViewText(R.id.next_countdown, context.getString(R.string.widget_tap_to_add))
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
            // The countdown line also says the alarm is on, so the state is not
            // left to a tint alone.
            views.setTextViewText(
                R.id.next_countdown,
                countdown(context, triggerAt - System.currentTimeMillis()) + " · " +
                    context.getString(R.string.widget_state_on),
            )
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 101))
        return views
    }

    private fun buildAlarmList(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alarm_list)
        val colors = palette()
        val use24 = Settings.use24Hour.value
        val alarms = AlarmStore.all().take(3)
        val rowIds = listOf(
            Triple(R.id.row1_time, R.id.row1_label, R.id.row1_toggle),
            Triple(R.id.row2_time, R.id.row2_label, R.id.row2_toggle),
            Triple(R.id.row3_time, R.id.row3_label, R.id.row3_toggle),
        )
        val rowContainers = listOf(R.id.row1, R.id.row2, R.id.row3)
        val stateIds = listOf(R.id.row1_state, R.id.row2_state, R.id.row3_state)

        views.setTextColor(R.id.list_header, colors.accentInk)

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
                views.setTextColor(timeId, colors.ink)
                views.setTextColor(labelId, colors.mutedInk)
                // On or Off in words as well as in the dot, so the state does
                // not depend on telling two tints apart.
                views.setTextColor(
                    stateIds[index],
                    if (alarm.enabled) colors.accentInk else colors.mutedInk,
                )
                views.setTextViewText(
                    stateIds[index],
                    context.getString(
                        if (alarm.enabled) R.string.widget_state_on else R.string.widget_state_off,
                    ),
                )
                views.setImageViewResource(
                    toggleId,
                    if (alarm.enabled) R.drawable.widget_toggle_on else R.drawable.widget_toggle_off,
                )
                views.setContentDescription(
                    toggleId,
                    context.getString(
                        if (alarm.enabled) R.string.widget_cd_alarm_on
                        else R.string.widget_cd_alarm_off,
                    ),
                )
                views.setOnClickPendingIntent(
                    toggleId,
                    action(context, WidgetActionReceiver.ACTION_TOGGLE_ALARM, 200 + index,
                        alarm.id),
                )
            }
        }
        if (alarms.isEmpty()) {
            views.setTextViewText(R.id.list_header, context.getString(R.string.widget_no_alarms))
        } else {
            views.setTextViewText(R.id.list_header, context.getString(R.string.widget_header_alarms))
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 102))
        return views
    }

    // ------------------------------------------------------------------ timer

    private fun buildTimer(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val colors = palette()
        val state = TimerEngine.state.value

        views.setTextColor(R.id.timer_chrono, colors.ink)
        views.setTextColor(R.id.timer_state, colors.mutedInk)
        views.setTextColor(R.id.timer_start, colors.buttonInk)
        views.setTextColor(R.id.timer_pause, colors.buttonInk)
        views.setTextColor(R.id.timer_reset, colors.buttonInk)

        if (state.running && state.remainingMs > 0) {
            views.setChronometerCountDown(R.id.timer_chrono, true)
            views.setChronometer(
                R.id.timer_chrono,
                SystemClock.elapsedRealtime() + state.remainingMs,
                null,
                true,
            )
            views.setTextViewText(
                R.id.timer_state,
                context.getString(R.string.widget_timer_running, state.label),
            )
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
                if (state.finished) context.getString(R.string.widget_timer_finished)
                else context.getString(R.string.widget_timer_ready, state.label),
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
        val colors = palette()
        val presets = TimerPresets.all().take(4)
        val buttonIds = listOf(
            R.id.preset1, R.id.preset2, R.id.preset3, R.id.preset4,
        )
        buttonIds.forEach { id -> views.setTextColor(id, colors.buttonInk) }
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
        val colors = palette()
        val state = StopwatchEngine.state.value
        val base = SystemClock.elapsedRealtime() - state.elapsedMs
        views.setChronometer(R.id.stopwatch_chrono, base, null, state.running)
        views.setTextColor(R.id.stopwatch_chrono, colors.ink)
        views.setTextColor(R.id.stopwatch_state, colors.mutedInk)
        views.setTextColor(R.id.stopwatch_toggle, colors.buttonInk)
        views.setTextColor(R.id.stopwatch_reset, colors.buttonInk)
        views.setTextViewText(
            R.id.stopwatch_state,
            context.getString(
                if (state.running) R.string.widget_stopwatch_running
                else R.string.widget_state_paused,
            ),
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
        val colors = palette()
        val today = LocalDate.now()

        views.setTextColor(R.id.hol_header, colors.accentInk)
        val upcoming = mutableListOf<Pair<LocalDate, String>>()

        for (year in intArrayOf(today.year, today.year + 1)) {
            HolidayStore.builtIn.forEach { (name, md) ->
                val date = md.atYear(year)
                if (!date.isBefore(today)) upcoming.add(date to name)
            }
            HolidayStore.customDates().forEach { iso ->
                runCatching { LocalDate.parse(iso) }.getOrNull()?.let { date ->
                    if (!date.isBefore(today)) {
                        upcoming.add(date to context.getString(R.string.widget_custom_holiday))
                    }
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
                views.setTextColor(dateId, colors.accentInk)
                views.setTextColor(nameId, colors.ink)
            }
        }
        if (next.isEmpty()) {
            views.setTextViewText(R.id.hol_header, context.getString(R.string.widget_no_holidays))
        }
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

    /**
     * Screen time today. UsageStatsManager is asked at most every five minutes —
     * the minute tick rebuilds the views from the cached numbers, because
     * querying a whole day of usage events every sixty seconds would be rude to
     * the battery.
     */
    private var cachedDay: com.osintgram.chronos.screentime.DayScreenTime? = null
    private var cachedAt = 0L

    private fun buildScreenTime(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_screen_time)
        val colors = palette()
        WidgetTheme.applyBackground(views, R.id.widget_root)
        views.setTextColor(R.id.screen_label, colors.accentInk)
        views.setTextColor(R.id.screen_total, colors.ink)
        views.setTextColor(R.id.screen_top, colors.mutedInk)
        views.setTextColor(R.id.screen_meta, colors.accentInk)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 108))

        if (!com.osintgram.chronos.screentime.UsageSource.hasAccess(context)) {
            views.setTextViewText(R.id.screen_total, context.getString(R.string.widget_no_usage))
            views.setTextViewText(R.id.screen_top, context.getString(R.string.widget_usage_access))
            views.setTextViewText(R.id.screen_meta, context.getString(R.string.widget_tap_to_open))
            return views
        }

        val now = System.currentTimeMillis()
        val day = cachedDay?.takeIf { now - cachedAt < 5 * 60_000L }
            ?: runCatching {
                com.osintgram.chronos.screentime.UsageSource.today(
                    context,
                    com.osintgram.chronos.screentime.ScreenTimeStore.notificationsFor(),
                )
            }.getOrNull()?.also { cachedDay = it; cachedAt = now }
            ?: return views

        views.setTextViewText(
            R.id.screen_total,
            com.osintgram.chronos.screentime.ScreenTimeLogic.formatDuration(day.totalForegroundMillis),
        )
        val top = day.mostUsed
        views.setTextViewText(
            R.id.screen_top,
            if (top == null) context.getString(R.string.widget_nothing_used)
            else "${top.label} · " + com.osintgram.chronos.screentime.ScreenTimeLogic
                .formatDuration(top.foregroundMillis),
        )
        val alerts = day.apps.sumOf { it.notifications }
        val meta = StringBuilder()
            .append(context.getString(R.string.widget_screen_unlocks, day.unlockCount))
            .append(" · ")
            .append(context.getString(R.string.widget_screen_on, day.screenOnCount))
        if (alerts > 0) {
            meta.append(" · ").append(context.getString(R.string.widget_screen_alerts, alerts))
        }
        // Usage against the goal: said in a word, not left to a tint, and only
        // when a limit is set and one of those apps has passed it.
        if (overGoal(day)) {
            meta.append(" · ").append(context.getString(R.string.widget_state_over))
        }
        views.setTextViewText(R.id.screen_meta, meta.toString())
        return views
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

    private fun countdown(context: Context, remainingMs: Long): String {
        if (remainingMs <= 0) return context.getString(R.string.widget_countdown_now)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
        val days = TimeUnit.MILLISECONDS.toDays(remainingMs)
        return when {
            minutes < 1 -> context.getString(R.string.widget_countdown_soon)
            minutes < 60 -> context.getString(R.string.widget_countdown_minutes, minutes.toInt())
            hours < 24 -> context.getString(
                R.string.widget_countdown_hours,
                hours.toInt(),
                (minutes % 60).toInt(),
            )
            else -> context.getString(R.string.widget_countdown_days, days.toInt())
        }
    }

    /**
     * True when at least one app with a daily limit has passed it: the only
     * "usage against the goal" these widgets can see. With no limits set there
     * is no goal to be over, so nothing is claimed.
     */
    private fun overGoal(day: com.osintgram.chronos.screentime.DayScreenTime): Boolean {
        val limits = com.osintgram.chronos.screentime.ScreenTimeStore.allLimits()
        if (limits.isEmpty()) return false
        return day.apps.any { app ->
            val limit = limits[app.packageName] ?: return@any false
            com.osintgram.chronos.screentime.ScreenTimeLogic.limitReached(
                app.foregroundMillis,
                limit,
            )
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
            ScreenTimeWidgetProvider::class.java,
        ).any { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider))?.isNotEmpty() == true
        }
    }

    fun todayLabel(): String =
        Instant.now().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US))
}

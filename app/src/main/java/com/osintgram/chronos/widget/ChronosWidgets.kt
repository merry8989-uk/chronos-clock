package com.osintgram.chronos.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Shared behaviour for every Chronos widget: build, then keep refreshing. */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    abstract val type: String

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, WidgetUpdater.build(context, type))
        }
        WidgetUpdater.scheduleMinuteTicks(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleMinuteTicks(context)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetUpdater.anyWidgets(context)) WidgetUpdater.cancelTicks(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetUpdater.build(context, type))
    }
}

/** 1x1 (or larger) live analog clock — the logo, on the home screen. */
class ClockWidgetProvider : BaseWidgetProvider() {
    override val type = WidgetUpdater.TYPE_CLOCK
}

/** Next alarm, with a countdown. */
class NextAlarmWidgetProvider : BaseWidgetProvider() {
    override val type = WidgetUpdater.TYPE_NEXT_ALARM
}

/** Up to three alarms, each with its own on/off toggle. */
class AlarmListWidgetProvider : BaseWidgetProvider() {
    override val type = WidgetUpdater.TYPE_ALARM_LIST
}

/** Running timer with start / pause / reset. */
class TimerWidgetProvider : BaseWidgetProvider() {
    override val type = WidgetUpdater.TYPE_TIMER
}

/** Stopwatch with start / pause / reset. */
class StopwatchWidgetProvider : BaseWidgetProvider() {
    override val type = WidgetUpdater.TYPE_STOPWATCH
}

/** Upcoming holidays, from the built-in Indian list plus your own dates. */
class HolidaysWidgetProvider : BaseWidgetProvider() {
    override val type = WidgetUpdater.TYPE_HOLIDAYS
}

/** One tap starts a saved timer preset. */
class TimerPresetsWidgetProvider : BaseWidgetProvider() {
    override val type = WidgetUpdater.TYPE_TIMER_PRESETS
}

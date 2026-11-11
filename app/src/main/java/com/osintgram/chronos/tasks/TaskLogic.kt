package com.osintgram.chronos.tasks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * The date maths behind the four task levels — day, week, month, year — kept
 * free of Android types so it can be unit-tested on the JVM.
 *
 * Weeks run Monday to Sunday, which is the working week in India.
 */
object TaskLogic {

    val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone()).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli() - 1

    fun dateKey(date: LocalDate): String = com.osintgram.chronos.common.Dates.key(date)

    fun parseDate(key: String): LocalDate? = com.osintgram.chronos.common.Dates.parse(key)

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun monthStart(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun yearStart(date: LocalDate): LocalDate = date.withDayOfYear(1)

    /** The seven days of the week that contains [date], Monday first. */
    fun weekDays(date: LocalDate): List<LocalDate> {
        val start = weekStart(date)
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    /** Every day of the month that contains [date]. */
    fun monthDays(date: LocalDate): List<LocalDate> {
        val month = YearMonth.from(date)
        return (1..month.lengthOfMonth()).map { month.atDay(it) }
    }

    /** Every month of the year that contains [date]. */
    fun monthsOfYear(date: LocalDate): List<YearMonth> =
        (1..12).map { YearMonth.of(date.year, it) }

    /**
     * The weeks that overlap [month]. A week can appear in two months, which is
     * why the month view shows whole weeks rather than clipped ones.
     */
    fun weeksOfMonth(month: YearMonth): List<LocalDate> {
        val first = weekStart(month.atDay(1))
        val lastDay = month.atEndOfMonth()
        val starts = mutableListOf<LocalDate>()
        var cursor = first
        while (cursor <= lastDay) {
            starts.add(cursor)
            cursor = cursor.plusWeeks(1)
        }
        return starts
    }

    // ---- Filtering ---------------------------------------------------------

    fun tasksOn(tasks: List<Task>, date: LocalDate): List<Task> {
        val from = startOfDay(date)
        val to = endOfDay(date)
        return tasks.filter { it.dueAt in from..to }.sortedBy { it.dueAt }
    }

    fun tasksBetween(tasks: List<Task>, from: LocalDate, to: LocalDate): List<Task> {
        val start = startOfDay(from)
        val end = endOfDay(to)
        return tasks.filter { it.dueAt in start..end }.sortedBy { it.dueAt }
    }

    fun overdue(tasks: List<Task>, now: Long = System.currentTimeMillis()): List<Task> =
        tasks.filter { !it.done && it.dueAt < now }.sortedBy { it.dueAt }

    fun upcoming(tasks: List<Task>, now: Long = System.currentTimeMillis()): List<Task> =
        tasks.filter { !it.done && it.dueAt >= now }.sortedBy { it.dueAt }

    fun forProject(tasks: List<Task>, projectId: Long): List<Task> =
        tasks.filter { it.projectId == projectId }.sortedBy { it.dueAt }

    fun progress(tasks: List<Task>): Progress = Progress(tasks.count { it.done }, tasks.size)

    // ---- Rollups -----------------------------------------------------------

    fun dayBuckets(tasks: List<Task>, days: List<LocalDate>): List<DayBucket> =
        days.map { DayBucket(it, tasksOn(tasks, it)) }

    fun weekBuckets(tasks: List<Task>, weekStarts: List<LocalDate>): List<WeekBucket> =
        weekStarts.map { start ->
            WeekBucket(start, dayBuckets(tasks, (0..6).map { start.plusDays(it.toLong()) }))
        }

    fun monthBuckets(tasks: List<Task>, year: Int): List<MonthBucket> =
        (1..12).map { month ->
            val ym = YearMonth.of(year, month)
            MonthBucket(ym, weekBuckets(tasks, weeksOfMonth(ym)))
        }

    /** The level a date belongs to, for the header line. */
    fun weekLabel(date: LocalDate): String {
        val start = weekStart(date)
        val end = start.plusDays(6)
        val sameMonth = start.month == end.month
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        return if (sameMonth) "${start.format(fmt)} – ${end.dayOfMonth} ${end.month.name.take(3)}"
        else "${start.format(fmt)} – ${end.format(fmt)}"
    }

    // ---- Repeating tasks ---------------------------------------------------

    // ---- Checklists --------------------------------------------------------

    fun checklistProgress(task: Task): Progress =
        Progress(task.checklist.count { it.done }, task.checklist.size)

    fun toggleChecklistItem(task: Task, index: Int): Task =
        if (index !in task.checklist.indices) task
        else task.copy(
            checklist = task.checklist.mapIndexed { i, item ->
                if (i == index) item.copy(done = !item.done) else item
            },
        )

    fun addChecklistItem(task: Task, text: String): Task {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return task
        return task.copy(checklist = task.checklist + ChecklistItem(trimmed))
    }

    fun removeChecklistItem(task: Task, index: Int): Task =
        if (index !in task.checklist.indices) task
        else task.copy(checklist = task.checklist.filterIndexed { i, _ -> i != index })

    /** True when every checklist line is ticked and there is at least one. */
    fun checklistComplete(task: Task): Boolean =
        task.checklist.isNotEmpty() && task.checklist.all { it.done }

    /** The next due instant for a repeating task, or null when it does not repeat. */
    fun nextOccurrence(task: Task, afterMillis: Long = task.dueAt): Long? {
        val from = dateOf(maxOf(afterMillis, task.dueAt))
        val next = when (task.repeat) {
            Task.REPEAT_DAILY -> from.plusDays(1)
            Task.REPEAT_WEEKLY -> from.plusWeeks(1)
            Task.REPEAT_MONTHLY -> from.plusMonths(1)
            else -> return null
        }
        // Keep the time of day for timed tasks.
        val time = Instant.ofEpochMilli(task.dueAt).atZone(zone()).toLocalTime()
        return next.atTime(time).atZone(zone()).toInstant().toEpochMilli()
    }

    /** True when a task belongs to the year view's month list. */
    fun inMonth(task: Task, month: YearMonth): Boolean =
        YearMonth.from(dateOf(task.dueAt)) == month
}

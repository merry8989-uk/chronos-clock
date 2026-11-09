package com.osintgram.chronos.tasks

/** A project groups tasks that belong to the same piece of work. */
data class Project(
    val id: Long,
    val name: String,
    val color: Int,
    val dueAt: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * One task. A task always has a due instant; when [allDay] is true only the
 * calendar date matters and the time is ignored by every view.
 */
data class Task(
    val id: Long,
    val title: String,
    val notes: String = "",
    val dueAt: Long,
    val allDay: Boolean = true,
    /** 0 = low, 1 = normal, 2 = high. */
    val priority: Int = 1,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val projectId: Long? = null,
    /** none | daily | weekly | monthly */
    val repeat: String = REPEAT_NONE,
    val createdAt: Long = 0L,
    /** Set when the task was also written to the phone calendar. */
    val calendarEventId: Long? = null,
) {
    companion object {
        const val REPEAT_NONE = "none"
        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKLY = "weekly"
        const val REPEAT_MONTHLY = "monthly"
        val REPEATS = listOf(REPEAT_NONE, REPEAT_DAILY, REPEAT_WEEKLY, REPEAT_MONTHLY)
    }
}

/** Done / total for any group of tasks. */
data class Progress(val done: Int, val total: Int) {
    val percent: Float get() = if (total <= 0) 0f else done.toFloat() / total
    val isEmpty: Boolean get() = total == 0
}

/** The tasks of one day, used by the week and month views. */
data class DayBucket(
    val date: java.time.LocalDate,
    val tasks: List<Task>,
) {
    val progress: Progress get() = Progress(tasks.count { it.done }, tasks.size)
}

/** A Monday-to-Sunday week. */
data class WeekBucket(
    val start: java.time.LocalDate,
    val days: List<DayBucket>,
) {
    val allTasks: List<Task> get() = days.flatMap { it.tasks }
    val progress: Progress get() = Progress(allTasks.count { it.done }, allTasks.size)
    val end: java.time.LocalDate get() = start.plusDays(6)
}

/** One month of the year view. */
data class MonthBucket(
    val month: java.time.YearMonth,
    val weeks: List<WeekBucket>,
) {
    val allTasks: List<Task> get() = weeks.flatMap { it.allTasks }
    val progress: Progress get() = Progress(allTasks.count { it.done }, allTasks.size)
}

/** One event read from the phone calendar. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val calendarName: String,
    val color: Int,
)

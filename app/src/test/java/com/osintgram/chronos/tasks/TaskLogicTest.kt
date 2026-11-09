package com.osintgram.chronos.tasks

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The date maths behind the four task levels, the rollups the views use, and
 * the JSON round-trip. All pure JVM — no device needed.
 */
class TaskLogicTest {

    private val monday = LocalDate.of(2026, 11, 9) // a Monday

    @Test
    fun `weeks run monday to sunday`() {
        assertEquals(monday, TaskLogic.weekStart(monday))
        assertEquals(monday, TaskLogic.weekStart(monday.plusDays(6))) // Sunday
        assertEquals(monday.minusWeeks(1), TaskLogic.weekStart(monday.minusDays(1))) // Sunday before
        assertEquals(
            listOf(monday, monday.plusDays(1), monday.plusDays(2), monday.plusDays(3), monday.plusDays(4), monday.plusDays(5), monday.plusDays(6)),
            TaskLogic.weekDays(monday.plusDays(3)),
        )
    }

    @Test
    fun `month and year boundaries are the first day`() {
        assertEquals(LocalDate.of(2026, 11, 1), TaskLogic.monthStart(monday))
        assertEquals(LocalDate.of(2026, 1, 1), TaskLogic.yearStart(monday))
        assertEquals(30, TaskLogic.monthDays(monday).size)
        assertEquals(12, TaskLogic.monthsOfYear(monday).size)
    }

    @Test
    fun `the weeks of a month cover every day of it`() {
        val month = java.time.YearMonth.of(2026, 11)
        val weeks = TaskLogic.weeksOfMonth(month)
        val covered = weeks.flatMap { start -> (0..6).map { start.plusDays(it.toLong()) } }
        assertTrue(covered.contains(month.atDay(1)))
        assertTrue(covered.contains(month.atEndOfMonth()))
        assertTrue(weeks.all { it.dayOfWeek == java.time.DayOfWeek.MONDAY })
    }

    @Test
    fun `tasks are found on their own day and inside a range`() {
        val tasks = listOf(
            task(1, "Today", monday),
            task(2, "Tomorrow", monday.plusDays(1)),
            task(3, "Next week", monday.plusDays(8)),
        )
        assertEquals(listOf(1L), TaskLogic.tasksOn(tasks, monday).map { it.id })
        assertEquals(
            listOf(1L, 2L),
            TaskLogic.tasksBetween(tasks, monday, monday.plusDays(6)).map { it.id },
        )
    }

    @Test
    fun `day buckets feed week buckets feed month buckets`() {
        val tasks = listOf(
            task(1, "Mon", monday),
            task(2, "Wed", monday.plusDays(2), done = true),
            task(3, "Next month", LocalDate.of(2026, 12, 2)),
        )
        val week = TaskLogic.weekBuckets(tasks, listOf(monday)).first()
        assertEquals(7, week.days.size)
        assertEquals(2, week.allTasks.size)
        assertEquals(1, week.progress.done)
        assertEquals(2, week.progress.total)
        assertEquals(0.5f, week.progress.percent, 0.001f)

        val months = TaskLogic.monthBuckets(tasks, 2026)
        assertEquals(12, months.size)
        assertEquals(2, months[10].allTasks.size) // November
        assertEquals(1, months[11].allTasks.size) // December
    }

    @Test
    fun `overdue and upcoming split on the clock`() {
        val now = System.currentTimeMillis()
        val tasks = listOf(
            task(1, "Late", monday, dueAt = now - 60_000),
            task(2, "Soon", monday, dueAt = now + 60_000),
            task(3, "Done late", monday, dueAt = now - 60_000, done = true),
        )
        assertEquals(listOf(1L), TaskLogic.overdue(tasks, now).map { it.id })
        assertEquals(listOf(2L), TaskLogic.upcoming(tasks, now).map { it.id })
    }

    @Test
    fun `a repeating task moves forward one period and keeps its time`() {
        val daily = task(1, "Daily", monday, dueAt = millisAt(monday, 9), repeat = Task.REPEAT_DAILY)
        val nextDaily = TaskLogic.nextOccurrence(daily)
        assertEquals(monday.plusDays(1), TaskLogic.dateOf(nextDaily!!))
        assertEquals("09:00", String.format("%02d:%02d",
            java.time.Instant.ofEpochMilli(nextDaily).atZone(TaskLogic.zone()).toLocalTime().hour,
            java.time.Instant.ofEpochMilli(nextDaily).atZone(TaskLogic.zone()).toLocalTime().minute))

        val weekly = task(2, "Weekly", monday, repeat = Task.REPEAT_WEEKLY)
        assertEquals(monday.plusWeeks(1), TaskLogic.dateOf(TaskLogic.nextOccurrence(weekly)!!))

        val monthly = task(3, "Monthly", monday, repeat = Task.REPEAT_MONTHLY)
        assertEquals(monday.plusMonths(1), TaskLogic.dateOf(TaskLogic.nextOccurrence(monthly)!!))

        assertNull(TaskLogic.nextOccurrence(task(4, "Once", monday)))
    }

    @Test
    fun `projects keep their own progress`() {
        val tasks = listOf(
            task(1, "A", monday, projectId = 10, done = true),
            task(2, "B", monday, projectId = 10),
            task(3, "C", monday, projectId = 11),
        )
        val project10 = TaskLogic.forProject(tasks, 10)
        assertEquals(2, project10.size)
        assertEquals(1, TaskLogic.progress(project10).done)
        assertEquals(1, TaskLogic.forProject(tasks, 11).size)
    }

    @Test
    fun `checklist items tick, add and remove without touching the task`() {
        val task = task(1, "Pack", monday).copy(
            checklist = listOf(ChecklistItem("Passport"), ChecklistItem("Charger", done = true)),
        )
        assertEquals(1, TaskLogic.checklistProgress(task).done)
        assertEquals(2, TaskLogic.checklistProgress(task).total)

        val ticked = TaskLogic.toggleChecklistItem(task, 0)
        assertTrue(ticked.checklist[0].done)
        assertTrue(TaskLogic.checklistComplete(ticked))
        assertEquals("Pack", ticked.title)

        val added = TaskLogic.addChecklistItem(task, "  Tickets  ")
        assertEquals(3, added.checklist.size)
        assertEquals("Tickets", added.checklist.last().text)
        assertEquals(2, TaskLogic.addChecklistItem(task, "   ").checklist.size) // blank ignored

        val removed = TaskLogic.removeChecklistItem(task, 1)
        assertEquals(listOf("Passport"), removed.checklist.map { it.text })
        assertEquals(task, TaskLogic.toggleChecklistItem(task, 9)) // out of range is a no-op
    }

    @Test
    fun `a checklist survives the json round trip and old records still load`() {
        val withSteps = task(1, "Pack", monday).copy(
            checklist = listOf(ChecklistItem("Passport", done = true), ChecklistItem("Charger")),
        )
        val json = TaskCodec.toJson(emptyList(), listOf(withSteps))
        val parsed = TaskCodec.tasksFromJson(JSONObject(json.toString()).optJSONArray("tasks"))
        assertEquals(withSteps, parsed.first())
        assertEquals(2, parsed.first().checklist.size)

        // A task written by 6.4.0 has no checklist key at all.
        val legacy = org.json.JSONArray().put(
            JSONObject().put("id", 9).put("title", "Old task").put("dueAt", millisAt(monday)),
        )
        val loaded = TaskCodec.tasksFromJson(legacy)
        assertEquals(1, loaded.size)
        assertTrue(loaded.first().checklist.isEmpty())
    }

    @Test
    fun `projects and tasks survive a json round trip`() {
        val projects = listOf(Project(id = 10, name = "Move house", color = 0xFF22C7A9.toInt(), dueAt = 1_800_000_000_000L))
        val tasks = listOf(
            task(1, "Pack kitchen", monday, projectId = 10, repeat = Task.REPEAT_WEEKLY, notes = "boxes"),
            task(2, "Book van", monday.plusDays(2), allDay = false, priority = 2, calendarEventId = 55L),
        )
        val json = TaskCodec.toJson(projects, tasks)
        val parsed = JSONObject(json.toString())

        assertEquals(projects, TaskCodec.projectsFromJson(parsed.optJSONArray("projects")))
        assertEquals(tasks, TaskCodec.tasksFromJson(parsed.optJSONArray("tasks")))
    }

    @Test
    fun `damaged records are skipped`() {
        val array = org.json.JSONArray()
            .put(JSONObject().put("id", 0).put("title", ""))
            .put(JSONObject().put("id", 5).put("title", "Real task").put("repeat", "fortnightly"))
            .put("junk")
        val tasks = TaskCodec.tasksFromJson(array)
        assertEquals(1, tasks.size)
        assertEquals(Task.REPEAT_NONE, tasks.first().repeat) // unknown repeat falls back
        assertTrue(tasks.first().dueAt >= 0L)
        assertFalse(tasks.first().done)
    }

    // ---- helpers -----------------------------------------------------------

    private fun millisAt(date: LocalDate, hour: Int = 9): Long =
        date.atTime(hour, 0).atZone(TaskLogic.zone()).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        title: String,
        date: LocalDate,
        done: Boolean = false,
        dueAt: Long = millisAt(date),
        projectId: Long? = null,
        repeat: String = Task.REPEAT_NONE,
        allDay: Boolean = true,
        priority: Int = 1,
        notes: String = "",
        calendarEventId: Long? = null,
    ): Task = Task(
        id = id,
        title = title,
        notes = notes,
        dueAt = dueAt,
        allDay = allDay,
        priority = priority,
        done = done,
        doneAt = if (done) dueAt else null,
        projectId = projectId,
        repeat = repeat,
        createdAt = 1_700_000_000_000L,
        calendarEventId = calendarEventId,
    )
}

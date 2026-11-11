package com.osintgram.chronos.tasks

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON the task store writes and reads back.
 *
 * Two things matter: a fully populated record must come back identical, and a
 * record damaged by a half-written file or by an older version must be skipped
 * rather than throwing, because this runs on launch.
 */
class TaskCodecTest {

    @Test
    fun `a fully populated record survives the round trip`() {
        val project = Project(
            id = 10,
            name = "Move house",
            color = 0xFF22C7A9.toInt(),
            dueAt = 1_800_000_000_000L,
            archived = true,
            createdAt = 1_799_000_000_000L,
        )
        val task = Task(
            id = 7,
            title = "Pack the kitchen",
            notes = "boxes and tape",
            dueAt = 1_800_000_600_000L,
            allDay = false,
            priority = 2,
            done = true,
            doneAt = 1_800_000_900_000L,
            projectId = 10,
            repeat = Task.REPEAT_WEEKLY,
            createdAt = 1_799_500_000_000L,
            calendarEventId = 55L,
            checklist = listOf(ChecklistItem("Plates", done = true), ChecklistItem("Glasses")),
        )

        // Read back through the text form, so the in-memory objects cannot leak through.
        val reparsed = JSONObject(TaskCodec.toJson(listOf(project), listOf(task)).toString())

        assertEquals(listOf(project), TaskCodec.projectsFromJson(reparsed.optJSONArray("projects")))
        assertEquals(listOf(task), TaskCodec.tasksFromJson(reparsed.optJSONArray("tasks")))
    }

    @Test
    fun `a record with only an id and a title falls back to the defaults`() {
        val array = JSONArray().put(JSONObject().put("id", 3).put("title", "Call mum"))
        val task = TaskCodec.tasksFromJson(array).single()

        assertEquals(3L, task.id)
        assertEquals("Call mum", task.title)
        assertEquals("", task.notes)
        assertEquals(0L, task.dueAt)
        assertTrue(task.allDay)
        assertEquals(1, task.priority)
        assertFalse(task.done)
        assertNull(task.doneAt)
        assertNull(task.projectId)
        assertEquals(Task.REPEAT_NONE, task.repeat)
        assertEquals(0L, task.createdAt)
        assertNull(task.calendarEventId)
        assertTrue(task.checklist.isEmpty())
    }

    @Test
    fun `a record with no keys at all is skipped`() {
        assertTrue(TaskCodec.tasksFromJson(JSONArray().put(JSONObject())).isEmpty())
        assertTrue(TaskCodec.projectsFromJson(JSONArray().put(JSONObject())).isEmpty())
    }

    @Test
    fun `nulls and non-objects inside the array are skipped`() {
        val array = JSONArray()
            .put(JSONObject.NULL)
            .put(JSONObject().put("id", 1).put("title", "Real task"))
            .put("junk")
            .put(JSONArray().put("nested"))
            .put(JSONObject().put("id", 2).put("title", "Another real task"))

        assertEquals(listOf(1L, 2L), TaskCodec.tasksFromJson(array).map { it.id })
    }

    @Test
    fun `a zero or negative id and a blank title are skipped`() {
        val array = JSONArray()
            .put(JSONObject().put("id", 0).put("title", "No id"))
            .put(JSONObject().put("id", -4).put("title", "Negative id"))
            .put(JSONObject().put("id", 5).put("title", "   "))
            .put(JSONObject().put("id", 6).put("title", "Kept"))

        assertEquals(listOf(6L), TaskCodec.tasksFromJson(array).map { it.id })
    }

    @Test
    fun `an out of range priority is clamped instead of rejecting the task`() {
        val array = JSONArray()
            .put(JSONObject().put("id", 1).put("title", "Low").put("priority", -3))
            .put(JSONObject().put("id", 2).put("title", "High").put("priority", 9))

        val tasks = TaskCodec.tasksFromJson(array)
        assertEquals(2, tasks.size)
        assertEquals(0, tasks[0].priority)
        assertEquals(2, tasks[1].priority)
    }

    @Test
    fun `an unknown repeat value falls back to none`() {
        val array = JSONArray()
            .put(JSONObject().put("id", 1).put("title", "Fortnightly").put("repeat", "fortnightly"))
            .put(JSONObject().put("id", 2).put("title", "Shouty").put("repeat", "DAILY"))
            .put(JSONObject().put("id", 3).put("title", "Known").put("repeat", Task.REPEAT_WEEKLY))

        val tasks = TaskCodec.tasksFromJson(array)
        assertEquals(Task.REPEAT_NONE, tasks[0].repeat)
        // The match is case-sensitive, so "DAILY" is not the same value as "daily".
        assertEquals(Task.REPEAT_NONE, tasks[1].repeat)
        assertEquals(Task.REPEAT_WEEKLY, tasks[2].repeat)
    }

    @Test
    fun `damaged checklist lines are skipped and the task is kept`() {
        val array = JSONArray().put(
            JSONObject().put("id", 1).put("title", "Trip").put(
                "checklist",
                JSONArray()
                    .put(JSONObject.NULL)
                    .put("junk")
                    .put(JSONObject().put("text", ""))
                    .put(JSONObject().put("text", "Passport").put("done", true)),
            ),
        )

        val task = TaskCodec.tasksFromJson(array).single()
        assertEquals(listOf(ChecklistItem("Passport", done = true)), task.checklist)
    }

    @Test
    fun `a missing array reads as no records`() {
        assertTrue(TaskCodec.tasksFromJson(null).isEmpty())
        assertTrue(TaskCodec.projectsFromJson(null).isEmpty())
    }
}

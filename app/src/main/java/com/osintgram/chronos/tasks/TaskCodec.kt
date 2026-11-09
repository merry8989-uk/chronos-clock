package com.osintgram.chronos.tasks

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON for projects and tasks, kept apart from the store so it holds no
 * Android dependency and can be round-trip tested. A record that cannot be
 * read is skipped rather than crashing the app on launch.
 */
object TaskCodec {

    private const val FORMAT_VERSION = 1

    fun projectsToJson(projects: List<Project>): JSONArray {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(
                JSONObject()
                    .put("id", project.id)
                    .put("name", project.name)
                    .put("color", project.color)
                    .put("dueAt", project.dueAt ?: JSONObject.NULL)
                    .put("archived", project.archived)
                    .put("createdAt", project.createdAt),
            )
        }
        return array
    }

    fun projectsFromJson(array: JSONArray?): List<Project> {
        if (array == null) return emptyList()
        val out = mutableListOf<Project>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val name = o.optString("name")
            if (id <= 0L || name.isBlank()) continue
            out.add(
                Project(
                    id = id,
                    name = name,
                    color = o.optInt("color", 0xFF4C8DFF.toInt()),
                    dueAt = if (o.isNull("dueAt")) null else o.optLong("dueAt", 0L),
                    archived = o.optBoolean("archived", false),
                    createdAt = o.optLong("createdAt", 0L),
                ),
            )
        }
        return out
    }

    fun tasksToJson(tasks: List<Task>): JSONArray {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("notes", task.notes)
                    .put("dueAt", task.dueAt)
                    .put("allDay", task.allDay)
                    .put("priority", task.priority)
                    .put("done", task.done)
                    .put("doneAt", task.doneAt ?: JSONObject.NULL)
                    .put("projectId", task.projectId ?: JSONObject.NULL)
                    .put("repeat", task.repeat)
                    .put("createdAt", task.createdAt)
                    .put("calendarEventId", task.calendarEventId ?: JSONObject.NULL)
                    .put(
                        "checklist",
                        JSONArray().also { array ->
                            task.checklist.forEach { item ->
                                array.put(
                                    JSONObject().put("text", item.text).put("done", item.done),
                                )
                            }
                        },
                    ),
            )
        }
        return array
    }

    fun tasksFromJson(array: JSONArray?): List<Task> {
        if (array == null) return emptyList()
        val out = mutableListOf<Task>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val title = o.optString("title")
            if (id <= 0L || title.isBlank()) continue
            val repeat = o.optString("repeat", Task.REPEAT_NONE)
            out.add(
                Task(
                    id = id,
                    title = title,
                    notes = o.optString("notes", ""),
                    dueAt = o.optLong("dueAt", 0L),
                    allDay = o.optBoolean("allDay", true),
                    priority = o.optInt("priority", 1).coerceIn(0, 2),
                    done = o.optBoolean("done", false),
                    doneAt = if (o.isNull("doneAt")) null else o.optLong("doneAt", 0L),
                    projectId = if (o.isNull("projectId")) null else o.optLong("projectId", 0L),
                    repeat = if (repeat in Task.REPEATS) repeat else Task.REPEAT_NONE,
                    createdAt = o.optLong("createdAt", 0L),
                    calendarEventId = if (o.isNull("calendarEventId")) null else o.optLong("calendarEventId", 0L),
                    checklist = readChecklist(o.optJSONArray("checklist")),
                ),
            )
        }
        return out
    }

    private fun readChecklist(array: JSONArray?): List<ChecklistItem> {
        if (array == null) return emptyList()
        val out = mutableListOf<ChecklistItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val text = o.optString("text")
            if (text.isBlank()) continue
            out.add(ChecklistItem(text = text, done = o.optBoolean("done", false)))
        }
        return out
    }

    /** One object holding everything, used by the backup file. */
    fun toJson(projects: List<Project>, tasks: List<Task>): JSONObject = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("projects", projectsToJson(projects))
        .put("tasks", tasksToJson(tasks))
}

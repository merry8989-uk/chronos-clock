package com.osintgram.chronos.tasks

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Projects and tasks, persisted as JSON in SharedPreferences and exposed as
 * observable lists — the same pattern as AlarmStore and WellnessStore, so every
 * view redraws the moment a task changes and nothing leaves the device.
 */
object TaskStore {

    private const val PREFS = "chronos_tasks"
    private const val KEY_TASKS = "tasks"
    private const val KEY_PROJECTS = "projects"

    private var prefs: SharedPreferences? = null

    private val tasks: SnapshotStateList<Task> = mutableStateListOf()
    private val projects: SnapshotStateList<Project> = mutableStateListOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        tasks.clear()
        projects.clear()
        tasks.addAll(TaskCodec.tasksFromJson(readArray(KEY_TASKS)))
        projects.addAll(TaskCodec.projectsFromJson(readArray(KEY_PROJECTS)))
        DiagnosticsLogger.log(
            "TaskStore",
            "Loaded ${tasks.size} task(s) and ${projects.size} project(s)",
        )
    }

    // ---- Reads -------------------------------------------------------------

    fun allTasks(): List<Task> = tasks.toList()

    fun allProjects(): List<Project> = projects.toList()

    fun task(id: Long): Task? = tasks.firstOrNull { it.id == id }

    fun project(id: Long?): Project? = id?.let { pid -> projects.firstOrNull { it.id == pid } }

    fun openTasks(): List<Task> = tasks.filter { !it.done }

    fun tasksOn(date: LocalDate): List<Task> = TaskLogic.tasksOn(tasks.toList(), date)

    fun tasksBetween(from: LocalDate, to: LocalDate): List<Task> =
        TaskLogic.tasksBetween(tasks.toList(), from, to)

    fun projectProgress(projectId: Long): Progress =
        TaskLogic.progress(TaskLogic.forProject(tasks.toList(), projectId))

    // ---- Writes ------------------------------------------------------------

    fun addTask(
        title: String,
        dueAt: Long,
        allDay: Boolean = true,
        priority: Int = 1,
        notes: String = "",
        projectId: Long? = null,
        repeat: String = Task.REPEAT_NONE,
    ): Task {
        val task = Task(
            id = nextId(),
            title = title.trim(),
            notes = notes,
            dueAt = dueAt,
            allDay = allDay,
            priority = priority.coerceIn(0, 2),
            projectId = projectId,
            repeat = if (repeat in Task.REPEATS) repeat else Task.REPEAT_NONE,
            createdAt = System.currentTimeMillis(),
        )
        tasks.add(task)
        persistTasks()
        DiagnosticsLogger.log("TaskStore", "Added task ${task.id}")
        return task
    }

    fun updateTask(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index < 0) return
        tasks[index] = task
        persistTasks()
    }

    fun setDone(taskId: Long, done: Boolean) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return
        val task = tasks[index]
        if (task.done == done) return
        tasks[index] = task.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)

        // A repeating task creates its next occurrence when it is completed.
        if (done && task.repeat != Task.REPEAT_NONE) {
            val next = TaskLogic.nextOccurrence(task)
            if (next != null) {
                tasks.add(
                    task.copy(
                        id = nextId(),
                        dueAt = next,
                        done = false,
                        doneAt = null,
                        calendarEventId = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        persistTasks()
    }

    fun deleteTask(taskId: Long) {
        tasks.removeAll { it.id == taskId }
        persistTasks()
    }

    /** Moves a task to another day, keeping its time of day. */
    fun reschedule(taskId: Long, date: LocalDate) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return
        val task = tasks[index]
        val time = java.time.Instant.ofEpochMilli(task.dueAt)
            .atZone(TaskLogic.zone())
            .toLocalTime()
        tasks[index] = task.copy(
            dueAt = date.atTime(time).atZone(TaskLogic.zone()).toInstant().toEpochMilli(),
        )
        persistTasks()
    }

    fun attachCalendarEvent(taskId: Long, eventId: Long?) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return
        tasks[index] = tasks[index].copy(calendarEventId = eventId)
        persistTasks()
    }

    // ---- Projects ----------------------------------------------------------

    fun addProject(name: String, color: Int, dueAt: Long? = null): Project {
        val project = Project(
            id = nextId(),
            name = name.trim(),
            color = color,
            dueAt = dueAt,
            createdAt = System.currentTimeMillis(),
        )
        projects.add(project)
        persistProjects()
        return project
    }

    fun updateProject(project: Project) {
        val index = projects.indexOfFirst { it.id == project.id }
        if (index < 0) return
        projects[index] = project
        persistProjects()
    }

    /** Deleting a project keeps its tasks and just unlinks them. */
    fun deleteProject(projectId: Long) {
        projects.removeAll { it.id == projectId }
        persistProjects()
        val affected = tasks.mapIndexedNotNull { index, task ->
            if (task.projectId == projectId) index else null
        }
        affected.forEach { index ->
            tasks[index] = tasks[index].copy(projectId = null)
        }
        if (affected.isNotEmpty()) persistTasks()
    }

    // ---- Backup ------------------------------------------------------------

    fun asJson(): JSONObject = TaskCodec.toJson(projects.toList(), tasks.toList())

    fun importJson(json: JSONObject?): String? {
        if (json == null) return null
        val restoredProjects = TaskCodec.projectsFromJson(json.optJSONArray("projects"))
        val restoredTasks = TaskCodec.tasksFromJson(json.optJSONArray("tasks"))
        projects.clear()
        projects.addAll(restoredProjects)
        tasks.clear()
        tasks.addAll(restoredTasks)
        persistProjects()
        persistTasks()
        return "${restoredTasks.size} task(s), ${restoredProjects.size} project(s)"
    }

    fun clearAll() {
        tasks.clear()
        projects.clear()
        prefs?.edit()?.clear()?.apply()
    }

    // ---- Internals ---------------------------------------------------------

    private fun nextId(): Long = System.currentTimeMillis() + (0..999).random()

    private fun readArray(key: String): JSONArray? =
        runCatching { prefs?.getString(key, null)?.let { JSONArray(it) } }.getOrNull()

    private fun persistTasks() =
        prefs?.edit()?.putString(KEY_TASKS, TaskCodec.tasksToJson(tasks.toList()).toString())?.apply()

    private fun persistProjects() =
        prefs?.edit()?.putString(KEY_PROJECTS, TaskCodec.projectsToJson(projects.toList()).toString())?.apply()
}

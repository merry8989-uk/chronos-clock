package com.osintgram.chronos.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.tasks.Project
import com.osintgram.chronos.tasks.Task
import com.osintgram.chronos.tasks.TaskLogic
import com.osintgram.chronos.tasks.TaskReminders
import com.osintgram.chronos.tasks.TaskStore
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.pressable
import java.time.LocalDate

/** One task line: tick, title, project and due time, plus calendar and delete. */
@Composable
fun TaskRow(
    task: Task,
    showDate: Boolean = false,
    onAddToCalendar: ((Task) -> Unit)? = null,
) {
    val context = LocalContext.current
    val project = TaskStore.project(task.projectId)
    var showChecklist by remember { mutableStateOf(false) }
    val checklistProgress = TaskLogic.checklistProgress(task)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    Haptics.tick(context)
                    TaskStore.setDone(task.id, !task.done)
                    val updated = TaskStore.task(task.id)
                    if (updated != null) TaskReminders.schedule(context, updated)
                },
            ) {
                Icon(
                    if (task.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (task.done) "Mark not done" else "Mark done",
                    tint = if (task.done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable { showChecklist = true },
            ) {
                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                val bits = buildList {
                    if (showDate) add(java.time.format.DateTimeFormatter.ofPattern("d MMM")
                        .format(TaskLogic.dateOf(task.dueAt)))
                    if (!task.allDay) add(timeOf(task))
                    if (task.repeat != Task.REPEAT_NONE) add(task.repeat)
                    project?.let { add(it.name) }
                    if (task.calendarEventId != null) add("in calendar")
                    if (!checklistProgress.isEmpty) {
                        add("${checklistProgress.done}/${checklistProgress.total} steps")
                    }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        text = bits.joinToString(" · "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (task.priority == 2) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            if (onAddToCalendar != null && task.calendarEventId == null) {
                IconButton(onClick = { onAddToCalendar(task) }) {
                    Icon(
                        Icons.Filled.Event,
                        contentDescription = "Add to phone calendar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = {
                TaskReminders.cancel(context, task.id)
                TaskStore.deleteTask(task.id)
            }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showChecklist) {
        ChecklistDialog(taskId = task.id, onDismiss = { showChecklist = false })
    }
}

/**
 * The checklist inside one task: tick a step, add one, delete one. The task is
 * re-read from the store on every change, so a tick shows immediately.
 */
@Composable
fun ChecklistDialog(taskId: Long, onDismiss: () -> Unit) {
    val task = TaskStore.task(taskId) ?: return
    var text by remember { mutableStateOf("") }
    val progress = TaskLogic.checklistProgress(task)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = task.title,
                fontSize = 16.sp,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (task.checklist.isEmpty()) {
                    Text(
                        text = "No steps yet — add the first one below.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                task.checklist.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { TaskStore.toggleChecklistItem(taskId, index) }) {
                            Icon(
                                if (item.done) Icons.Filled.CheckCircle
                                else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = if (item.done) "Untick ${item.text}" else "Tick ${item.text}",
                                tint = if (item.done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = item.text,
                            fontSize = 14.sp,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (item.done) {
                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { TaskStore.removeChecklistItem(taskId, index) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove ${item.text}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(120) },
                        label = { Text("Add a step") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            TaskStore.addChecklistItem(taskId, text)
                            text = ""
                        },
                    ) { Text("Add") }
                }
                if (!progress.isEmpty) {
                    Text(
                        text = "${progress.done} of ${progress.total} steps done",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

internal fun timeOf(task: Task): String {
    val time = java.time.Instant.ofEpochMilli(task.dueAt).atZone(TaskLogic.zone()).toLocalTime()
    return String.format("%02d:%02d", time.hour, time.minute)
}

/** A group heading with its own done/total line. */
@Composable
internal fun GroupHeader(
    title: String,
    subtitle: String,
    progress: Float,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(5.dp))
        ProgressLine(progress)
    }
}

/** Add / edit a task. Everything the store understands is on this sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var priority by remember { mutableStateOf(1) }
    var repeat by remember { mutableStateOf(Task.REPEAT_NONE) }
    var projectId by remember { mutableStateOf<Long?>(null) }
    var timeMinutes by remember { mutableLongStateOf(-1L) } // -1 = all day
    var showPicker by remember { mutableStateOf(false) }
    var checklistDraft by remember { mutableStateOf(listOf<String>()) }
    var checklistText by remember { mutableStateOf("") }
    val projects = TaskStore.allProjects()

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = TaskLogic.startOfDay(date),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("What needs doing?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Due " + java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy").format(date),
                        fontSize = 13.sp,
                    )
                }
                Text("Time", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1L to "All day", 9L to "09:00", 12L to "12:00", 18L to "18:00")
                        .forEach { (value, label) ->
                            Pill(
                                text = label,
                                selected = timeMinutes == value,
                                onClick = { timeMinutes = value },
                            )
                        }
                }
                Text("Priority", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Low", 1 to "Normal", 2 to "High").forEach { (value, label) ->
                        Pill(
                            text = label,
                            selected = priority == value,
                            onClick = { priority = value },
                        )
                    }
                }
                Text("Repeat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Task.REPEATS.forEach { option ->
                        Pill(
                            text = option,
                            selected = repeat == option,
                            onClick = { repeat = option },
                        )
                    }
                }
                if (projects.isNotEmpty()) {
                    Text("Project", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(
                            text = "None",
                            selected = projectId == null,
                            onClick = { projectId = null },
                        )
                        projects.take(3).forEach { project ->
                            Pill(
                                text = project.name,
                                selected = projectId == project.id,
                                onClick = { projectId = project.id },
                            )
                        }
                    }
                }
                Text(
                    "Checklist (optional)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                checklistDraft.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "• $item",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            checklistDraft = checklistDraft.filterIndexed { i, _ -> i != index }
                        }) { Text("Remove", fontSize = 11.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = checklistText,
                        onValueChange = { checklistText = it.take(120) },
                        label = { Text("Add a step") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        enabled = checklistText.isNotBlank(),
                        onClick = {
                            checklistDraft = checklistDraft + checklistText.trim()
                            checklistText = ""
                        },
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = {
            val context = LocalContext.current
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val dueAt = if (timeMinutes < 0) {
                        TaskLogic.startOfDay(date)
                    } else {
                        date.atTime(timeMinutes.toInt(), 0)
                            .atZone(TaskLogic.zone()).toInstant().toEpochMilli()
                    }
                    val task = TaskStore.addTask(
                        title = title,
                        dueAt = dueAt,
                        allDay = timeMinutes < 0,
                        priority = priority,
                        notes = notes,
                        projectId = projectId,
                        repeat = repeat,
                    )
                    checklistDraft.forEach { step -> TaskStore.addChecklistItem(task.id, step) }
                    TaskReminders.schedule(context, task)
                    Haptics.success(context)
                    onDismiss()
                },
            ) { Text("Add task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Create a project. */
@Composable
fun ProjectEditorDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val colors = listOf(
        0xFF4C8DFF.toInt(),
        0xFF22C7A9.toInt(),
        0xFFFFB020.toInt(),
        0xFFFF6E8A.toInt(),
        0xFF8B7CFF.toInt(),
    )
    var color by remember { mutableStateOf(colors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { value ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(androidx.compose.ui.graphics.Color(value), CircleShape)
                                .pressable { color = value },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == value) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selected colour",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    "Tap a colour to pick it",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    TaskStore.addProject(name, color)
                    onDismiss()
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Colour swatch helper used by the project cards. */
@Composable
internal fun ColorDot(color: Int, size: Int = 10) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(androidx.compose.ui.graphics.Color(color), CircleShape),
    )
}

/** A project card: name, progress, and the count of open tasks. */
@Composable
internal fun ProjectCard(project: Project, onClick: () -> Unit) {
    val progress = TaskStore.projectProgress(project.id)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(project.color)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = project.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${progress.done}/${progress.total}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressLine(progress.percent)
        }
    }
}

/** The small "add" button used at the top of each task view. */
@Composable
internal fun AddButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp)
    }
}

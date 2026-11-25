package com.osintgram.chronos.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.tasks.Project
import com.osintgram.chronos.tasks.Task
import com.osintgram.chronos.tasks.TaskLogic
import com.osintgram.chronos.tasks.TaskReminders
import com.osintgram.chronos.tasks.TaskStore
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.ui.theme.pressable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---- Local sizes -----------------------------------------------------------
// Everything that is padding, a radius or a type size comes from Spacing, Radii
// and ChronosType. These three are fixed sizes those scales do not cover: an
// icon, the dot that marks a high-priority task, and a colour swatch big enough
// to be a tap target. ColorDot keeps its own diameter as a default argument, so
// its signature is unchanged for the calendar rows that call it.

/** The small glyphs inside a row and inside a button. */
private val IconSize = 18.dp

/** The tick and the remove glyph inside a checklist step, a size apart. */
private val ChecklistTickIconSize = 20.dp
private val ChecklistRemoveIconSize = 16.dp

/** The dot beside a high-priority task. */
private val PriorityDotSize = 8.dp

/** A tappable colour swatch in the project editor. */
private val SwatchSize = 30.dp

/**
 * The project palette.
 *
 * A project's colour is data, not theme: it is chosen once, stored with the
 * project and shown back on its card, so it must not be repainted when the user
 * changes preset. The five ARGB values therefore live here, once, as a named
 * list — and each carries the name its dot is announced with, because a dot
 * alone tells a person who cannot separate two blues nothing at all.
 */
private data class ProjectColour(val argb: Int, val name: String)

private val ProjectPalette: List<ProjectColour> = listOf(
    ProjectColour(0xFF4C8DFF.toInt(), "Blue"),
    ProjectColour(0xFF22C7A9.toInt(), "Teal"),
    ProjectColour(0xFFFFB020.toInt(), "Amber"),
    ProjectColour(0xFFFF6E8A.toInt(), "Rose"),
    ProjectColour(0xFF8B7CFF.toInt(), "Violet"),
)

/** The palette name for a stored colour, so any dot can be described out loud. */
private fun colourName(argb: Int): String =
    ProjectPalette.firstOrNull { it.argb == argb }?.name ?: "Custom colour"

/** One task line: tick, title, project and due time, plus calendar and delete. */
@Composable
fun TaskRow(
    task: Task,
    showDate: Boolean = false,
    onAddToCalendar: ((Task) -> Unit)? = null,
) {
    TaskLine(task = task, showDate = showDate, onAddToCalendar = onAddToCalendar)
}

/**
 * Priority as a word, not a colour, the way the restyled task list reads it:
 * high on the warning colour, low quietly, and normal carries nothing so the
 * list stays calm for the tasks that have nothing to say.
 */
@Composable
private fun PriorityTag(text: String, high: Boolean) {
    Surface(
        color = if (high) StatusColors.warning else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radii.sm),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = ChronosType.label,
                fontWeight = FontWeight.SemiBold,
                color = if (high) StatusColors.onWarning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The body of a task line. It follows the task list's scan row: a title on the
 * body step, one metadata line on the label step, the card on [Radii.md] and
 * the row's padding on the spacing scale.
 */
@Composable
private fun TaskLine(
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
        shape = RoundedCornerShape(Radii.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.sm,
                end = Spacing.xs,
                top = Spacing.xs,
                bottom = Spacing.xs,
            ),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = ChronosType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (task.priority == 2) {
                        Spacer(Modifier.width(Spacing.sm))
                        Box(
                            Modifier
                                .size(PriorityDotSize)
                                .background(MaterialTheme.colorScheme.error, CircleShape),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        PriorityTag(text = "High", high = true)
                    }
                }
                val bits = buildList {
                    if (showDate) {
                        add(DateTimeFormatter.ofPattern("d MMM").format(TaskLogic.dateOf(task.dueAt)))
                    }
                    project?.let { add(it.name) }
                    if (!task.allDay) add(timeOf(task))
                    if (task.repeat != Task.REPEAT_NONE) add(task.repeat)
                    if (task.calendarEventId != null) add("in calendar")
                    if (!checklistProgress.isEmpty) {
                        add("${checklistProgress.done}/${checklistProgress.total} steps")
                    }
                }
                if (bits.isNotEmpty()) {
                    // No weight here: this Text sits in a Column that wraps its
                    // content, and a weighted child in an unbounded column
                    // collapses to nothing. Ellipsis does the clipping instead.
                    Text(
                        text = bits.joinToString(" · "),
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onAddToCalendar != null && task.calendarEventId == null) {
                IconButton(onClick = { onAddToCalendar(task) }) {
                    Icon(
                        Icons.Filled.Event,
                        contentDescription = "Add to phone calendar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize),
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
                    modifier = Modifier.size(IconSize),
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
 *
 * Each step is a row on [Radii.md], the add field is the dialog's action, and
 * an empty checklist says what to do next rather than showing nothing.
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
                style = ChronosType.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (task.checklist.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(
                                text = "No steps yet",
                                style = ChronosType.body,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "Break this task down below: every step you add is a tick " +
                                    "you can make in one go.",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                task.checklist.forEachIndexed { index, item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { TaskStore.toggleChecklistItem(taskId, index) }) {
                                Icon(
                                    if (item.done) Icons.Filled.CheckCircle
                                    else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = if (item.done) "Untick ${item.text}" else "Tick ${item.text}",
                                    tint = if (item.done) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ChecklistTickIconSize),
                                )
                            }
                            Text(
                                text = item.text,
                                style = ChronosType.body,
                                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (item.done) {
                                    TextDecoration.LineThrough
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
                                    modifier = Modifier.size(ChecklistRemoveIconSize),
                                )
                            }
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
                    Spacer(Modifier.width(Spacing.sm))
                    Button(
                        enabled = text.isNotBlank(),
                        onClick = {
                            TaskStore.addChecklistItem(taskId, text)
                            text = ""
                        },
                        shape = RoundedCornerShape(Radii.md),
                    ) {
                        Text("Add", style = ChronosType.label, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!progress.isEmpty) {
                    Text(
                        text = "${progress.done} of ${progress.total} steps done",
                        style = ChronosType.label,
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
                style = ChronosType.body,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = subtitle,
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        ProgressLine(progress)
    }
}

/**
 * An all-caps section heading inside a form, on the same label step the task
 * list uses for its group headings, so the two read as one family.
 */
@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = ChronosType.label,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.xs),
    )
}

/** The quiet label above a row of pills, one step below a section heading. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = ChronosType.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs),
    )
}

/**
 * Add / edit a task. Everything the store understands is on this sheet, grouped
 * into sections so the form reads as three short questions rather than one long
 * column: what it is, when it is due, and what it is tagged with.
 *
 * Save is the only filled button here; Cancel and the checklist's "Add" stay
 * quiet so the primary action is unambiguous.
 */
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FormSectionLabel("The task")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("What needs doing?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (title.isBlank()) {
                    Text(
                        text = "A title is needed before this can be saved.",
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                FormSectionLabel("When")
                OutlinedButton(
                    onClick = { showPicker = true },
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Due " + DateTimeFormatter.ofPattern("d MMM yyyy").format(date),
                        style = ChronosType.body,
                    )
                }
                FieldLabel("Time")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(-1L to "All day", 9L to "09:00", 12L to "12:00", 18L to "18:00")
                        .forEach { (value, label) ->
                            Pill(
                                text = label,
                                selected = timeMinutes == value,
                                onClick = { timeMinutes = value },
                            )
                        }
                }

                FormSectionLabel("Details")
                FieldLabel("Priority")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(0 to "Low", 1 to "Normal", 2 to "High").forEach { (value, label) ->
                        Pill(
                            text = label,
                            selected = priority == value,
                            onClick = { priority = value },
                        )
                    }
                }
                FieldLabel("Repeat")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Task.REPEATS.forEach { option ->
                        Pill(
                            text = option,
                            selected = repeat == option,
                            onClick = { repeat = option },
                        )
                    }
                }
                if (projects.isNotEmpty()) {
                    FieldLabel("Project")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

                FormSectionLabel("Checklist")
                if (checklistDraft.isEmpty()) {
                    Text(
                        text = "Optional: steps you can tick off one at a time.",
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                checklistDraft.forEachIndexed { index, item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item,
                                style = ChronosType.body,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                checklistDraft = checklistDraft.filterIndexed { i, _ -> i != index }
                            }) { Text("Remove", style = ChronosType.label) }
                        }
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
                    Spacer(Modifier.width(Spacing.sm))
                    TextButton(
                        enabled = checklistText.isNotBlank(),
                        onClick = {
                            checklistDraft = checklistDraft + checklistText.trim()
                            checklistText = ""
                        },
                    ) { Text("Add", style = ChronosType.label) }
                }
            }
        },
        confirmButton = {
            val context = LocalContext.current
            Button(
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
                shape = RoundedCornerShape(Radii.md),
            ) { Text("Save", style = ChronosType.body, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Create a project. */
@Composable
fun ProjectEditorDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val colors = ProjectPalette
    var color by remember { mutableStateOf(colors.first().argb) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    colors.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(SwatchSize)
                                .background(Color(swatch.argb), CircleShape)
                                .semantics { contentDescription = swatch.name }
                                .pressable { color = swatch.argb },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == swatch.argb) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selected colour",
                                    tint = Color.White,
                                    modifier = Modifier.size(IconSize),
                                )
                            }
                        }
                    }
                }
                Text(
                    "Tap a colour to pick it",
                    style = ChronosType.label,
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

/**
 * Colour swatch helper used by the project cards and the calendar rows. A
 * project's colour is data, so it arrives as a stored ARGB value; the dot names
 * it for anyone who cannot tell two dots apart by sight.
 */
@Composable
internal fun ColorDot(color: Int, size: Int = 10) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(Color(color), CircleShape)
            .semantics { contentDescription = colourName(color) },
    )
}

/** A project card: name, progress, and the count of open tasks. */
@Composable
internal fun ProjectCard(project: Project, onClick: () -> Unit) {
    val progress = TaskStore.projectProgress(project.id)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        onClick = onClick,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(project.color)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = project.name,
                    style = ChronosType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${progress.done}/${progress.total}",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            ProgressLine(progress.percent)
        }
    }
}

/** The small "add" button used at the top of each task view. */
@Composable
internal fun AddButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.md),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(IconSize))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = ChronosType.body, fontWeight = FontWeight.SemiBold)
    }
}

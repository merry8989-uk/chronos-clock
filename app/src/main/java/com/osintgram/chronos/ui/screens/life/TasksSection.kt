package com.osintgram.chronos.ui.screens.life

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.tasks.CalendarEvent
import com.osintgram.chronos.tasks.CalendarSource
import com.osintgram.chronos.tasks.Progress
import com.osintgram.chronos.tasks.Task
import com.osintgram.chronos.tasks.TaskLogic
import com.osintgram.chronos.tasks.TaskReminders
import com.osintgram.chronos.tasks.TaskStore
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.ui.theme.pressable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The six task views: four time levels, projects, and the calendar. */
enum class TasksView(val label: String) {
    Today("Today"),
    Week("Week"),
    Month("Month"),
    Year("Year"),
    Projects("Projects"),
    Calendar("Calendar"),
}

// ---- Local sizes -----------------------------------------------------------
// Everything that is padding, a radius or a type size comes from Spacing, Radii
// and ChronosType. These three are fixed sizes those scales do not cover: an
// icon, a calendar cell, and the block an empty state is given so it reads as
// a block rather than a stray line.

private val IconSize = 18.dp
private val DayCellHeight = 44.dp
private val EmptyBlockHeight = 180.dp
private val DotSize = Spacing.xs
private val GridGap = 2.dp

/**
 * Tasks: a daily task lives in its week, a week in its month, a month in its
 * year — the four views read the same task list at four zoom levels — and a
 * task can belong to a project instead of (or as well as) a date.
 */
@Composable
fun TasksSection() {
    var view by rememberSaveable { mutableStateOf(TasksView.Today.name) }
    val current = TasksView.entries.firstOrNull { it.name == view } ?: TasksView.Today
    var showEditor by remember { mutableStateOf(false) }
    var showProjectEditor by remember { mutableStateOf(false) }
    var editorDate by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    val tasks = TaskStore.allTasks()

    Column(modifier = Modifier.fillMaxWidth()) {
        // The view switcher is a quiet selector: the only filled button on any
        // of these screens is the one that adds something.
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(TasksView.entries, key = { it.name }) { entry ->
                Pill(
                    text = entry.label,
                    selected = entry == current,
                    onClick = {
                        Haptics.tick(context)
                        view = entry.name
                    },
                )
            }
        }

        when (current) {
            TasksView.Today -> TodayView(
                tasks = tasks,
                onAdd = { editorDate = LocalDate.now(); showEditor = true },
            )
            TasksView.Week -> WeekView(
                tasks = tasks,
                onAdd = { date -> editorDate = date; showEditor = true },
            )
            TasksView.Month -> MonthView(tasks = tasks)
            TasksView.Year -> YearView(tasks = tasks)
            TasksView.Projects -> ProjectsView(
                onAddProject = { showProjectEditor = true },
                onAddTask = { showEditor = true },
            )
            TasksView.Calendar -> CalendarView(tasks = tasks)
        }
    }

    if (showEditor) {
        TaskEditorDialog(initialDate = editorDate, onDismiss = { showEditor = false })
    }
    if (showProjectEditor) {
        ProjectEditorDialog(onDismiss = { showProjectEditor = false })
    }
}

// ---- Shared pieces ---------------------------------------------------------

/** The one filled button in a view: adding a task, or the first project. */
@Composable
private fun PrimaryAddButton(label: String, onClick: () -> Unit) {
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

/** A small all-caps group heading, kept to one or two words. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = ChronosType.label,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.xs),
    )
}

/**
 * Priority as a word, not a colour: high reads "High" on the warning colour,
 * low reads "Low" quietly. Normal priority carries no pill, so the list stays
 * quiet for the tasks that have nothing to say.
 */
@Composable
private fun PriorityPill(text: String, high: Boolean) {
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

/** An empty state with room to breathe, so an empty view reads as deliberate. */
@Composable
private fun TaskEmptyState(title: String, subtitle: String) {
    EmptyState(title = title, subtitle = subtitle, modifier = Modifier.height(EmptyBlockHeight))
}

/**
 * One task line: tick, title, then a metadata line of project, time and
 * repeat, with priority and overdue carried as words so neither is signalled
 * by colour alone.
 */
@Composable
private fun TaskScanRow(
    task: Task,
    showDate: Boolean = false,
    onAddToCalendar: ((Task) -> Unit)? = null,
) {
    val context = LocalContext.current
    val project = TaskStore.project(task.projectId)
    var showChecklist by remember { mutableStateOf(false) }
    val checklistProgress = TaskLogic.checklistProgress(task)
    // Reuses the store's own overdue rule rather than re-deriving the date.
    val overdue = !task.done && TaskLogic.overdue(listOf(task)).isNotEmpty()

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
                    if (task.priority == 2 || task.priority == 0) {
                        Spacer(Modifier.width(Spacing.sm))
                        PriorityPill(
                            text = if (task.priority == 2) "High" else "Low",
                            high = task.priority == 2,
                        )
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
                if (overdue || bits.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (overdue) {
                            Text(
                                text = "Overdue",
                                style = ChronosType.label,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusColors.warning,
                            )
                            if (bits.isNotEmpty()) Spacer(Modifier.width(Spacing.xs))
                        }
                        if (bits.isNotEmpty()) {
                            Text(
                                text = bits.joinToString(" · "),
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
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

// ---- Today -----------------------------------------------------------------

@Composable
private fun TodayView(tasks: List<Task>, onAdd: () -> Unit) {
    val today = LocalDate.now()
    val todays = TaskLogic.tasksOn(tasks, today)
    val overdue = TaskLogic.overdue(tasks)
    val progress = TaskLogic.progress(todays)

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        text = "Today",
                        style = ChronosType.headline,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = DateTimeFormatter.ofPattern("EEE d MMM").format(today),
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = if (progress.isEmpty) "Nothing due today"
                        else "${progress.done} of ${progress.total} done",
                        style = ChronosType.numeric,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    ProgressLine(progress.percent)
                    if (overdue.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = "${overdue.size} overdue from earlier",
                            style = ChronosType.label,
                            color = StatusColors.warning,
                        )
                    }
                }
            }
        }
        item { PrimaryAddButton("Add a task for today", onAdd) }

        if (overdue.isNotEmpty()) {
            item { GroupLabel("Overdue") }
            items(overdue, key = { "overdue:${it.id}" }) { task -> TaskScanRow(task, showDate = true) }
        }

        item { GroupLabel("Today") }
        if (todays.isEmpty()) {
            item {
                TaskEmptyState(
                    title = "Clear day",
                    subtitle = "Add a task above, or plan the week from the Week tab.",
                )
            }
        } else {
            items(todays, key = { "today:${it.id}" }) { task -> TaskScanRow(task) }
        }
    }
}

// ---- Week ------------------------------------------------------------------

@Composable
private fun WeekView(tasks: List<Task>, onAdd: (LocalDate) -> Unit) {
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    val days = TaskLogic.weekDays(anchor)
    val buckets = TaskLogic.dayBuckets(tasks, days)
    val weekProgress = TaskLogic.progress(TaskLogic.tasksBetween(tasks, days.first(), days.last()))

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            PeriodHeader(
                title = TaskLogic.weekLabel(anchor),
                subtitle = "${weekProgress.done} of ${weekProgress.total} done this week",
                progress = weekProgress,
                onPrevious = { anchor = anchor.minusWeeks(1) },
                onNext = { anchor = anchor.plusWeeks(1) },
                onToday = { anchor = LocalDate.now() },
            )
        }
        item { PrimaryAddButton("Add a task", onClick = { onAdd(LocalDate.now()) }) }
        if (weekProgress.total == 0) {
            item {
                TaskEmptyState(
                    title = "Nothing this week",
                    subtitle = "Add a task above, or move to Month to plan further ahead.",
                )
            }
        } else {
            items(buckets, key = { it.date.toString() }) { bucket ->
                val isToday = bucket.date == LocalDate.now()
                Surface(
                    color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = DateTimeFormatter.ofPattern("EEE d").format(bucket.date),
                                style = ChronosType.label,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${bucket.progress.done}/${bucket.progress.total}",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { onAdd(bucket.date) }) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add a task on this day",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconSize),
                                )
                            }
                        }
                        bucket.tasks.forEach { task ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                            ) {
                                Box(
                                    Modifier
                                        .size(DotSize)
                                        .background(
                                            if (task.done) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            CircleShape,
                                        ),
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = task.title,
                                    style = ChronosType.body,
                                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!task.allDay) {
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        text = timeOf(task),
                                        style = ChronosType.label,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (bucket.tasks.isEmpty()) {
                            Text(
                                text = "Nothing planned",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Month -----------------------------------------------------------------

@Composable
private fun MonthView(tasks: List<Task>) {
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    val month = YearMonth.from(anchor)
    val weeks = TaskLogic.weekBuckets(tasks, TaskLogic.weeksOfMonth(month))
    // Only this month's tasks count, even though the weeks are shown whole.
    val monthTasks = TaskLogic.tasksBetween(tasks, month.atDay(1), month.atEndOfMonth())
    val progress = TaskLogic.progress(monthTasks)

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            PeriodHeader(
                title = DateTimeFormatter.ofPattern("MMMM yyyy").format(month),
                subtitle = "${progress.done} of ${progress.total} done · ${monthTasks.size} tasks",
                progress = progress,
                onPrevious = { anchor = anchor.minusMonths(1) },
                onNext = { anchor = anchor.plusMonths(1) },
                onToday = { anchor = LocalDate.now() },
            )
        }
        if (monthTasks.isEmpty()) {
            item {
                TaskEmptyState(
                    title = "Nothing this month",
                    subtitle = "Add a task from the Today tab and it will roll up here.",
                )
            }
        } else {
            items(weeks, key = { it.start.toString() }) { week ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ("Week of " +
                                    DateTimeFormatter.ofPattern("d MMM").format(week.start)).uppercase(),
                                style = ChronosType.label,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${week.progress.done}/${week.progress.total}",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        ProgressLine(week.progress.percent)
                        week.days.filter { it.tasks.isNotEmpty() }.forEach { day ->
                            Text(
                                text = "${DateTimeFormatter.ofPattern("EEE d").format(day.date)} — " +
                                    day.tasks.joinToString(", ") { it.title },
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                        if (week.progress.total == 0) {
                            Text(
                                text = "No tasks this week",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Year ------------------------------------------------------------------

@Composable
private fun YearView(tasks: List<Task>) {
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    val buckets = TaskLogic.monthBuckets(tasks, anchor.year)
    val yearTasks = buckets.flatMap { it.allTasks }.distinctBy { it.id }
    val progress = TaskLogic.progress(yearTasks)

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            PeriodHeader(
                title = anchor.year.toString(),
                subtitle = "${progress.done} of ${progress.total} done · ${yearTasks.size} tasks",
                progress = progress,
                onPrevious = { anchor = anchor.minusYears(1) },
                onNext = { anchor = anchor.plusYears(1) },
                onToday = { anchor = LocalDate.now() },
            )
        }
        if (yearTasks.isEmpty()) {
            item {
                TaskEmptyState(
                    title = "Nothing this year",
                    subtitle = "Add a task from the Today tab and it will appear under its month.",
                )
            }
        } else {
            items(buckets, key = { it.month.toString() }) { bucket ->
                val isThisMonth = YearMonth.from(LocalDate.now()) == bucket.month
                Surface(
                    color = if (isThisMonth) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = bucket.month.month.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = ChronosType.title,
                                color = if (isThisMonth) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            ProgressLine(bucket.progress.percent)
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            text = "${bucket.progress.done}/${bucket.progress.total}",
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---- Projects --------------------------------------------------------------

@Composable
private fun ProjectsView(onAddProject: () -> Unit, onAddTask: () -> Unit) {
    val projects = TaskStore.allProjects()
    var openProject by remember { mutableStateOf<Long?>(null) }
    val tasks = TaskStore.allTasks()

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text(
                text = "Projects",
                style = ChronosType.headline,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
            )
        }
        item { PrimaryAddButton("New project", onAddProject) }
        if (projects.isEmpty()) {
            item {
                TaskEmptyState(
                    title = "No projects yet",
                    subtitle = "Start one above: a project groups the tasks that belong together — a move, a course, a launch.",
                )
            }
        }
        items(projects, key = { it.id }) { project ->
            ProjectCard(project = project, onClick = {
                openProject = if (openProject == project.id) null else project.id
            })
            if (openProject == project.id) {
                val projectTasks = TaskLogic.forProject(tasks, project.id)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (projectTasks.isEmpty()) {
                        WellnessNote("No tasks in this project yet — add the first one below.")
                    }
                    projectTasks.forEach { task -> TaskScanRow(task, showDate = true) }
                    OutlinedButton(
                        onClick = onAddTask,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add a task", style = ChronosType.label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            WellnessNote(
                "Daily tasks roll up into the week, weeks into the month and months into the year. " +
                    "A task can have a project as well as a date.",
            )
        }
    }
}

// ---- Calendar --------------------------------------------------------------

@Composable
private fun CalendarView(tasks: List<Task>) {
    val context = LocalContext.current
    var readGranted by remember { mutableStateOf(CalendarSource.hasRead(context)) }
    var writeGranted by remember { mutableStateOf(CalendarSource.hasWrite(context)) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    val month = YearMonth.from(anchor)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        readGranted = CalendarSource.hasRead(context)
        writeGranted = CalendarSource.hasWrite(context)
        message = if (readGranted) "Calendar connected" else "Calendar permission not granted"
    }

    LaunchedEffect(month, readGranted) {
        events = if (readGranted) {
            CalendarSource.eventsBetween(context, month.atDay(1), month.atEndOfMonth())
        } else {
            emptyList()
        }
    }

    val monthDays = TaskLogic.monthDays(anchor)
    val tasksByDay = monthDays.associateWith { TaskLogic.tasksOn(tasks, it) }
    val eventsByDay = monthDays.associateWith { day ->
        events.filter { event ->
            val start = TaskLogic.dateOf(event.begin)
            val end = TaskLogic.dateOf(event.end)
            day in start..end
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            PeriodHeader(
                title = DateTimeFormatter.ofPattern("MMMM yyyy").format(month),
                subtitle = if (readGranted) "${events.size} calendar events"
                else "Calendar access not granted",
                progress = Progress(0, 0),
                onPrevious = { anchor = anchor.minusMonths(1) },
                onNext = { anchor = anchor.plusMonths(1) },
                onToday = { anchor = LocalDate.now() },
            )
        }
        if (!readGranted) {
            item {
                OutlinedButton(
                    onClick = {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Allow calendar access", style = ChronosType.label, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                WellnessNote(
                    "Chronos reads your events to show them next to your tasks, and writes a task " +
                        "to your calendar only when you tap the calendar icon on it.",
                )
            }
        }

        item { MonthGrid(month, tasksByDay, eventsByDay, selected) { selected = it } }

        item { GroupLabel(DateTimeFormatter.ofPattern("EEEE d MMMM").format(selected)) }
        val dayTasks = tasksByDay[selected].orEmpty()
        if (dayTasks.isEmpty()) {
            item {
                TaskEmptyState(
                    title = "No tasks on this day",
                    subtitle = "Pick another day above, or add a task from the Today tab.",
                )
            }
        } else {
            items(dayTasks, key = { "day:${it.id}" }) { task ->
                TaskScanRow(
                    task = task,
                    onAddToCalendar = { t ->
                        if (!writeGranted) {
                            launcher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR,
                                ),
                            )
                        } else {
                            val id = CalendarSource.insertTask(context, t)
                            TaskStore.attachCalendarEvent(t.id, id)
                            message = if (id != null) "Added \"${t.title}\" to your calendar"
                            else "No writable calendar found on this phone"
                        }
                    },
                )
            }
        }
        val dayEvents = eventsByDay[selected].orEmpty()
        if (dayEvents.isNotEmpty()) {
            item { GroupLabel("From your calendar") }
            items(dayEvents, key = { "e${it.id}${it.begin}" }) { event ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(event.color)
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = ChronosType.body,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${CalendarSource.timeLabel(event)} · ${event.calendarName}",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) {
            item { WellnessNote(message) }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    tasksByDay: Map<LocalDate, List<Task>>,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val firstWeekStart = TaskLogic.weekStart(month.atDay(1))
    val weeks = TaskLogic.weeksOfMonth(month)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            Row(Modifier.fillMaxWidth()) {
                (1..7).map {
                    java.time.DayOfWeek.of(it).getDisplayName(
                        java.time.format.TextStyle.NARROW,
                        java.util.Locale.ENGLISH,
                    )
                }.forEach { label ->
                    Text(
                        text = label,
                        style = ChronosType.label,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            weeks.forEach { weekStart ->
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { offset ->
                        val day = weekStart.plusDays(offset.toLong())
                        val inMonth = YearMonth.from(day) == month
                        val isSelected = day == selected
                        val isToday = day == LocalDate.now()
                        val taskCount = tasksByDay[day].orEmpty().size
                        val eventCount = eventsByDay[day].orEmpty().size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(GridGap)
                                .height(DayCellHeight)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    RoundedCornerShape(Radii.sm),
                                )
                                .pressable { if (inMonth) onSelect(day) }
                                .semantics { role = Role.Button },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.dayOfMonth.toString(),
                                    style = ChronosType.label,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold
                                    else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        inMonth -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                if (taskCount + eventCount > 0) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                                        if (taskCount > 0) Box(
                                            Modifier
                                                .size(DotSize)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                ),
                                        )
                                        if (eventCount > 0) Box(
                                            Modifier
                                                .size(DotSize)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.tertiary,
                                                    CircleShape,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Dot = task · second dot = calendar event",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shared header for the week, month, year and calendar views. */
@Composable
private fun PeriodHeader(
    title: String,
    subtitle: String,
    progress: Progress,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = ChronosType.headline,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                }
            }
            if (progress.total > 0) {
                Spacer(Modifier.height(Spacing.sm))
                ProgressLine(progress.percent)
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    text = "Back to now",
                    style = ChronosType.label,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .pressable { onToday() },
                )
            }
        }
    }
}

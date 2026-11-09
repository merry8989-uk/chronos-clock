package com.osintgram.chronos.ui.screens.life

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.tasks.CalendarEvent
import com.osintgram.chronos.tasks.CalendarSource
import com.osintgram.chronos.tasks.Progress
import com.osintgram.chronos.tasks.Task
import com.osintgram.chronos.tasks.TaskLogic
import com.osintgram.chronos.tasks.TaskStore
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.Haptics
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
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

// ---- Today -----------------------------------------------------------------

@Composable
private fun TodayView(tasks: List<Task>, onAdd: () -> Unit) {
    val today = LocalDate.now()
    val todays = TaskLogic.tasksOn(tasks, today)
    val overdue = TaskLogic.overdue(tasks)
    val progress = TaskLogic.progress(todays)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Today · ${DateTimeFormatter.ofPattern("EEE d MMM").format(today)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (progress.isEmpty) "Nothing due today"
                        else "${progress.done} of ${progress.total} done",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(progress.percent)
                    if (overdue.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${overdue.size} overdue from earlier",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item { AddButton("Add a task for today", onAdd) }

        if (overdue.isNotEmpty()) {
            item { WellnessLabel("OVERDUE") }
            items(overdue, key = { it.id }) { task -> TaskRow(task, showDate = true) }
        }

        item { WellnessLabel("TODAY") }
        if (todays.isEmpty()) {
            item {
                EmptyState(
                    title = "Clear day",
                    subtitle = "Add a task, or plan the week from the Week tab.",
                    modifier = Modifier.height(140.dp),
                )
            }
        } else {
            items(todays, key = { it.id }) { task -> TaskRow(task) }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PeriodHeader(
                title = TaskLogic.weekLabel(anchor),
                subtitle = "${weekProgress.done}/${weekProgress.total} done this week",
                progress = weekProgress,
                onPrevious = { anchor = anchor.minusWeeks(1) },
                onNext = { anchor = anchor.plusWeeks(1) },
                onToday = { anchor = LocalDate.now() },
            )
        }
        items(buckets, key = { it.date.toString() }) { bucket ->
            val isToday = bucket.date == LocalDate.now()
            Surface(
                color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = DateTimeFormatter.ofPattern("EEE d").format(bucket.date),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${bucket.progress.done}/${bucket.progress.total}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { onAdd(bucket.date) }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add a task on this day",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    bucket.tasks.forEach { task ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        if (task.done) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = task.title,
                                fontSize = 13.sp,
                                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (!task.allDay) {
                                Text(
                                    text = timeOf(task),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (bucket.tasks.isEmpty()) {
                        Text(
                            text = "Nothing planned",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
    val monthTasks = weeks.flatMap { it.allTasks }.distinctBy { it.id }
    val progress = TaskLogic.progress(monthTasks)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PeriodHeader(
                title = DateTimeFormatter.ofPattern("MMMM yyyy").format(month),
                subtitle = "${progress.done}/${progress.total} done · ${monthTasks.size} tasks",
                progress = progress,
                onPrevious = { anchor = anchor.minusMonths(1) },
                onNext = { anchor = anchor.plusMonths(1) },
                onToday = { anchor = LocalDate.now() },
            )
        }
        items(weeks, key = { it.start.toString() }) { week ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    GroupHeader(
                        title = "Week of ${DateTimeFormatter.ofPattern("d MMM").format(week.start)}",
                        subtitle = "${week.progress.done}/${week.progress.total}",
                        progress = week.progress.percent,
                    )
                    week.days.filter { it.tasks.isNotEmpty() }.forEach { day ->
                        Text(
                            text = "${DateTimeFormatter.ofPattern("EEE d").format(day.date)} — " +
                                day.tasks.joinToString(", ") { it.title },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (week.progress.total == 0) {
                        Text(
                            text = "No tasks this week",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PeriodHeader(
                title = anchor.year.toString(),
                subtitle = "${progress.done}/${progress.total} done · ${yearTasks.size} tasks",
                progress = progress,
                onPrevious = { anchor = anchor.minusYears(1) },
                onNext = { anchor = anchor.plusYears(1) },
                onToday = { anchor = LocalDate.now() },
            )
        }
        items(buckets, key = { it.month.toString() }) { bucket ->
            val isThisMonth = YearMonth.from(LocalDate.now()) == bucket.month
            Surface(
                color = if (isThisMonth) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = bucket.month.month.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        ProgressLine(bucket.progress.percent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${bucket.progress.done}/${bucket.progress.total}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { AddButton("New project", onAddProject) }
        if (projects.isEmpty()) {
            item {
                EmptyState(
                    title = "No projects yet",
                    subtitle = "A project groups tasks that belong together — a move, a course, a launch.",
                    modifier = Modifier.height(160.dp),
                )
            }
        }
        items(projects, key = { it.id }) { project ->
            ProjectCard(project = project, onClick = {
                openProject = if (openProject == project.id) null else project.id
            })
            if (openProject == project.id) {
                val projectTasks = TaskLogic.forProject(tasks, project.id)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (projectTasks.isEmpty()) {
                        WellnessNote("No tasks in this project yet.")
                    }
                    projectTasks.forEach { task -> TaskRow(task, showDate = true) }
                    OutlinedButton(onClick = onAddTask, modifier = Modifier.fillMaxWidth()) {
                        Text("Add a task", fontSize = 13.sp)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Allow calendar access", fontSize = 13.sp)
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

        item { WellnessLabel(DateTimeFormatter.ofPattern("EEEE d MMMM").format(selected)) }
        val dayTasks = tasksByDay[selected].orEmpty()
        if (dayTasks.isEmpty()) {
            item { WellnessNote("No tasks on this day.") }
        } else {
            items(dayTasks, key = { it.id }) { task ->
                TaskRow(
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
            item { WellnessLabel("FROM YOUR CALENDAR") }
            items(dayEvents, key = { "e${it.id}${it.begin}" }) { event ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(event.color)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${CalendarSource.timeLabel(event)} · ${event.calendarName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
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
                                .padding(2.dp)
                                .height(42.dp)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    RoundedCornerShape(9.dp),
                                )
                                .pressable { if (inMonth) onSelect(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.dayOfMonth.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold
                                    else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        inMonth -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                if (taskCount + eventCount > 0) {
                                    Spacer(Modifier.height(3.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (taskCount > 0) Box(
                                            Modifier
                                                .size(4.dp)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                ),
                                        )
                                        if (eventCount > 0) Box(
                                            Modifier
                                                .size(4.dp)
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Dot = task · second dot = calendar event",
                fontSize = 10.sp,
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
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                }
            }
            if (progress.total > 0) {
                Spacer(Modifier.height(6.dp))
                ProgressLine(progress.percent)
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    text = "Back to now",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(6.dp)
                        .pressable { onToday() },
                )
            }
        }
    }
}

package com.osintgram.chronos.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.AnimatedSwitch
import com.osintgram.chronos.ui.components.SwipeToDelete
import com.osintgram.chronos.ui.components.staggerIn
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@Composable
fun AlarmListScreen(modifier: Modifier = Modifier, onEdit: (Long?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms = AlarmStore.all()
    val use24 = Settings.use24Hour.value
    val showCountdown = Settings.showCountdown.value

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingDelete by remember { mutableStateOf<Alarm?>(null) }
    var menuAlarm by remember { mutableStateOf<Alarm?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val next = nextEnabledAlarm(alarms, nowMillis)

    Box(modifier = modifier.fillMaxWidth()) {
        if (alarms.isEmpty()) {
            // No loading state exists on this screen: the list is read straight
            // from the store, so empty is the only state that needs explaining.
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
                AlarmListHeader(
                    caption = "Nothing scheduled yet",
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
                )
                AlarmEmptyState(modifier = Modifier.weight(1f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom = Spacing.xl * 4,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "header") {
                    AlarmListHeader(
                        caption = nextAlarmSummary(next, nowMillis, use24, showCountdown),
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
                    )
                }

                items(alarms, key = { it.id }) { alarm ->
                    val index = alarms.indexOf(alarm)
                    SwipeToDelete(
                        modifier = Modifier.fillMaxWidth().staggerIn(index),
                        onDelete = {
                            pendingDelete = alarm
                            AlarmScheduler.cancel(context, alarm.id)
                            AlarmStore.delete(alarm.id)
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = "Alarm deleted",
                                    actionLabel = "Undo",
                                    withDismissAction = false,
                                )
                                val deleted = pendingDelete
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed &&
                                    deleted != null
                                ) {
                                    AlarmStore.upsert(deleted)
                                    if (deleted.enabled) AlarmScheduler.schedule(context, deleted)
                                }
                                pendingDelete = null
                            }
                        },
                    ) {
                        AlarmCard(
                            alarm = alarm,
                            use24 = use24,
                            showCountdown = showCountdown,
                            nowMillis = nowMillis,
                            isNext = next?.first?.id == alarm.id,
                            onClick = { onEdit(alarm.id) },
                            onLongClick = {
                                Haptics.tap(context)
                                menuAlarm = alarm
                            },
                            onToggle = { enabled ->
                                AlarmStore.setEnabled(alarm.id, enabled)
                                if (enabled) {
                                    AlarmScheduler.schedule(context, alarm.copy(enabled = true))
                                } else {
                                    AlarmScheduler.cancel(context, alarm.id)
                                }
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md),
        )

        // The one dominant action on this screen. It opens the same editor the
        // top-bar add button does, so there is still a single way in.
        FloatingActionButton(
            onClick = { onEdit(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
            shape = RoundedCornerShape(Radii.lg),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add alarm")
        }
    }

    menuAlarm?.let { alarm ->
        AlertDialog(
            onDismissRequest = { menuAlarm = null },
            title = { Text(alarm.timeText(use24) + " " + alarm.meridiem(use24)) },
            text = { Text(alarm.label.ifBlank { "No label" } + " · " + alarm.repeatSummary()) },
            confirmButton = {
                TextButton(onClick = {
                    val copy = alarm.copy(
                        id = AlarmStore.nextId(),
                        enabled = false,
                        createdAt = System.currentTimeMillis(),
                    )
                    AlarmStore.upsert(copy)
                    menuAlarm = null
                }) { Text("Duplicate") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        AlarmStore.setEnabled(alarm.id, !alarm.enabled)
                        if (!alarm.enabled) AlarmScheduler.schedule(context, alarm.copy(enabled = true))
                        else AlarmScheduler.cancel(context, alarm.id)
                        menuAlarm = null
                    }) { Text(if (alarm.enabled) "Turn off" else "Turn on") }
                    TextButton(onClick = {
                        AlarmScheduler.cancel(context, alarm.id)
                        AlarmStore.delete(alarm.id)
                        menuAlarm = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}

/**
 * The screen title and, under it, the one line that answers "when does this
 * ring next?". The summary is deliberately quiet: the list below is the content.
 */
@Composable
private fun AlarmListHeader(caption: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Alarms",
            style = ChronosType.headline,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = caption,
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Explains the one thing an empty list cannot show: how an alarm gets made. */
@Composable
private fun AlarmEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No alarms yet",
                style = ChronosType.title,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Tap Add alarm to set your first one. Pick a time, then " +
                    "choose the days it repeats, a label and a sound.",
                style = ChronosType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A status is always a word as well as a colour, never the colour alone. */
@Composable
private fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(Radii.sm),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = ChronosType.label,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

/** "Next alarm at 07:00 am · in 6 hours 12 min", or that there is no alarm on. */
private fun nextAlarmSummary(
    next: Pair<Alarm, Long>?,
    nowMillis: Long,
    use24: Boolean,
    showCountdown: Boolean,
): String {
    if (next == null) return "No alarm switched on"
    val alarm = next.first
    val triggerAt = next.second
    val time = if (use24) alarm.timeText(true) else alarm.timeText(false) + " " + alarm.meridiem(false)
    if (!showCountdown) return "Next alarm at $time"
    return "Next alarm at $time · ${describeRemaining(triggerAt - nowMillis)}"
}

/** The next alarm that will actually ring, with its trigger time. */
private fun nextEnabledAlarm(alarms: List<Alarm>, nowMillis: Long): Pair<Alarm, Long>? {
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    val holidays = HolidayStore.schedulingDates()
    return alarms.filter { it.enabled }
        .mapNotNull { alarm ->
            Alarm.nextTriggerMillis(alarm, now, holidays)?.let { alarm to it }
        }
        .minByOrNull { it.second }
}

private fun describeRemaining(remainingMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
    return when {
        minutes < 1 -> "ringing now"
        minutes < 60 -> "in $minutes minute${if (minutes == 1L) "" else "s"}"
        hours < 24 -> "in $hours hour${if (hours == 1L) "" else "s"} ${minutes % 60} min"
        else -> "in ${TimeUnit.MILLISECONDS.toDays(remainingMs)} days"
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    use24: Boolean,
    showCountdown: Boolean,
    nowMillis: Long,
    isNext: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (alarm.enabled) 1f else 0.45f,
        animationSpec = tween(Motion.MEDIUM),
        label = "cardAlpha",
    )

    // "Next" and "Off" are states of this alarm, so each carries a word and a
    // status colour instead of relying on tint alone.
    val statusChips: List<Pair<String, Color>> = listOfNotNull(
        if (isNext && alarm.enabled) Pair("Next", StatusColors.info) else null,
        if (!alarm.enabled) Pair("Off", StatusColors.warning) else null,
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(alarm.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            }
            // pointerInput has no semantics, so TalkBack could not activate the card.
            .semantics {
                role = Role.Button
                this.onClick(label = "Edit alarm") {
                    onClick()
                    true
                }
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = contentAlpha },
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = alarm.timeText(use24),
                            style = ChronosType.numeric,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!use24) {
                            Text(
                                text = alarm.meridiem(false),
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
                            )
                        }
                    }
                    if (alarm.label.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = alarm.label,
                            style = ChronosType.body,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = subtitle(alarm, showCountdown, nowMillis),
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                AnimatedSwitch(checked = alarm.enabled, onCheckedChange = onToggle)
            }

            if (statusChips.isNotEmpty() || alarm.hasMissions) {
                Spacer(Modifier.height(Spacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (statusChips.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            statusChips.forEach { (text, color) ->
                                StatusChip(text = text, color = color)
                            }
                        }
                    }
                    if (alarm.hasMissions) {
                        StatusChip(
                            text = "${alarm.missions.size} mission(s) to dismiss",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun subtitle(alarm: Alarm, showCountdown: Boolean, nowMillis: Long): String {
    val repeat = alarm.repeatSummary()
    if (!showCountdown || !alarm.enabled) return repeat
    val next = Alarm.nextTriggerMillis(
        alarm = alarm,
        now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault()),
        holidays = HolidayStore.schedulingDates(),
    ) ?: return repeat
    val diff = next - nowMillis
    if (diff <= 0) return repeat
    return "$repeat · ${describeGap(diff)}"
}

private fun describeGap(diffMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        minutes < 1 -> "Alarm in under a minute"
        minutes < 60 -> "Alarm in $minutes minute${if (minutes == 1L) "" else "s"}"
        hours < 24 -> "Alarm in $hours hour${if (hours == 1L) "" else "s"} ${minutes % 60} min"
        days == 1L -> "Alarm tomorrow"
        else -> "Alarm in $days days"
    }
}

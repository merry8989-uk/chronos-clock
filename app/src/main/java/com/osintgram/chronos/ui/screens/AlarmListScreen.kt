package com.osintgram.chronos.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.AnimatedSwitch
import com.osintgram.chronos.ui.components.CircleProgress
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.SwipeToDelete
import com.osintgram.chronos.ui.components.TickerText
import com.osintgram.chronos.ui.components.staggerIn
import com.osintgram.chronos.ui.theme.ClockTextStyle
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.pressable
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

    if (alarms.isEmpty()) {
        EmptyState(
            title = "No alarms yet",
            subtitle = "Tap + in the top bar to set your first alarm.",
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val next = nextEnabledAlarm(alarms, nowMillis)
            if (next != null && showCountdown) {
                item(key = "hero") {
                    NextAlarmHero(
                        alarm = next.first,
                        triggerAt = next.second,
                        use24 = use24,
                        onClick = { onEdit(next.first.id) },
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
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

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
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

/**
 * Hero card: the next alarm, with a ring that closes as the moment approaches.
 */
@Composable
private fun NextAlarmHero(
    alarm: Alarm,
    triggerAt: Long,
    use24: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(triggerAt) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remaining = (triggerAt - nowMillis).coerceAtLeast(0L)
    val window = TimeUnit.HOURS.toMillis(24)
    val progress by animateFloatAsState(
        targetValue = (1f - remaining.toFloat() / window).coerceIn(0f, 1f),
        animationSpec = tween(Motion.SLOW, easing = Motion.easeOut),
        label = "heroRing",
    )
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .pressable(scale = 0.985f, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.06f)),
                    ),
                    RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NEXT ALARM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        TickerText(
                            text = alarm.timeText(use24),
                            style = ClockTextStyle.copy(fontSize = 44.sp, fontWeight = FontWeight.Light),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (!use24) {
                            Text(
                                text = alarm.meridiem(false),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 5.dp, bottom = 8.dp),
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            if (alarm.label.isNotBlank()) append(alarm.label).append(" · ")
                            append(alarm.repeatSummary())
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = describeRemaining(remaining),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                CircleProgress(
                    progress = progress,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }
            }
        }
    }
}

private fun describeRemaining(remainingMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
    return when {
        minutes < 1 -> "Ringing now"
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (alarm.enabled) 1f else 0.45f,
        animationSpec = tween(Motion.MEDIUM),
        label = "cardAlpha",
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(alarm.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = contentAlpha },
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = alarm.timeText(use24),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        style = ClockTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!use24) {
                        Text(
                            text = alarm.meridiem(false),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 5.dp, bottom = 6.dp),
                        )
                    }
                    if (alarm.label.isNotBlank()) {
                        Text(
                            text = alarm.label,
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp, bottom = 7.dp),
                        )
                    }
                }
                Text(
                    text = subtitle(alarm, showCountdown, nowMillis),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (alarm.hasMissions) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "${alarm.missions.size} mission(s) to dismiss",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            AnimatedSwitch(checked = alarm.enabled, onCheckedChange = onToggle)
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

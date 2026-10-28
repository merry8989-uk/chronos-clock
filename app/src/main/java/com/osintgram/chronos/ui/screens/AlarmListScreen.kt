package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.EmptyState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@Composable
fun AlarmListScreen(modifier: Modifier = Modifier, onEdit: (Long?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val alarms = AlarmStore.all()
    val use24 = Settings.use24Hour.value
    val showCountdown = Settings.showCountdown.value

    // Keeps the "Alarm in ..." line honest without recomposing every frame.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
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

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(alarms, key = { it.id }) { alarm ->
            AlarmCard(
                alarm = alarm,
                use24 = use24,
                showCountdown = showCountdown,
                nowMillis = nowMillis,
                onClick = { onEdit(alarm.id) },
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

@Composable
private fun AlarmCard(
    alarm: Alarm,
    use24: Boolean,
    showCountdown: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = alarm.timeText(use24),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!use24) {
                        Text(
                            text = alarm.meridiem(false),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 7.dp),
                        )
                    }
                    if (alarm.label.isNotBlank()) {
                        Text(
                            text = alarm.label,
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle(alarm, showCountdown, nowMillis),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

/** "Every day · Alarm in 33 minutes" — the second half only when it matters. */
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

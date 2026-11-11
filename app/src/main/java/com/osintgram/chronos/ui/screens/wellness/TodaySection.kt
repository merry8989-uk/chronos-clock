package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.screens.MetricBlock
import com.osintgram.chronos.ui.screens.MetricRow
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.pressable
import com.osintgram.chronos.wellness.DayLog
import com.osintgram.chronos.wellness.HealthConnectActions
import com.osintgram.chronos.wellness.HealthConnectSource
import com.osintgram.chronos.wellness.HealthConnectState
import com.osintgram.chronos.wellness.HeartRateSensor
import com.osintgram.chronos.wellness.rememberHeartRate
import kotlinx.coroutines.launch
import com.osintgram.chronos.wellness.StepCounter
import com.osintgram.chronos.wellness.WellnessLogic
import com.osintgram.chronos.wellness.WellnessPlans
import com.osintgram.chronos.wellness.WellnessStore
import com.osintgram.chronos.wellness.WellnessSync
import com.osintgram.chronos.wellness.rememberHealthConnect

/**
 * Today: steps, water, sleep, habits and the two one-tap actions. Every number
 * on this screen comes from the store or a real sensor read — there are no
 * sample values, so an empty install shows zeros and says where they come from.
 */
@Composable
fun TodaySection(
    onOpenWorkout: () -> Unit,
    onOpenMeditate: () -> Unit,
) {
    val context = LocalContext.current
    val day = WellnessStore.day()
    val stepState = StepCounter.rememberTodaySteps(enabled = true)
    val shownSteps = if (stepState.steps > 0) stepState.steps else day.steps
    val stepGoal = Settings.stepGoal.value
    val waterGoal = Settings.waterGoalMl.value
    val sleepGoal = Settings.sleepGoalMinutes.value
    val sensorAvailable = remember(context) { StepCounter.isAvailable(context) }
    val (healthState, healthActions) = rememberHealthConnect(enabled = Settings.healthConnectSync.value)
    val writeScope = rememberCoroutineScope()
    val streak = WellnessStore.streak()

    // Realtime: while this card is on screen, pull today's totals from Health
    // Connect every ten seconds, so the number keeps up with the phone instead
    // of only updating when the user taps Sync.
    LaunchedEffect(healthState.granted) {
        if (!healthState.granted) return@LaunchedEffect
        while (true) {
            HealthConnectSource.readToday(context)?.let { WellnessSync.apply(context, it) }
            kotlinx.coroutines.delay(10_000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepRing(
                            steps = shownSteps,
                            goal = stepGoal,
                            modifier = Modifier.size(104.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = WellnessLogic.formatSteps(shownSteps),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "steps today · goal ${WellnessLogic.formatSteps(stepGoal)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = when {
                                    healthState.granted -> "Health Connect — whole day, refreshed every 10 s"
                                    sensorAvailable && stepState.countedFromNow ->
                                        "Sensor — counted from when you opened Chronos today"
                                    sensorAvailable -> "Sensor — counted from midnight"
                                    else -> "No step sensor — add steps by hand"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ProgressLine(WellnessLogic.progress(shownSteps, stepGoal))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                WellnessStore.setSteps((shownSteps - 500).coerceAtLeast(0))
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("−500", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                WellnessStore.setSteps(shownSteps + 500)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("+500", fontSize = 12.sp) }
                        Spacer(Modifier.weight(1f))
                        if (streak > 0) {
                            Text(
                                text = "$streak day streak",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocalDrink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Water",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${WellnessLogic.formatLitres(day.waterMl)} of ${WellnessLogic.formatLitres(waterGoal)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(WellnessLogic.progress(day.waterMl, waterGoal))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(250, 500).forEach { amount ->
                            OutlinedButton(
                                onClick = {
                                    Haptics.tick(context)
                                    WellnessStore.addWater(amount)
                                    // Written back to Health Connect too, when it is connected.
                                    writeScope.launch {
                                        HealthConnectSource.writeHydration(context, amount)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) { Text("+${amount} ml", fontSize = 12.sp) }
                        }
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                WellnessStore.addWater(-250)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("−250", fontSize = 12.sp) }
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Sleep last night",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = WellnessLogic.formatMinutes(day.sleepMinutes),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressLine(WellnessLogic.progress(day.sleepMinutes, sleepGoal))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                WellnessStore.setSleepMinutes((day.sleepMinutes - 30).coerceAtLeast(0))
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("−30 min", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                WellnessStore.setSleepMinutes(day.sleepMinutes + 30)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("+30 min", fontSize = 12.sp) }
                    }
                }
            }
        }

        item { WellnessLabel("HABITS TODAY") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WellnessPlans.habits, key = { it.id }) { habit ->
                    Pill(
                        text = if (habit.id in day.habits) "${habit.title} ✓" else habit.title,
                        selected = habit.id in day.habits,
                        onClick = {
                            Haptics.tick(context)
                            WellnessStore.toggleHabit(habit.id)
                        },
                    )
                }
            }
        }

        item { WellnessLabel("ONE TAP") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onOpenWorkout() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Workout", fontSize = 13.sp)
                }
                Button(
                    onClick = { onOpenMeditate() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(Icons.Filled.SelfImprovement, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Breathe", fontSize = 13.sp)
                }
            }
        }

        item { WellnessLabel("HEART RATE") }
        item { HeartRateCard(day = day) }

        item { WellnessLabel("HEALTH CONNECT") }
        item { HealthConnectCard(state = healthState, actions = healthActions) }

        item {
            WellnessNote(
                "Steps come from this phone's step counter and are counted from the first " +
                    "reading of the day. Water and sleep are entered by hand or synced from " +
                    "Health Connect. Nothing on this screen leaves the device.",
            )
        }
    }
}

/** The arc that shows today's steps against the goal. */
@Composable
private fun StepRing(steps: Int, goal: Int, modifier: Modifier = Modifier) {
    val progress = WellnessLogic.progress(steps, goal)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        if (progress > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

/**
 * Heart rate from the phone's own sensor. It is read only while the user has
 * tapped Measure, and the sensor's zero ("no contact") readings are ignored.
 */
@Composable
private fun HeartRateCard(day: DayLog) {
    val context = LocalContext.current
    val hasSensor = remember(context) { HeartRateSensor.hasSensor(context) }
    var granted by remember { mutableStateOf(HeartRateSensor.hasPermission(context)) }
    var measuring by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        if (allowed) measuring = true
    }
    val live = rememberHeartRate(measuring) { reading ->
        WellnessStore.setHeartRate(reading.bpm, reading.measuredAt)
    }
    // The sensor runs for 30 seconds and then stops on its own.
    LaunchedEffect(measuring) {
        if (measuring) {
            kotlinx.coroutines.delay(30_000)
            measuring = false
        }
    }
    val bpm = live?.bpm ?: day.heartRateBpm
    val measuredAt = live?.measuredAt ?: day.heartRateAt

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (bpm > 0) "$bpm bpm" else "No reading yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            !hasSensor -> "This phone has no heart-rate sensor"
                            !granted -> "Allow the body sensor to read your pulse"
                            measuring -> "Hold still, measuring…"
                            bpm > 0 -> "${WellnessLogic.heartRateBand(bpm)} · ${timeLabel(measuredAt)}"
                            else -> "Tap Measure and hold still"
                        },
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hasSensor) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        !granted -> Button(
                            onClick = {
                                Haptics.tick(context)
                                permissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Allow", fontSize = 12.sp) }
                        else -> OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                measuring = !measuring
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text(if (measuring) "Stop" else "Measure", fontSize = 12.sp) }
                    }
                }
            }
            WellnessNote(
                "Read straight from the sensor while this card is on screen. Nothing is written " +
                    "to Health Connect.",
            )
        }
    }
}

@Composable
private fun timeLabel(millis: Long): String {
    if (millis <= 0L) return ""
    val time = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return String.format("at %02d:%02d", time.hour, time.minute)
}

/** Health Connect: connect, sync, import history, or say plainly what is missing. */
@Composable
private fun HealthConnectCard(state: HealthConnectState, actions: HealthConnectActions) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (state.status) {
                            HealthConnectSource.Status.Ready ->
                                if (state.granted) "Connected" else "Available — not connected"
                            HealthConnectSource.Status.UpdateRequired -> "Health Connect needs an update"
                            HealthConnectSource.Status.Unavailable -> "Health Connect not available"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.message.ifBlank {
                            when {
                                state.status != HealthConnectSource.Status.Ready ->
                                    "Install Health Connect from the Play Store to sync steps, water and sleep."
                                state.granted -> "Reading steps, water and sleep recorded by other apps."
                                else -> "Allow read access to steps, water and sleep."
                            }
                        },
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.status == HealthConnectSource.Status.Ready) {
                if (state.granted) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append(if (state.hasBackground) "Background sync on" else "Background sync off")
                            append(" · ")
                            append(if (state.hasHistory) "history read on" else "history read off")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.needsManualGrant) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { actions.updateHealthConnect() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text("Update Health Connect", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { actions.openSettings() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text("Open Health Connect", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = actions.connect,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) { Text("Try Connect again", fontSize = 11.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.granted && !state.needsManualGrant) {
                        OutlinedButton(
                            onClick = { actions.openSettings() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text("Open Health Connect", fontSize = 12.sp) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = actions.connect,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Connect", fontSize = 12.sp) }
                    } else if (state.granted) {
                        OutlinedButton(
                            onClick = actions.sync,
                            enabled = !state.busy,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Sync now", fontSize = 12.sp) }
                        if (!state.hasBackground || !state.hasHistory) {
                            OutlinedButton(
                                onClick = actions.requestExtras,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("Background access", fontSize = 12.sp) }
                        }
                        if (state.hasHistory) {
                            OutlinedButton(
                                onClick = actions.importHistory,
                                enabled = !state.busy,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("Import 84 days", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

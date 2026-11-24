package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.components.RingStat
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.heroSurfaceBrush
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
import kotlin.math.roundToInt

/** Where the Quick add card sits in the list below, for the water tile's jump. */
private const val QUICK_ADD_INDEX = 2

/**
 * Today: a dashboard, not a form.
 *
 * Four tiles answer "how am I doing" at a glance and tap through to the section
 * that owns each number; the steps hero carries the day's headline figure; one
 * Quick add row holds every correction this screen offers, so the small buttons
 * are in one place instead of scattered under each card. Habits, heart rate,
 * Health Connect and the two deep links follow.
 *
 * Every number on this screen comes from the store or a real sensor read —
 * there are no sample values, so an empty install shows zeros and says where
 * they come from.
 *
 * @param onOpenWorkout opens the hidden Workout section from the bottom button.
 * @param onOpenSleep opens the Sleep section from the sleep tile.
 * @param onOpenBody opens the Body section from the heart tile.
 * @param onOpenActivity opens the Activity section from the steps tile.
 * @param onOpenLearn opens the hidden Learn section (yoga and breathing) from
 *   the More menu.
 * @param onOpenProgress opens Progress, where "how this is counted" is written.
 */
@Composable
fun TodaySection(
    onOpenWorkout: () -> Unit,
    onOpenSleep: () -> Unit = {},
    onOpenBody: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenLearn: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
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
    val listState = rememberLazyListState()

    // Realtime: while this screen is on screen, pull today's totals from Health
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
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            // The dashboard row. Water has no section of its own, so its tile
            // scrolls to the Quick add row where the water steppers live.
            MetricTiles(
                steps = shownSteps,
                stepGoal = stepGoal,
                waterMl = day.waterMl,
                waterGoal = waterGoal,
                sleepMinutes = day.sleepMinutes,
                sleepGoal = sleepGoal,
                heartBpm = day.heartRateBpm,
                onSteps = onOpenActivity,
                onWater = { writeScope.launch { listState.animateScrollToItem(QUICK_ADD_INDEX) } },
                onSleep = onOpenSleep,
                onHeart = onOpenBody,
            )
        }

        item {
            // The hero. One number leads: the count sits inside its ring, the
            // goal sits under it, and where the number came from is a caption.
            // The gradient is the only new surface treatment on the screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .background(heroSurfaceBrush()),
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(152.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            RingStat(
                                progress = WellnessLogic.progress(shownSteps, stepGoal),
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 12.dp,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = WellnessLogic.formatSteps(shownSteps),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "of ${WellnessLogic.formatSteps(stepGoal)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "STEPS TODAY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                text = when {
                                    healthState.granted -> "Health Connect — whole day, refreshed every 10 s"
                                    sensorAvailable && stepState.countedFromNow ->
                                        "Sensor — counted from when you opened Chronos today"
                                    sensorAvailable -> "Sensor — counted from midnight"
                                    else -> "No step sensor — add steps by hand"
                                },
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (streak > 0) {
                                Spacer(Modifier.height(Spacing.sm))
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
        }

        item {
            // One place for every correction on this screen — water, steps and
            // sleep, each in both directions — with the handlers the cards used
            // to own, so no number needs hunting for its own minus button.
            QuickAddRow(
                onAddWater = { amount ->
                    Haptics.tick(context)
                    WellnessStore.addWater(amount)
                    // Written back to Health Connect too, when it is connected.
                    writeScope.launch {
                        HealthConnectSource.writeHydration(context, amount)
                    }
                },
                onRemoveWater = {
                    Haptics.tick(context)
                    WellnessStore.addWater(-250)
                },
                onAddSteps = {
                    Haptics.tick(context)
                    WellnessStore.setSteps(shownSteps + 500)
                },
                onRemoveSteps = {
                    Haptics.tick(context)
                    WellnessStore.setSteps((shownSteps - 500).coerceAtLeast(0))
                },
                onAddSleep = {
                    Haptics.tick(context)
                    WellnessStore.setSleepMinutes(day.sleepMinutes + 30)
                },
                onRemoveSleep = {
                    Haptics.tick(context)
                    WellnessStore.setSleepMinutes((day.sleepMinutes - 30).coerceAtLeast(0))
                },
            )
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

        item { WellnessLabel("HEART RATE") }
        item { HeartRateCard(day = day) }

        item {
            // One short line up front; the long version waits behind More.
            ExpandableNote(
                summary = "Steps, water and sleep stay on this device.",
                detail = "Steps come from this phone's step counter and are counted from the " +
                    "first reading of the day. Water and sleep are entered by hand or synced " +
                    "from Health Connect. Nothing on this screen leaves the device.",
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        Haptics.tick(context)
                        onOpenWorkout()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Log a workout", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(context)
                        onOpenProgress()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text("How this is counted", fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        item { WellnessLabel("HEALTH CONNECT") }
        item { HealthConnectCard(state = healthState, actions = healthActions) }
    }
}

/**
 * The four numbers that matter today, side by side: steps, water, sleep and
 * heart. Each tile is one tap into the place that owns the number, and the bar
 * under the value is the same progress the bigger card draws.
 */
@Composable
private fun MetricTiles(
    steps: Int,
    stepGoal: Int,
    waterMl: Int,
    waterGoal: Int,
    sleepMinutes: Int,
    sleepGoal: Int,
    heartBpm: Int,
    onSteps: () -> Unit,
    onWater: () -> Unit,
    onSleep: () -> Unit,
    onHeart: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MetricTile(
            icon = Icons.Filled.DirectionsWalk,
            value = WellnessLogic.formatSteps(steps),
            label = "steps",
            progress = WellnessLogic.progress(steps, stepGoal),
            onClick = onSteps,
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            icon = Icons.Filled.LocalDrink,
            value = WellnessLogic.formatLitres(waterMl),
            label = "water",
            progress = WellnessLogic.progress(waterMl, waterGoal),
            onClick = onWater,
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            icon = Icons.Filled.Bedtime,
            value = WellnessLogic.formatMinutes(sleepMinutes),
            label = "sleep",
            progress = WellnessLogic.progress(sleepMinutes, sleepGoal),
            onClick = onSleep,
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            icon = Icons.Filled.Favorite,
            value = if (heartBpm > 0) "$heartBpm bpm" else "—",
            label = "heart",
            // Heart rate has no goal, so no bar: an empty one would read as zero.
            progress = null,
            onClick = onHeart,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One compact tile: icon, value, label and the value's progress towards its
 * goal. The value, the label and the percentage are announced as one node, so a
 * screen reader says "steps 8,240, 100% of goal" instead of three fragments.
 *
 * @param progress 0f..1f towards the goal, or null when the metric has no goal.
 */
@Composable
private fun MetricTile(
    icon: ImageVector,
    value: String,
    label: String,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .semantics {
                contentDescription = buildString {
                    append(label)
                    append(' ')
                    append(value)
                    if (progress != null) {
                        append(", ")
                        append((progress * 100f).roundToInt())
                        append("% of goal")
                    }
                }
            }
            .pressable(scale = 0.96f) { onClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            // A tile without a goal keeps the same height, so the row stays even.
            Spacer(Modifier.height(6.dp))
            if (progress != null) {
                ProgressLine(progress)
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/**
 * The one correction card: water, steps and sleep, in and out. The small
 * buttons live here instead of under each card, so there is a single place to
 * fix a wrong number — and the handlers are the ones the cards used to own.
 */
@Composable
private fun QuickAddRow(
    onAddWater: (Int) -> Unit,
    onRemoveWater: () -> Unit,
    onAddSteps: () -> Unit,
    onRemoveSteps: () -> Unit,
    onAddSleep: () -> Unit,
    onRemoveSleep: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = "QUICK ADD",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            QuickAddLine("water") {
                QuickAddButton("+250 ml") { onAddWater(250) }
                QuickAddButton("+500 ml") { onAddWater(500) }
                QuickAddButton("−250 ml", onRemoveWater)
            }
            Spacer(Modifier.height(6.dp))
            QuickAddLine("steps") {
                QuickAddButton("+500", onAddSteps)
                QuickAddButton("−500", onRemoveSteps)
            }
            Spacer(Modifier.height(6.dp))
            QuickAddLine("sleep") {
                QuickAddButton("−30 min", onRemoveSleep)
                QuickAddButton("+30 min", onAddSleep)
            }
        }
    }
}

/** One labelled line inside the Quick add card: a quiet name, then its buttons. */
@Composable
private fun QuickAddLine(label: String, buttons: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        buttons()
    }
}

/** A single correction button, sized to share its line evenly. */
@Composable
private fun RowScope.QuickAddButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 11.sp, maxLines = 1) }
}

/**
 * A note that keeps its first line visible and hides the rest behind More, so
 * the long explanation of where the numbers come from does not push the cards
 * off the screen.
 */
@Composable
private fun ExpandableNote(summary: String, detail: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = summary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
        if (open) {
            WellnessNote(detail)
        }
        TextButton(
            onClick = { open = !open },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(if (open) "Less" else "More", fontSize = 12.sp)
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
                "Sensor only — nothing is written to Health Connect.",
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
                        if (state.settingsAvailable) {
                            OutlinedButton(
                                onClick = { actions.openSettings() },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("Open Health Connect", fontSize = 12.sp) }
                        }
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

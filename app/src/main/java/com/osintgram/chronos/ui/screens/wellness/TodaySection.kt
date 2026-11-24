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
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.components.RingStat
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
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
 * No step of the type scale carries letter spacing, so the tracking the small
 * upper-case labels use is declared once here instead of at each call site.
 */
private val LABEL_TRACKING = 1.sp

/**
 * The scale has no semibold body step, and a tile figure has to stay small
 * enough for four of them to sit side by side: body size at semibold.
 */
private val TileValueType = ChronosType.body.copy(fontWeight = FontWeight.SemiBold)

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
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
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
                    .clip(RoundedCornerShape(Radii.lg))
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
                                    style = ChronosType.numeric,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    text = "of ${WellnessLogic.formatSteps(stepGoal)}",
                                    style = ChronosType.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "STEPS TODAY",
                                style = ChronosType.label,
                                letterSpacing = LABEL_TRACKING,
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
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (streak > 0) {
                                Spacer(Modifier.height(Spacing.sm))
                                Text(
                                    text = "$streak day streak",
                                    style = ChronosType.label,
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
            // to own, so no number needs hunting for its own minus button. This
            // is the one action Today offers, so it carries the strongest wash
            // on the screen and its "add" buttons are the filled ones.
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

        // Nothing logged yet is a real state, not a zero: say what to do about
        // it. It sits after the Quick add row, so the row keeps its index and
        // the water tile still scrolls to the right place.
        if (day.isEmpty && shownSteps <= 0) {
            item {
                EmptyState(
                    title = "Nothing logged yet today",
                    subtitle = "Use Quick add above to log water, steps or sleep — or connect " +
                        "Health Connect to have the phone fill this in for you.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.lg),
                )
            }
        }

        item { WellnessLabel("HABITS TODAY") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(
                    onClick = {
                        Haptics.tick(context)
                        onOpenWorkout()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Log a workout", style = ChronosType.label, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(context)
                        onOpenProgress()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text("How this is counted", style = ChronosType.label, maxLines = 1)
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
        shape = RoundedCornerShape(Radii.md),
        modifier = modifier
            .clip(RoundedCornerShape(Radii.md))
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
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = value,
                style = TileValueType,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            // A tile without a goal keeps the same height, so the row stays even.
            Spacer(Modifier.height(Spacing.xs))
            if (progress != null) {
                ProgressLine(progress)
            } else {
                Spacer(Modifier.height(Spacing.xs))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(MaterialTheme.colorScheme.surface)
            .background(heroSurfaceBrush(topAlpha = 0.22f)),
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
            Text(
                text = "QUICK ADD",
                style = ChronosType.label,
                letterSpacing = LABEL_TRACKING,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.sm))
            QuickAddLine("water") {
                QuickAddButton("+250 ml", primary = true) { onAddWater(250) }
                QuickAddButton("+500 ml", primary = true) { onAddWater(500) }
                QuickAddButton("−250 ml", onRemoveWater)
            }
            Spacer(Modifier.height(Spacing.xs))
            QuickAddLine("steps") {
                QuickAddButton("+500", primary = true, onClick = onAddSteps)
                QuickAddButton("−500", onRemoveSteps)
            }
            Spacer(Modifier.height(Spacing.xs))
            QuickAddLine("sleep") {
                QuickAddButton("−30 min", onRemoveSleep)
                QuickAddButton("+30 min", primary = true, onClick = onAddSleep)
            }
        }
    }
}

/** One labelled line inside the Quick add card: a quiet name, then its buttons. */
@Composable
private fun QuickAddLine(label: String, buttons: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        buttons()
    }
}

/**
 * A single correction button, sized to share its line evenly. Adding is the
 * filled one and taking away is the outlined one, so the direction is readable
 * before the label is.
 */
@Composable
private fun RowScope.QuickAddButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm)
    if (primary) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            contentPadding = padding,
        ) { Text(text, style = ChronosType.label, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            contentPadding = padding,
        ) { Text(text, style = ChronosType.label, maxLines = 1) }
    }
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
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs),
        )
        if (open) {
            WellnessNote(detail)
        }
        TextButton(
            onClick = { open = !open },
            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs),
        ) {
            Text(if (open) "Less" else "More", style = ChronosType.label)
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
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (bpm > 0) "$bpm bpm" else "No reading yet",
                        style = ChronosType.title,
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
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hasSensor) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    when {
                        !granted -> Button(
                            onClick = {
                                Haptics.tick(context)
                                permissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)
                            },
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        ) { Text("Allow", style = ChronosType.label) }
                        else -> OutlinedButton(
                            onClick = {
                                Haptics.tick(context)
                                measuring = !measuring
                            },
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        ) { Text(if (measuring) "Stop" else "Measure", style = ChronosType.label) }
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
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (state.status) {
                            HealthConnectSource.Status.Ready ->
                                if (state.granted) "Connected" else "Available — not connected"
                            HealthConnectSource.Status.UpdateRequired -> "Health Connect needs an update"
                            HealthConnectSource.Status.Unavailable -> "Health Connect not available"
                        },
                        style = ChronosType.body,
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
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.status == HealthConnectSource.Status.Ready) {
                if (state.granted) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = buildString {
                            append(if (state.hasBackground) "Background sync on" else "Background sync off")
                            append(" · ")
                            append(if (state.hasHistory) "history read on" else "history read off")
                        },
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                if (state.needsManualGrant) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(
                            onClick = { actions.updateHealthConnect() },
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                        ) { Text("Update Health Connect", style = ChronosType.label) }
                        if (state.settingsAvailable) {
                            OutlinedButton(
                                onClick = { actions.openSettings() },
                                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                            ) { Text("Open Health Connect", style = ChronosType.label) }
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    OutlinedButton(
                        onClick = actions.connect,
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                    ) { Text("Try Connect again", style = ChronosType.label) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (!state.granted && !state.needsManualGrant) {
                        OutlinedButton(
                            onClick = { actions.openSettings() },
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                        ) { Text("Open Health Connect", style = ChronosType.label) }
                        Spacer(Modifier.width(Spacing.sm))
                        Button(
                            onClick = actions.connect,
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        ) { Text("Connect", style = ChronosType.label) }
                    } else if (state.granted) {
                        OutlinedButton(
                            onClick = actions.sync,
                            enabled = !state.busy,
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        ) { Text("Sync now", style = ChronosType.label) }
                        if (!state.hasBackground || !state.hasHistory) {
                            OutlinedButton(
                                onClick = actions.requestExtras,
                                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                            ) { Text("Background access", style = ChronosType.label) }
                        }
                        if (state.hasHistory) {
                            OutlinedButton(
                                onClick = actions.importHistory,
                                enabled = !state.busy,
                                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                            ) { Text("Import 84 days", style = ChronosType.label) }
                        }
                    }
                }
            }
        }
    }
}

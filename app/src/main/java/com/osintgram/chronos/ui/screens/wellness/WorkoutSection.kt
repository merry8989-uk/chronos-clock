package com.osintgram.chronos.ui.screens.wellness

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.RowDivider
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.ui.theme.heroSurfaceBrush
import com.osintgram.chronos.ui.theme.pressable
import com.osintgram.chronos.wellness.HealthConnectSource
import com.osintgram.chronos.wellness.WellnessLogic
import com.osintgram.chronos.wellness.WellnessPlans
import com.osintgram.chronos.wellness.WellnessStore
import com.osintgram.chronos.wellness.WorkoutEntry
import com.osintgram.chronos.wellness.WorkoutSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Workout: pick a routine, log sets as you go and close the session. The
 * session is written to the store the moment it starts, so switching tabs or
 * closing the app never loses a workout in progress.
 *
 * Styled against the design system: spacing comes from [Spacing], shapes from
 * [Radii], type from [ChronosType]. The running session's volume is the one
 * number the card leads with, "Log a set" is the only filled button on the
 * screen, and everything that lists is a row rather than a card of its own.
 */
@Composable
fun WorkoutSection() {
    val context = LocalContext.current
    val active = WellnessStore.activeWorkout()
    val history = WellnessStore.allWorkouts().filter { it.isFinished }.take(6)
    var showCustom by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    // What Health Connect said about the last finished session: null until a
    // write has been attempted. It only feeds the status line below; the write
    // itself, and everything it is given, is unchanged.
    var healthWrite by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // The result of the Health Connect write is reported in words as well
        // as in colour, so a failed write is never just an amber dot.
        val outcome = healthWrite
        if (outcome != null) {
            item { HealthWriteStatus(written = outcome) }
        }

        if (active != null) {
            item { ActiveSessionCard(active) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // Finishing is the exception, not the action, so it steps
                    // back to an outline while logging a set stays filled.
                    OutlinedButton(
                        onClick = { confirmFinish = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.lg,
                            vertical = Spacing.md,
                        ),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Finish session", style = ChronosType.label)
                    }
                    TextButton(
                        onClick = {
                            Haptics.tick(context)
                            WellnessStore.discardWorkout(active.id)
                        },
                        contentPadding = PaddingValues(
                            horizontal = Spacing.lg,
                            vertical = Spacing.md,
                        ),
                    ) { Text("Discard", style = ChronosType.label) }
                }
            }
            item { WellnessLabel("LOG A SET") }
            val plan = WellnessPlans.plan(active.planId)
            if (plan != null) {
                items(plan.exercises, key = { it.name }) { exercise ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Spacing.md,
                                vertical = Spacing.sm,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = exercise.name,
                                    style = ChronosType.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Plan: ${exercise.sets} × ${exercise.reps}" +
                                        if (exercise.cue.isNotBlank()) " · ${exercise.cue}" else "",
                                    style = ChronosType.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            // The one filled action on this screen.
                            Button(
                                onClick = {
                                    Haptics.tick(context)
                                    WellnessStore.addEntry(
                                        active.id,
                                        WorkoutEntry(exercise.name, 1, exercise.reps),
                                    )
                                },
                                contentPadding = PaddingValues(
                                    horizontal = Spacing.md,
                                    vertical = Spacing.sm,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Log a set of ${exercise.name}",
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Log a set", style = ChronosType.label)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showCustom = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = Spacing.sm),
                ) { Text("Log something else", style = ChronosType.label) }
            }
            item { WellnessLabel("LOGGED") }
            if (active.entries.isEmpty()) {
                item {
                    WellnessNote("No sets logged yet. Tap + beside a movement each time you finish a set.")
                }
            } else {
                // The logged sets read as one list: a single rounded surface
                // with a divider between rows, rather than one card per set.
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            active.entries.forEachIndexed { index, entry ->
                                if (index > 0) RowDivider()
                                Row(
                                    modifier = Modifier.padding(
                                        start = Spacing.md,
                                        end = Spacing.xs,
                                        top = Spacing.xs,
                                        bottom = Spacing.xs,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${entry.name} — ${entry.sets} × ${entry.reps}" +
                                            if (entry.weightKg > 0) " @ ${entry.weightKg} kg" else "",
                                        style = ChronosType.body,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        Haptics.tick(context)
                                        WellnessStore.removeEntry(active.id, index)
                                    }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Remove this set",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item { WellnessLabel("START A ROUTINE") }
            items(WellnessPlans.plans, key = { it.id }) { plan ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable { WellnessStore.startWorkout(plan.id, plan.title) },
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = plan.title,
                                style = ChronosType.title,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${plan.focus} · ${plan.minutes} min · ${plan.level}",
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Start",
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                WellnessNote(
                    "Tapping a routine starts a session immediately. The starting sets and reps " +
                        "are suggestions — log what you actually did.",
                )
            }
        }

        if (history.isNotEmpty()) {
            item { WellnessLabel("RECENT SESSIONS") }
            items(history, key = { it.id }) { session -> HistoryRow(session) }
        }

        if (active == null && history.isEmpty()) {
            item {
                Spacer(Modifier.height(Spacing.lg))
                // The empty state says how a session starts, then what the list
                // will hold once one is finished.
                EmptyState(
                    title = "No sessions yet",
                    subtitle = "Tap a routine above to start a session. Finished workouts are " +
                        "listed here with duration and volume.",
                    modifier = Modifier.height(160.dp),
                )
            }
        }
    }

    if (showCustom && active != null) {
        CustomEntryDialog(
            onDismiss = { showCustom = false },
            onAdd = { entry ->
                WellnessStore.addEntry(active.id, entry)
                showCustom = false
            },
        )
    }

    if (confirmFinish && active != null) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish ${active.title}?") },
            text = {
                Text(
                    "${active.entries.size} set(s) logged. The session is saved with its real " +
                        "duration, counted from when you started it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Haptics.success(context)
                    WellnessStore.finishWorkout(active.id)
                    // The finished session is written to Health Connect as exercise.
                    val ended = System.currentTimeMillis()
                    scope.launch {
                        healthWrite = HealthConnectSource.writeExercise(
                            context = context,
                            title = active.title,
                            startMillis = active.startedAt,
                            endMillis = ended,
                        )
                    }
                    confirmFinish = false
                }) { Text("Finish") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text("Keep going") }
            },
        )
    }
}

/**
 * How the Health Connect write ended, in words and colour together.
 *
 * A failed write is the case that matters: it is drawn in [StatusColors.warning]
 * and says in so many words that the session was not written, because a colour
 * on its own is not a message.
 */
@Composable
private fun HealthWriteStatus(written: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (written) Icons.Filled.Check else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (written) MaterialTheme.colorScheme.primary else StatusColors.warning,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = if (written) {
                "Session written to Health Connect"
            } else {
                "Not written to Health Connect — the write did not go through"
            },
            style = ChronosType.label,
            color = if (written) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                StatusColors.warning
            },
        )
    }
}

/**
 * The session in progress. The volume carried so far is the hero number, set in
 * [ChronosType.numeric] over the soft accent wash, with the elapsed clock and
 * the set count as the caption under it.
 */
@Composable
private fun ActiveSessionCard(session: WorkoutSession) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = WellnessLogic.minutesBetween(session.startedAt, now)
    val seconds = ((now - session.startedAt).coerceAtLeast(0) / 1000) % 60
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(MaterialTheme.colorScheme.surface)
            .background(heroSurfaceBrush()),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "Session running",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = session.title,
                style = ChronosType.title,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))
            // The hero: the volume this session has added up to. An em dash
            // when no set carried a weight, rather than a zero that would read
            // as a session with nothing in it.
            Text(
                text = if (session.volumeKg > 0) "${session.volumeKg.toInt()} kg" else DASH,
                style = ChronosType.numeric,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "total volume",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = String.format("%02d:%02d", elapsed, seconds) + " · ${session.entries.size} set(s)",
                style = ChronosType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(session: WorkoutSession) {
    val date = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM"))
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.md,
                end = Spacing.xs,
                top = Spacing.sm,
                bottom = Spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = ChronosType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$date · ${session.minutes} min · ${session.entries.size} set(s)" +
                        if (session.volumeKg > 0) " · ${session.volumeKg.toInt()} kg" else "",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { WellnessStore.discardWorkout(session.id) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete this session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CustomEntryDialog(onDismiss: () -> Unit, onAdd: (WorkoutEntry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("1") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log an exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { input -> weight = input.filter { it.isDigit() || it == '.' }.take(5) },
                    label = { Text("Weight in kg (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (sets.toIntOrNull() ?: 0) > 0,
                onClick = {
                    onAdd(
                        WorkoutEntry(
                            name = name.trim(),
                            sets = sets.toIntOrNull() ?: 1,
                            reps = reps.toIntOrNull() ?: 0,
                            weightKg = weight.toDoubleOrNull() ?: 0.0,
                        ),
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The em dash used when the session has no volume to show. */
private const val DASH = "\u2014"

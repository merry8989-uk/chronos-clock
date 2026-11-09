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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.Haptics
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
 */
@Composable
fun WorkoutSection() {
    val context = LocalContext.current
    val active = WellnessStore.activeWorkout()
    val history = WellnessStore.allWorkouts().filter { it.isFinished }.take(6)
    var showCustom by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (active != null) {
            item { ActiveSessionCard(active) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { confirmFinish = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Finish session", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            Haptics.tick(context)
                            WellnessStore.discardWorkout(active.id)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) { Text("Discard", fontSize = 13.sp) }
                }
            }
            item { WellnessLabel("LOG A SET") }
            val plan = WellnessPlans.plan(active.planId)
            if (plan != null) {
                items(plan.exercises, key = { it.name }) { exercise ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = exercise.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Plan: ${exercise.sets} × ${exercise.reps}" +
                                        if (exercise.cue.isNotBlank()) " · ${exercise.cue}" else "",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    Haptics.tick(context)
                                    WellnessStore.addEntry(
                                        active.id,
                                        WorkoutEntry(exercise.name, 1, exercise.reps),
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Log a set of ${exercise.name}",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showCustom = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) { Text("Log something else", fontSize = 13.sp) }
            }
            item { WellnessLabel("LOGGED") }
            if (active.entries.isEmpty()) {
                item {
                    WellnessNote("No sets logged yet. Tap + beside a movement each time you finish a set.")
                }
            } else {
                items(active.entries.size) { index ->
                    val entry = active.entries[index]
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${entry.name} — ${entry.sets} × ${entry.reps}" +
                                    if (entry.weightKg > 0) " @ ${entry.weightKg} kg" else "",
                                fontSize = 13.sp,
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
        } else {
            item { WellnessLabel("START A ROUTINE") }
            items(WellnessPlans.plans, key = { it.id }) { plan ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable { WellnessStore.startWorkout(plan.id, plan.title) },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = plan.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${plan.focus} · ${plan.minutes} min · ${plan.level}",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Start",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
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
                Spacer(Modifier.height(20.dp))
                EmptyState(
                    title = "No sessions yet",
                    subtitle = "Your finished workouts will be listed here with duration and volume.",
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
                        HealthConnectSource.writeExercise(
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
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Session running",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = session.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = String.format("%02d:%02d", elapsed, seconds) + " · ${session.entries.size} set(s)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            if (session.volumeKg > 0) {
                Text(
                    text = "Volume ${session.volumeKg.toInt()} kg",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$date · ${session.minutes} min · ${session.entries.size} set(s)" +
                        if (session.volumeKg > 0) " · ${session.volumeKg.toInt()} kg" else "",
                    fontSize = 11.sp,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

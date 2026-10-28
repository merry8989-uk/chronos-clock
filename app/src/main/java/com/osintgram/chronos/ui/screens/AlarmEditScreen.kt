package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.alarm.AlarmService
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.RowDivider
import com.osintgram.chronos.ui.components.SectionCard
import com.osintgram.chronos.ui.components.SettingSwitchRow
import com.osintgram.chronos.ui.components.SettingValueRow
import com.osintgram.chronos.ui.components.WheelPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(alarmId: Long?, onClose: () -> Unit) {
    val context = LocalContext.current
    val existing = remember(alarmId) { alarmId?.let { AlarmStore.byId(it) } }
    val use24 = Settings.use24Hour.value

    var hour24 by remember { mutableIntStateOf(existing?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var repeatDays by remember { mutableStateOf(existing?.repeatDays ?: emptySet<Int>()) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var vibrate by remember { mutableStateOf(existing?.vibrate ?: Settings.defaultVibrate.value) }
    var volumeRamp by remember {
        mutableStateOf(existing?.volumeRamp ?: Settings.defaultVolumeRamp.value)
    }
    var snoozeMinutes by remember {
        mutableIntStateOf(existing?.snoozeMinutes ?: Settings.defaultSnooze.value)
    }
    var skipHolidays by remember {
        mutableStateOf(existing?.skipHolidays ?: Settings.defaultSkipHolidays.value)
    }
    var deleteAfterRing by remember {
        mutableStateOf(existing?.deleteAfterRing ?: Settings.defaultDeleteAfterRing.value)
    }
    var soundUri by remember { mutableStateOf(existing?.soundUri) }

    var showRepeatDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showLabelDialog by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun save() {
        val base = existing ?: Alarm(id = AlarmStore.nextId())
        val alarm = base.copy(
            hour = hour24,
            minute = minute,
            label = label.trim(),
            enabled = true,
            repeatDays = repeatDays,
            vibrate = vibrate,
            volumeRamp = volumeRamp,
            snoozeMinutes = snoozeMinutes,
            skipHolidays = skipHolidays && Settings.holidaySkipping.value,
            deleteAfterRing = deleteAfterRing,
            soundUri = soundUri,
        )
        AlarmStore.upsert(alarm)
        AlarmScheduler.schedule(context, alarm)
        onClose()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add alarm" else "Edit alarm") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save alarm")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Wheel picker -------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (use24) {
                    WheelPicker(
                        values = (0..23).map { String.format("%02d", it) },
                        selectedIndex = hour24,
                        onSelected = { hour24 = it },
                    )
                } else {
                    WheelPicker(
                        values = (1..12).map { String.format("%02d", it) },
                        selectedIndex = (if (hour24 % 12 == 0) 12 else hour24 % 12) - 1,
                        onSelected = { index ->
                            val h12 = index + 1
                            hour24 = (h12 % 12) + if (hour24 >= 12) 12 else 0
                        },
                    )
                }
                WheelPicker(
                    values = (0..59).map { String.format("%02d", it) },
                    selectedIndex = minute,
                    onSelected = { minute = it },
                )
                if (!use24) {
                    WheelPicker(
                        values = listOf("am", "pm"),
                        selectedIndex = if (hour24 >= 12) 1 else 0,
                        onSelected = { index ->
                            hour24 = (hour24 % 12) + if (index == 1) 12 else 0
                        },
                        textSize = 22,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SectionCard {
                SettingValueRow(
                    title = "Repeat",
                    value = if (repeatDays.isEmpty()) "Once" else repeatSummary(repeatDays),
                    onClick = { showRepeatDialog = true },
                )
                RowDivider()
                SettingValueRow(
                    title = "Alarm sound",
                    value = AlarmService.ringtoneTitle(context, soundUri),
                    onClick = { showSoundDialog = true },
                )
                RowDivider()
                SettingValueRow(
                    title = "Label",
                    value = label.ifBlank { "None" },
                    onClick = { showLabelDialog = true },
                )
                RowDivider()
                SettingValueRow(
                    title = "Snooze",
                    value = "$snoozeMinutes minutes",
                    onClick = { showSnoozeDialog = true },
                )
            }

            SectionCard {
                SettingSwitchRow(
                    title = "Skip public holidays",
                    subtitle = if (Settings.holidaySkipping.value) {
                        "Skips Indian national holidays and your custom dates"
                    } else {
                        "Turn on holiday skipping in Settings first"
                    },
                    checked = skipHolidays && Settings.holidaySkipping.value,
                    enabled = Settings.holidaySkipping.value,
                    onCheckedChange = { skipHolidays = it },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Vibrate while ringing",
                    checked = vibrate,
                    onCheckedChange = { vibrate = it },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Gradual volume",
                    subtitle = "Ramps the tone up from quiet",
                    checked = volumeRamp,
                    onCheckedChange = { volumeRamp = it },
                )
                if (repeatDays.isEmpty()) {
                    RowDivider()
                    SettingSwitchRow(
                        title = "Delete this alarm after ringing",
                        subtitle = "Otherwise it stays in the list, switched off",
                        checked = deleteAfterRing,
                        onCheckedChange = { deleteAfterRing = it },
                    )
                }
            }

            if (existing != null) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("  Delete alarm", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showRepeatDialog) {
        RepeatDialog(
            selected = repeatDays,
            onDismiss = { showRepeatDialog = false },
            onConfirm = {
                repeatDays = it
                showRepeatDialog = false
            },
        )
    }

    if (showSoundDialog) {
        SoundDialog(
            selectedUri = soundUri,
            onDismiss = { showSoundDialog = false },
            onPick = {
                soundUri = it
                showSoundDialog = false
            },
        )
    }

    if (showLabelDialog) {
        LabelDialog(
            initial = label,
            onDismiss = { showLabelDialog = false },
            onConfirm = {
                label = it
                showLabelDialog = false
            },
        )
    }

    if (showSnoozeDialog) {
        ChoiceDialog(
            title = "Snooze length",
            options = listOf(5, 10, 15, 20, 30).map { "$it minutes" to it },
            selected = "$snoozeMinutes minutes",
            onDismiss = { showSnoozeDialog = false },
            onPick = {
                snoozeMinutes = it
                showSnoozeDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this alarm?") },
            text = { Text("It will be removed from the list and will not ring again.") },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.cancel(context, existing?.id ?: -1L)
                    existing?.let { AlarmStore.delete(it.id) }
                    showDeleteDialog = false
                    onClose()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private fun repeatSummary(days: Set<Int>): String {
    if (days.size == 7) return "Every day"
    if (days == setOf(1, 2, 3, 4, 5)) return "Mon to Fri"
    if (days == setOf(6, 7)) return "Weekends"
    return days.sorted().joinToString(", ") { Alarm.dayShort(it) }
}

@Composable
private fun RepeatDialog(
    selected: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
) {
    var working by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Once", "Every day", "Weekdays", "Weekends").forEach { preset ->
                        Pill(
                            text = preset,
                            selected = false,
                            onClick = {
                                working = when (preset) {
                                    "Once" -> emptySet()
                                    "Every day" -> (1..7).toSet()
                                    "Weekdays" -> setOf(1, 2, 3, 4, 5)
                                    else -> setOf(6, 7)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { day ->
                        Pill(
                            text = Alarm.dayShort(day),
                            selected = working.contains(day),
                            onClick = {
                                working = if (working.contains(day)) working - day else working + day
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (working.isEmpty()) "Rings once, then switches off"
                    else repeatSummary(working),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(working) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SoundDialog(
    selectedUri: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val context = LocalContext.current
    val options = remember { AlarmService.systemAlarmRingtones(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarm sound") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Device default",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = if (selectedUri == null) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    TextButton(onClick = { onPick(null) }) { Text("Use") }
                }
                options.forEach { (title, uri) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = if (selectedUri == uri) FontWeight.SemiBold
                            else FontWeight.Normal,
                        )
                        TextButton(onClick = { onPick(uri) }) { Text("Use") }
                    }
                }
                if (options.isEmpty()) {
                    Text(
                        text = "No alarm tones found on this device. The default alarm sound will be used.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun LabelDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Label") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 40) text = it },
                singleLine = true,
                placeholder = { Text("Wake up, Gym, Stand-up…") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (text, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = if (text == selected) FontWeight.SemiBold
                            else FontWeight.Normal,
                        )
                        TextButton(onClick = { onPick(value) }) { Text("Use") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

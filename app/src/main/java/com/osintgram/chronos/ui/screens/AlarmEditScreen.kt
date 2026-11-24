package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.alarm.AlarmService
import com.osintgram.chronos.data.Alarm
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.MissionSpec
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.Tones
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.RowDivider
import com.osintgram.chronos.ui.components.SettingSwitchRow
import com.osintgram.chronos.ui.components.SettingValueRow
import com.osintgram.chronos.ui.components.WheelPicker
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing

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
    var missions by remember { mutableStateOf(existing?.missions ?: emptyList<MissionSpec>()) }
    var wallpaperPaths by remember {
        mutableStateOf(existing?.wallpaperPaths ?: emptyList<String>())
    }
    var randomWallpaper by remember { mutableStateOf(existing?.randomWallpaper ?: false) }
    var showMissionDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }

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
            missions = missions,
            wallpaperPaths = wallpaperPaths,
            randomWallpaper = randomWallpaper,
        )
        AlarmStore.upsert(alarm)
        AlarmScheduler.schedule(context, alarm)
        onClose()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existing == null) "Add alarm" else "Edit alarm",
                        style = ChronosType.title,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                // Cancel stays a quiet icon: saving is the filled button below.
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
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
            // ---- Time: the reading leads, the wheels below stay the control --
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeDisplay(hour24, minute, use24),
                        style = ChronosType.display,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (!use24) {
                        Text(
                            text = meridiemLabel(hour24),
                            style = ChronosType.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            }

            // ---- Form, grouped into labelled sections ------------------------
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                EditSection(title = "SCHEDULE") {
                    SettingValueRow(
                        title = "Repeat",
                        value = if (repeatDays.isEmpty()) "Once" else repeatSummary(repeatDays),
                        onClick = { showRepeatDialog = true },
                    )
                    RowDivider()
                    SettingValueRow(
                        title = "Alarm sound",
                        value = soundLabel(context, soundUri),
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

                EditSection(title = "WAKE-UP") {
                    SettingValueRow(
                        title = "Missions",
                        subtitle = if (missions.isEmpty()) {
                            "No challenge — one tap dismisses the alarm"
                        } else {
                            "Must be completed before you can snooze or dismiss"
                        },
                        value = if (missions.isEmpty()) "None" else "${missions.size} set",
                        onClick = { showMissionDialog = true },
                    )
                    RowDivider()
                    SettingValueRow(
                        title = "Wallpaper",
                        subtitle = "Picture behind the clock while this alarm rings",
                        value = when {
                            wallpaperPaths.isEmpty() -> "None"
                            randomWallpaper -> "Random (${wallpaperPaths.size})"
                            else -> "${wallpaperPaths.size} image(s)"
                        },
                        onClick = { showWallpaperDialog = true },
                    )
                }

                EditSection(title = "RINGING") {
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
            }

            Spacer(Modifier.height(Spacing.xl))

            // ---- The one dominant action: save ------------------------------
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .semantics { contentDescription = "Save alarm" },
                shape = RoundedCornerShape(Radii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(text = "Save", style = ChronosType.title)
            }

            // ---- Quiet, destructive, and last ---------------------------------
            if (existing != null) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("  Delete alarm", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
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
        SoundPickerDialog(
            current = soundUri,
            onDismiss = { showSoundDialog = false },
            onPick = {
                soundUri = it
                showSoundDialog = false
            },
        )
    }

    if (showMissionDialog) {
        MissionDialog(
            current = missions,
            onDismiss = { showMissionDialog = false },
            onSave = {
                missions = it
                showMissionDialog = false
            },
        )
    }

    if (showWallpaperDialog) {
        WallpaperDialog(
            currentPaths = wallpaperPaths,
            currentRandom = randomWallpaper,
            onDismiss = { showWallpaperDialog = false },
            onSave = { paths, random ->
                wallpaperPaths = paths
                randomWallpaper = random
                showWallpaperDialog = false
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

/**
 * One labelled group of the form: a small heading, then the rows on a card whose
 * corners come from [Radii.md] rather than from a number picked here.
 */
@Composable
private fun EditSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.sm),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radii.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** The hour and minute the wheels are sitting on, in the user's clock format. */
private fun timeDisplay(hour24: Int, minute: Int, use24: Boolean): String =
    java.time.LocalTime.of(hour24.coerceIn(0, 23), minute.coerceIn(0, 59)).format(
        java.time.format.DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "hh:mm"),
    )

private fun meridiemLabel(hour24: Int): String = if (hour24 < 12) "am" else "pm"

private fun soundLabel(context: android.content.Context, uri: String?): String {
    Tones.toneForUri(uri)?.let { return it.title }
    return AlarmService.ringtoneTitle(context, uri)
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = if (working.isEmpty()) "Rings once, then switches off"
                    else repeatSummary(working),
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(working) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.weight(1f),
                            style = ChronosType.body,
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

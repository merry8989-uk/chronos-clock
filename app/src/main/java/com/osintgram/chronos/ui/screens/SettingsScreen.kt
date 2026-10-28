package com.osintgram.chronos.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osintgram.chronos.BuildConfig
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.Backup
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.diagnostics.LogExporter
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.RowDivider
import com.osintgram.chronos.ui.components.SectionCard
import com.osintgram.chronos.ui.components.SettingActionRow
import com.osintgram.chronos.ui.components.SettingSliderRow
import com.osintgram.chronos.ui.components.SettingSwitchRow
import com.osintgram.chronos.ui.components.SettingValueRow
import com.osintgram.chronos.ui.theme.AccentOptions
import com.osintgram.chronos.updater.UpdateUiState
import com.osintgram.chronos.updater.UpdateViewModel
import java.time.LocalDate

private const val INSTAGRAM_URL = "https://instagram.com/osintgram_io"
private const val SOURCE_URL = "https://github.com/merry8989-uk/chronos-clock"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val updateVm: UpdateViewModel = viewModel()

    var showHolidays by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showSnoozeLimitDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val file = Backup.export(context, BuildConfig.VERSION_NAME)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(file.readText().toByteArray())
                }
                status = "Backup saved"
            }.onFailure { status = "Backup failed: ${it.message}" }
        }
    }

    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()
            if (text == null) {
                status = "Could not read that file"
            } else {
                val summary = Backup.restore(context, text)
                status = if (summary == null) "Restore failed" else "Restored: $summary"
                AlarmScheduler.rescheduleAll(context)
            }
        }
    }

    if (showHolidays) {
        HolidaysScreen(onBack = { showHolidays = false })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            status?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    onClick = { status = null },
                ) {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // ---- Appearance ---------------------------------------------------
            SectionCard(title = "APPEARANCE") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                            .forEach { (key, label) ->
                                Pill(
                                    text = label,
                                    selected = Settings.themeMode.value == key,
                                    onClick = { Settings.themeMode.set(key) },
                                )
                            }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Accent colour", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentOptions.forEach { (key, _, color) ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(color, CircleShape)
                                    .clickable { Settings.accent.set(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (Settings.accent.value == key) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                RowDivider()
                SettingSliderRow(
                    title = "Text size",
                    valueLabel = "${(Settings.textScale.value * 100).toInt()}%",
                    value = Settings.textScale.value,
                    valueRange = 0.85f..1.3f,
                    steps = 8,
                    onValueChange = { Settings.textScale.set((it * 20).toInt() / 20f) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "24-hour time",
                    checked = Settings.use24Hour.value,
                    onCheckedChange = { Settings.use24Hour.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Pure black in dark mode",
                    subtitle = "Saves power on OLED screens",
                    checked = Settings.amoledBlack.value,
                    onCheckedChange = { Settings.amoledBlack.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Show seconds on the world clock",
                    checked = Settings.showSeconds.value,
                    onCheckedChange = { Settings.showSeconds.set(it) },
                )
            }

            // ---- Alarms -------------------------------------------------------
            SectionCard(title = "ALARMS") {
                SettingValueRow(
                    title = "Default snooze",
                    value = "${Settings.defaultSnooze.value} minutes",
                    onClick = { showSnoozeDialog = true },
                )
                RowDivider()
                SettingValueRow(
                    title = "Snooze limit",
                    subtitle = "How many times one alarm can be snoozed",
                    value = "${Settings.snoozeLimit.value}×",
                    onClick = { showSnoozeLimitDialog = true },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Vibrate new alarms",
                    checked = Settings.defaultVibrate.value,
                    onCheckedChange = { Settings.defaultVibrate.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Gradual volume on new alarms",
                    checked = Settings.defaultVolumeRamp.value,
                    onCheckedChange = { Settings.defaultVolumeRamp.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Show countdown on alarm cards",
                    subtitle = "\"Alarm in 33 minutes\" under each alarm",
                    checked = Settings.showCountdown.value,
                    onCheckedChange = { Settings.showCountdown.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Full-screen alarm",
                    subtitle = "Wake the screen and show the ring screen over the lock screen",
                    checked = Settings.fullScreenAlarm.value,
                    onCheckedChange = { Settings.fullScreenAlarm.set(it) },
                )
                RowDivider()
                SettingSliderRow(
                    title = "Alarm volume",
                    valueLabel = "${(Settings.alarmVolume.value * 100).toInt()}%",
                    value = Settings.alarmVolume.value,
                    valueRange = 0.1f..1f,
                    steps = 8,
                    onValueChange = { Settings.alarmVolume.set((it * 10).toInt() / 10f) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Skip public holidays",
                    subtitle = "Master switch for holiday-aware alarms",
                    checked = Settings.holidaySkipping.value,
                    onCheckedChange = { Settings.holidaySkipping.set(it) },
                )
                RowDivider()
                SettingValueRow(
                    title = "Holiday dates",
                    subtitle = "Indian national holidays and your own dates",
                    value = "${HolidayStore.customDates().size + HolidayStore.builtIn.size}",
                    onClick = { showHolidays = true },
                )
                RowDivider()
                SettingActionRow(
                    title = "Exact alarm permission",
                    subtitle = if (AlarmScheduler.canScheduleExact(context)) {
                        "Granted — alarms fire on the exact minute"
                    } else {
                        "Not granted — tap to allow precise alarms"
                    },
                    onClick = {
                        if (!AlarmScheduler.canScheduleExact(context)) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure {
                                status = "Open Settings > Apps > Chronos > Alarms & reminders"
                            }
                        } else {
                            status = "Exact alarms are already allowed"
                        }
                    },
                )
            }

            // ---- Timer --------------------------------------------------------
            SectionCard(title = "TIMER") {
                SettingSwitchRow(
                    title = "Timer running sounds",
                    checked = Settings.timerRunningSound.value,
                    onCheckedChange = { Settings.timerRunningSound.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Sound when the timer ends",
                    checked = Settings.timerDoneSound.value,
                    onCheckedChange = { Settings.timerDoneSound.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Vibrate when the timer ends",
                    checked = Settings.timerVibrate.value,
                    onCheckedChange = { Settings.timerVibrate.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Keep the screen on while a timer runs",
                    checked = Settings.keepScreenOnTimer.value,
                    onCheckedChange = { Settings.keepScreenOnTimer.set(it) },
                )
            }

            // ---- Stopwatch ----------------------------------------------------
            SectionCard(title = "STOPWATCH") {
                SettingSwitchRow(
                    title = "Stopwatch running sounds",
                    checked = Settings.stopwatchSound.value,
                    onCheckedChange = { Settings.stopwatchSound.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Haptic feedback",
                    checked = Settings.haptics.value,
                    onCheckedChange = { Settings.haptics.set(it) },
                )
            }

            // ---- General ------------------------------------------------------
            SectionCard(title = "GENERAL") {
                SettingSwitchRow(
                    title = "Keep the screen on",
                    subtitle = "Applies to the whole app",
                    checked = Settings.keepScreenOn.value,
                    onCheckedChange = { Settings.keepScreenOn.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Confirm before deleting an alarm",
                    checked = Settings.confirmDelete.value,
                    onCheckedChange = { Settings.confirmDelete.set(it) },
                )
            }

            // ---- Data ---------------------------------------------------------
            SectionCard(title = "BACKUP & DATA") {
                SettingActionRow(
                    title = "Back up alarms and settings",
                    subtitle = "Saves a JSON file you choose",
                    onClick = { exportBackup.launch("chronos-backup.json") },
                )
                RowDivider()
                SettingActionRow(
                    title = "Restore from a backup",
                    subtitle = "Replaces alarms, settings, cities and holidays",
                    onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
                RowDivider()
                SettingActionRow(
                    title = "Reset everything to defaults",
                    subtitle = "Clears every setting and deletes all alarms",
                    onClick = { showResetDialog = true },
                )
            }

            // ---- Diagnostics ---------------------------------------------------
            SectionCard(title = "DIAGNOSTICS") {
                SettingSwitchRow(
                    title = "Collect logs",
                    subtitle = "On by default so problems can be diagnosed without reproducing them",
                    checked = Settings.diagnosticsEnabled.value,
                    onCheckedChange = {
                        Settings.diagnosticsEnabled.set(it)
                        DiagnosticsLogger.setEnabled(it)
                    },
                )
                RowDivider()
                SettingValueRow(
                    title = "Collected logs",
                    value = "${DiagnosticsLogger.lineCount()} lines",
                    subtitle = "${DiagnosticsLogger.sizeBytes() / 1024} KB stored",
                    onClick = { status = "Logs are stored on this device only" },
                )
                RowDivider()
                SettingActionRow(
                    title = "Share logs",
                    subtitle = "Send the log file through any app",
                    onClick = {
                        val file = LogExporter.export(
                            context,
                            LogExporter.Format.TXT,
                            "Chronos",
                            BuildConfig.VERSION_NAME,
                        )
                        LogExporter.share(context, file, "text/plain")
                    },
                )
                RowDivider()
                SettingActionRow(
                    title = "Export logs",
                    subtitle = "Choose .txt, .json, .md or .zip",
                    onClick = { showExportDialog = true },
                )
                RowDivider()
                SettingActionRow(
                    title = "Clear logs",
                    onClick = {
                        DiagnosticsLogger.clear()
                        status = "Logs cleared"
                    },
                )
            }

            // ---- About ----------------------------------------------------------
            SectionCard(title = "ABOUT") {
                SettingValueRow(
                    title = "Version",
                    value = BuildConfig.VERSION_NAME,
                    subtitle = "Chronos for Android",
                    onClick = { updateVm.check(BuildConfig.VERSION_NAME) },
                )
                RowDivider()
                when (val state = updateVm.state) {
                    is UpdateUiState.Available -> SettingActionRow(
                        title = "Update to ${state.info.latestVersion}",
                        subtitle = "Download and install without a browser",
                        onClick = { updateVm.install(state.info) },
                    )
                    is UpdateUiState.Downloading -> SettingActionRow(
                        title = "Downloading update…",
                        subtitle = "The installer opens automatically",
                        onClick = { },
                    )
                    is UpdateUiState.Checking -> SettingActionRow(
                        title = "Checking for updates…",
                        onClick = { },
                    )
                    is UpdateUiState.UpToDate -> SettingActionRow(
                        title = "You're on the latest version",
                        subtitle = "Tap to check again",
                        onClick = { updateVm.check(BuildConfig.VERSION_NAME) },
                    )
                    is UpdateUiState.Failed -> SettingActionRow(
                        title = "Update check failed",
                        subtitle = state.message,
                        onClick = { updateVm.check(BuildConfig.VERSION_NAME) },
                    )
                    UpdateUiState.Idle -> SettingActionRow(
                        title = "Check for updates",
                        subtitle = "Installs straight over the current app",
                        onClick = { updateVm.check(BuildConfig.VERSION_NAME) },
                    )
                }
                RowDivider()
                SettingActionRow(
                    title = "Source code",
                    subtitle = "github.com/merry8989-uk/chronos-clock",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                        }
                    },
                )
            }

            // ---- Contact footer (Rule 4) ---------------------------------------
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Chronos ${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "@osintgram_io",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(INSTAGRAM_URL)),
                                )
                            }
                        }
                        .padding(8.dp),
                )
                Text(
                    text = "Suggestions, feedback and feature requests — tap to open Instagram",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }
    }

    if (showSnoozeDialog) {
        OptionDialog(
            title = "Default snooze",
            options = listOf(5, 10, 15, 20, 30),
            selected = Settings.defaultSnooze.value,
            label = { "$it minutes" },
            onDismiss = { showSnoozeDialog = false },
            onPick = {
                Settings.defaultSnooze.set(it)
                showSnoozeDialog = false
            },
        )
    }

    if (showSnoozeLimitDialog) {
        OptionDialog(
            title = "Snooze limit",
            options = listOf(1, 2, 3, 5, 10),
            selected = Settings.snoozeLimit.value,
            label = { "$it time(s)" },
            onDismiss = { showSnoozeLimitDialog = false },
            onPick = {
                Settings.snoozeLimit.set(it)
                showSnoozeLimitDialog = false
            },
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export logs as") },
            text = {
                Column {
                    LogExporter.Format.values().forEach { format ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ".${format.ext}",
                                modifier = Modifier.weight(1f),
                                fontSize = 16.sp,
                            )
                            TextButton(onClick = {
                                val file = LogExporter.export(
                                    context,
                                    format,
                                    "Chronos",
                                    BuildConfig.VERSION_NAME,
                                )
                                LogExporter.share(context, file, format.mime)
                                showExportDialog = false
                            }) { Text("Export") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Close") }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset everything?") },
            text = {
                Text(
                    "Every setting returns to its default and all alarms are deleted. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AlarmStore.all().forEach { AlarmScheduler.cancel(context, it.id) }
                    AlarmStore.replaceAll(emptyList())
                    Settings.resetAll()
                    DiagnosticsLogger.setEnabled(Settings.diagnosticsEnabled.value)
                    showResetDialog = false
                    status = "Everything reset to defaults"
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label(option),
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = if (option == selected) FontWeight.SemiBold
                            else FontWeight.Normal,
                        )
                        if (option == selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        TextButton(onClick = { onPick(option) }) { Text("Use") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidaysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val custom = HolidayStore.customDates()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Holiday dates") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            Text(
                text = "Alarms with \"Skip public holidays\" switched on will not ring on these " +
                    "dates. Fixed national holidays are built in; add the moving festivals " +
                    "yourself so the app never guesses a date.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )

            SectionCard(title = "BUILT IN (INDIA)") {
                HolidayStore.builtIn.forEachIndexed { index, (name, md) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${md.dayOfMonth}/${md.monthValue}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != HolidayStore.builtIn.lastIndex) HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            SectionCard(title = "YOUR DATES") {
                if (custom.isEmpty()) {
                    Text(
                        text = "No custom dates yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    custom.forEachIndexed { index, iso ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = iso,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(onClick = {
                                HolidayStore.removeCustom(iso)
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index != custom.lastIndex) HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
                RowDivider()
                SettingActionRow(
                    title = "Add a date",
                    onClick = {
                        val today = LocalDate.now()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                HolidayStore.addCustom(LocalDate.of(year, month + 1, day))
                            },
                            today.year,
                            today.monthValue - 1,
                            today.dayOfMonth,
                        ).show()
                    },
                )
            }
        }
    }
}

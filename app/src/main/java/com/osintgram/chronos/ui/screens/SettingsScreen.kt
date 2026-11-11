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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osintgram.chronos.BuildConfig
import kotlinx.coroutines.launch
import com.osintgram.chronos.alarm.AlarmScheduler
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.Backup
import com.osintgram.chronos.data.HolidayStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.Tones
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.diagnostics.LogExporter
import com.osintgram.chronos.ui.components.AnalogClock
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.SegmentedControl
import com.osintgram.chronos.ui.components.RowDivider
import com.osintgram.chronos.ui.components.SectionCard
import com.osintgram.chronos.ui.components.SettingActionRow
import com.osintgram.chronos.ui.components.SettingSliderRow
import com.osintgram.chronos.ui.components.SettingSwitchRow
import com.osintgram.chronos.ui.components.SettingValueRow
import com.osintgram.chronos.ui.theme.AccentOptions
import com.osintgram.chronos.ui.theme.pressable
import com.osintgram.chronos.updater.UpdateUiState
import com.osintgram.chronos.widget.WidgetPinner
import com.osintgram.chronos.updater.UpdateViewModel
import java.time.LocalDate

private const val INSTAGRAM_URL = "https://instagram.com/osintgram_io"
private const val SOURCE_URL = "https://github.com/merry8989-uk/chronos-clock"

/** Section keys the caller can ask the screen to jump to. */
const val SCROLL_ABOUT = "about"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, scrollTo: String? = null) {
    val context = LocalContext.current
    val updateVm: UpdateViewModel = viewModel()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var aboutOffset by remember { mutableIntStateOf(0) }
    // "About & updates" in the ⋮ menu opens this screen at the About card
    // instead of at the top of a long settings list.
    LaunchedEffect(scrollTo, aboutOffset) {
        if (scrollTo == SCROLL_ABOUT && aboutOffset > 0) {
            scrollState.animateScrollTo(aboutOffset)
        }
    }

    var showHolidays by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showSnoozeLimitDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showWidgetInfo by remember { mutableStateOf(false) }
    var showStepDialog by remember { mutableStateOf(false) }
    var showStepGoalDialog by remember { mutableStateOf(false) }
    var showWaterGoalDialog by remember { mutableStateOf(false) }
    var showSleepGoalDialog by remember { mutableStateOf(false) }
    var showMeditationDialog by remember { mutableStateOf(false) }
    var showBellToneDialog by remember { mutableStateOf(false) }

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
                .verticalScroll(scrollState)
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
                    SegmentedControl(
                        options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                        selected = Settings.themeMode.value,
                        onSelect = { Settings.themeMode.set(it) },
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Accent colour", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentOptions.forEach { (key, name, color) ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(color, CircleShape)
                                    .pressable(scale = 0.88f) { Settings.accent.set(key) }
                                    .semantics {
                                        contentDescription = "Accent colour $name"
                                        role = Role.RadioButton
                                    },
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

            // ---- Logo & live clock ---------------------------------------------
            SectionCard(title = "LOGO & CLOCK") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnalogClock(
                        modifier = Modifier.size(132.dp),
                        showSecondHand = Settings.clockSecondHand.value,
                        smoothSweep = Settings.clockSmooth.value,
                        rimColor = MaterialTheme.colorScheme.primary,
                        tickColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        hourHandColor = MaterialTheme.colorScheme.onSurface,
                        minuteHandColor = MaterialTheme.colorScheme.onSurface,
                        secondHandColor = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "The app logo is a working clock — hour, minute and second " +
                            "hands run off the device time.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Logo style",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf("analog" to "Analog clock", "digital" to "Digital"),
                        selected = Settings.logoStyle.value,
                        onSelect = { Settings.logoStyle.set(it) },
                    )
                }
                RowDivider()
                SettingSwitchRow(
                    title = "Second hand",
                    subtitle = "Show the sweeping second hand in the logo",
                    checked = Settings.clockSecondHand.value,
                    onCheckedChange = { Settings.clockSecondHand.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Smooth sweep",
                    subtitle = "Glide instead of ticking once per second",
                    enabled = Settings.clockSecondHand.value,
                    checked = Settings.clockSmooth.value,
                    onCheckedChange = { Settings.clockSmooth.set(it) },
                )
            }


            // ---- Widget style --------------------------------------------------
            SectionCard(title = "WIDGET STYLE") {
                Column(modifier = Modifier.padding(16.dp)) {
                    WidgetStylePreview()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Background",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "transparent" to "None",
                            "half" to "Half",
                            "white" to "White",
                            "blue" to "Blue",
                        ).forEach { (key, label) ->
                            Pill(
                                text = label,
                                selected = Settings.widgetBgStyle.value == key,
                                onClick = {
                                    Settings.widgetBgStyle.set(key)
                                    if (key == "white") {
                                        Settings.widgetTextColor.set(0xFF10141B.toInt())
                                    }
                                    refreshWidgets(context)
                                },
                            )
                        }
                        Pill(
                            text = "Custom",
                            selected = Settings.widgetBgStyle.value == "custom",
                            onClick = {
                                Settings.widgetBgStyle.set("custom")
                                refreshWidgets(context)
                            },
                        )
                    }
                    if (Settings.widgetBgStyle.value == "custom") {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Background colour",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            WidgetPalette.forEach { (name, argb) ->
                                ColourDot(
                                    argb = argb,
                                    selected = Settings.widgetBgColor.value == argb,
                                    label = "Background colour $name",
                                    onClick = {
                                        Settings.widgetBgColor.set(argb)
                                        refreshWidgets(context)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Text colour",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextPalette.forEach { (name, argb) ->
                            ColourDot(
                                argb = argb,
                                selected = Settings.widgetTextColor.value == argb,
                                label = "Text colour $name",
                                onClick = {
                                    Settings.widgetTextColor.set(argb)
                                    refreshWidgets(context)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Time format",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "12s" to "12h + sec",
                            "12" to "12h",
                            "24s" to "24h + sec",
                            "24" to "24h",
                        ).forEach { (key, label) ->
                            Pill(
                                text = label,
                                selected = Settings.widgetClockFormat.value == key,
                                onClick = {
                                    Settings.widgetClockFormat.set(key)
                                    refreshWidgets(context)
                                },
                            )
                        }
                    }
                }
                RowDivider()
                SettingSliderRow(
                    title = "Text size",
                    valueLabel = "${Settings.widgetTextSize.value}sp",
                    value = Settings.widgetTextSize.value.coerceIn(12, 48).toFloat(),
                    valueRange = 12f..48f,
                    steps = 11,
                    onValueChange = {
                        Settings.widgetTextSize.set(it.toInt())
                        refreshWidgets(context)
                    },
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
                RowDivider()
                SettingValueRow(
                    title = "Adjust step",
                    subtitle = "How much the − and + buttons change the timer",
                    value = "${Settings.timerStepMinutes.value} min",
                    onClick = { showStepDialog = true },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Floating timer bubble",
                    subtitle = "A small draggable countdown over other apps while a timer runs",
                    checked = Settings.timerBubble.value,
                    onCheckedChange = { Settings.timerBubble.set(it) },
                )
                if (Settings.timerBubble.value && !overlayAllowed(context)) {
                    RowDivider()
                    SettingActionRow(
                        title = "Allow display over other apps",
                        subtitle = "Required for the bubble — tap to grant it",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure {
                                status = "Open Settings > Apps > Chronos > Display over other apps"
                            }
                        },
                    )
                }
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

            // ---- Wellness ------------------------------------------------------
            SectionCard(title = "WELLNESS") {
                SettingValueRow(
                    title = "Step goal",
                    subtitle = "The ring on the Today card",
                    value = "${Settings.stepGoal.value} steps",
                    onClick = { showStepGoalDialog = true },
                )
                RowDivider()
                SettingValueRow(
                    title = "Water goal",
                    subtitle = "Daily target for the water card",
                    value = com.osintgram.chronos.wellness.WellnessLogic
                        .formatLitres(Settings.waterGoalMl.value),
                    onClick = { showWaterGoalDialog = true },
                )
                RowDivider()
                SettingValueRow(
                    title = "Sleep goal",
                    subtitle = "Per night, shown against what you enter",
                    value = com.osintgram.chronos.wellness.WellnessLogic
                        .formatMinutes(Settings.sleepGoalMinutes.value),
                    onClick = { showSleepGoalDialog = true },
                )
                RowDivider()
                SettingValueRow(
                    title = "Default meditation length",
                    subtitle = "What the Breathe section starts at",
                    value = "${Settings.meditationMinutes.value} min",
                    onClick = { showMeditationDialog = true },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Session bell",
                    subtitle = "A tone when a breathing session completes",
                    checked = Settings.wellnessBell.value,
                    onCheckedChange = { Settings.wellnessBell.set(it) },
                )
                RowDivider()
                SettingValueRow(
                    title = "Bell tone",
                    subtitle = "From the tones that ship with the app",
                    value = Tones.byId(Settings.wellnessBellTone.value)?.title ?: "Classic Bell",
                    onClick = { showBellToneDialog = true },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Health Connect sync",
                    subtitle = "Read steps, water and sleep that other apps recorded",
                    checked = Settings.healthConnectSync.value,
                    onCheckedChange = {
                        Settings.healthConnectSync.set(it)
                        com.osintgram.chronos.wellness.WellnessSync.scheduleIfEnabled(context)
                    },
                )
            }

            // ---- Bedtime mode --------------------------------------------------
            SectionCard(title = "BEDTIME MODE") {
                SettingSwitchRow(
                    title = "Bedtime mode",
                    subtitle = "A wind-down reminder at night, with optional Do Not Disturb",
                    checked = Settings.bedtimeEnabled.value,
                    onCheckedChange = {
                        Settings.bedtimeEnabled.set(it)
                        com.osintgram.chronos.bedtime.BedtimeScheduler.reschedule(context)
                    },
                )
                RowDivider()
                SettingValueRow(
                    title = "Bedtime",
                    subtitle = "When the wind-down reminder fires",
                    value = minutesLabel(Settings.bedtimeStart.value),
                    onClick = {
                        pickTime(context, Settings.bedtimeStart.value) {
                            Settings.bedtimeStart.set(it)
                            com.osintgram.chronos.bedtime.BedtimeScheduler.reschedule(context)
                        }
                    },
                )
                RowDivider()
                SettingValueRow(
                    title = "Wake-up",
                    subtitle = "When bedtime mode ends",
                    value = minutesLabel(Settings.bedtimeEnd.value),
                    onClick = {
                        pickTime(context, Settings.bedtimeEnd.value) {
                            Settings.bedtimeEnd.set(it)
                            com.osintgram.chronos.bedtime.BedtimeScheduler.reschedule(context)
                        }
                    },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Reminder notification",
                    subtitle = "A quiet nudge at bedtime",
                    checked = Settings.bedtimeNotify.value,
                    onCheckedChange = { Settings.bedtimeNotify.set(it) },
                )
                RowDivider()
                SettingSwitchRow(
                    title = "Do Not Disturb during bedtime",
                    subtitle = if (com.osintgram.chronos.bedtime.BedtimeDnd.canControl(context)) {
                        "Switched on at bedtime and off at wake-up. Alarms still ring"
                    } else {
                        "Needs notification-policy access — tap below to grant it"
                    },
                    checked = Settings.bedtimeDnd.value,
                    onCheckedChange = { Settings.bedtimeDnd.set(it) },
                )
                if (!com.osintgram.chronos.bedtime.BedtimeDnd.canControl(context)) {
                    RowDivider()
                    SettingActionRow(
                        title = "Allow Do Not Disturb control",
                        subtitle = "Opens the system setting",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings
                                            .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                                    ),
                                )
                            }.onFailure {
                                status = "Open Settings > Apps > Special access > Do Not Disturb"
                            }
                        },
                    )
                }
                RowDivider()
                SettingActionRow(
                    title = "Open the bedtime screen now",
                    subtitle = "Dim clock with your next alarm on show",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    context,
                                    com.osintgram.chronos.bedtime.BedtimeActivity::class.java,
                                ),
                            )
                        }
                    },
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
                    title = "Keep running in background",
                    subtitle = "A low-priority notification stays; steps and sync keep working " +
                        "when the app is closed. Exclude Chronos from battery optimisation too",
                    checked = Settings.keepAlive.value,
                    onCheckedChange = {
                        Settings.keepAlive.set(it)
                        com.osintgram.chronos.keepalive.KeepAliveService.sync(context)
                        com.osintgram.chronos.diagnostics.DiagnosticsLogger.log(
                            "Alive",
                            "background service setting = $it",
                        )
                    },
                )
                RowDivider()
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
                SettingSwitchRow(
                    title = "Capture platform log",
                    subtitle = "Pulls this app's own logcat buffer in, for crashes the app never saw",
                    checked = Settings.logcatCapture.value,
                    onCheckedChange = { Settings.logcatCapture.set(it) },
                )
                RowDivider()
                SettingActionRow(
                    title = "Capture now",
                    subtitle = "Reads the last few hundred logcat lines into the log",
                    onClick = {
                        scope.launch {
                            val lines = com.osintgram.chronos.diagnostics.LogcatCollector.capture(context)
                            status = if (lines > 0) "Captured $lines logcat line(s)" else "logcat returned nothing"
                        }
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
            SectionCard(
                title = "ABOUT",
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    aboutOffset = coordinates.positionInParent().y.toInt()
                },
            ) {
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
                    is UpdateUiState.Downloading -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Downloading update…",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (state.total > 0L) "${(state.percent * 100).toInt()}%"
                                else state.downloadedLabel,
                                fontSize = 13.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (state.total > 0L) {
                            LinearProgressIndicator(
                                progress = { state.percent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (state.total > 0L) {
                                "${state.downloadedLabel} of ${state.totalLabel} · the installer opens automatically"
                            } else {
                                "Starting the download · the installer opens automatically"
                            },
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        title = "Update failed",
                        subtitle = state.message,
                        onClick = { updateVm.check(BuildConfig.VERSION_NAME) },
                    )
                    UpdateUiState.Idle -> SettingActionRow(
                        title = "Check for updates",
                        subtitle = "Installs straight over the current app",
                        onClick = { updateVm.check(BuildConfig.VERSION_NAME) },
                    )
                }
                if (!updateVm.canInstall()) {
                    RowDivider()
                    SettingActionRow(
                        title = "Allow app installs",
                        subtitle = "Android needs this before Chronos can install an update",
                        onClick = { updateVm.openInstallPermissionSettings() },
                    )
                }
                RowDivider()
                SettingSwitchRow(
                    title = "Update over Wi-Fi only",
                    subtitle = "Holds the download until you are on Wi-Fi",
                    checked = Settings.updateWifiOnly.value,
                    onCheckedChange = { Settings.updateWifiOnly.set(it) },
                )
                RowDivider()
                SettingActionRow(
                    title = "Home-screen widgets",
                    subtitle = "Clock, next alarm, alarm list, timer, stopwatch, holidays, " +
                        "presets, screen time",
                    onClick = { showWidgetInfo = true },
                )
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

    if (showStepDialog) {
        AlertDialog(
            onDismissRequest = { showStepDialog = false },
            title = { Text("Adjust step") },
            text = {
                Column {
                    Text(
                        text = "Each press of − or + changes the timer by this much.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(1, 2, 5, 10, 15).forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$option minute${if (option == 1) "" else "s"}",
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                fontWeight = if (Settings.timerStepMinutes.value == option) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                            TextButton(onClick = {
                                Settings.timerStepMinutes.set(option)
                                showStepDialog = false
                            }) { Text("Use") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStepDialog = false }) { Text("Close") }
            },
        )
    }

    if (showWidgetInfo) {
        AlertDialog(
            onDismissRequest = { showWidgetInfo = false },
            title = { Text("Home-screen widgets") },
            text = {
                Column {
                    Text(
                        "Long-press an empty spot on your home screen → Widgets → Chronos, " +
                            "then drag the one you want.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        "Clock — live analog clock, hands move on their own",
                        "Next alarm — the next alarm with a countdown",
                        "Alarm list — up to three alarms with on/off toggles",
                        "Timer — countdown with start, pause and reset",
                        "Stopwatch — start, pause and reset",
                        "Holidays — the next holidays coming up",
                        "Timer presets — one tap starts a saved timer",
                        "Screen time — today's total, the most-used app, unlocks and screen-on count",
                    ).forEach { line ->
                        Text(
                            text = "·  $line",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The clock, timer and stopwatch widgets animate themselves, so they " +
                            "keep running without the app being open.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pinned = WidgetPinner.pin(
                        context,
                        com.osintgram.chronos.widget.ClockWidgetProvider::class.java,
                    )
                    status = if (pinned) {
                        "Confirm the prompt to place the clock on your home screen"
                    } else {
                        "This launcher does not support one-tap pinning — long-press your " +
                            "home screen and pick Chronos from Widgets"
                    }
                }) { Text("Add analog clock") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val pinned = WidgetPinner.pin(
                            context,
                            com.osintgram.chronos.widget.DigitalClockWidgetProvider::class.java,
                        )
                        status = if (pinned) {
                            "Confirm the prompt to place the digital clock"
                        } else {
                            "Long-press your home screen → Widgets → Chronos"
                        }
                    }) { Text("Digital") }
                    TextButton(onClick = {
                        val pinned = WidgetPinner.pin(
                            context,
                            com.osintgram.chronos.widget.ScreenTimeWidgetProvider::class.java,
                        )
                        status = if (pinned) {
                            "Confirm the prompt to place the screen time widget"
                        } else {
                            "Long-press your home screen → Widgets → Chronos"
                        }
                    }) { Text("Screen time") }
                    TextButton(onClick = { showWidgetInfo = false }) { Text("Close") }
                }
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

    if (showStepGoalDialog) {
        OptionDialog(
            title = "Step goal",
            options = listOf(4000, 6000, 8000, 10_000, 12_000),
            selected = Settings.stepGoal.value,
            label = { "$it steps" },
            onDismiss = { showStepGoalDialog = false },
            onPick = {
                Settings.stepGoal.set(it)
                showStepGoalDialog = false
            },
        )
    }

    if (showWaterGoalDialog) {
        OptionDialog(
            title = "Water goal",
            options = listOf(1500, 2000, 2500, 3000, 3500),
            selected = Settings.waterGoalMl.value,
            label = { "$it ml" },
            onDismiss = { showWaterGoalDialog = false },
            onPick = {
                Settings.waterGoalMl.set(it)
                showWaterGoalDialog = false
            },
        )
    }

    if (showSleepGoalDialog) {
        OptionDialog(
            title = "Sleep goal",
            options = listOf(360, 420, 450, 480, 540),
            selected = Settings.sleepGoalMinutes.value,
            label = { com.osintgram.chronos.wellness.WellnessLogic.formatMinutes(it) },
            onDismiss = { showSleepGoalDialog = false },
            onPick = {
                Settings.sleepGoalMinutes.set(it)
                showSleepGoalDialog = false
            },
        )
    }

    if (showMeditationDialog) {
        OptionDialog(
            title = "Default meditation length",
            options = listOf(3, 5, 10, 15, 20),
            selected = Settings.meditationMinutes.value,
            label = { "$it minutes" },
            onDismiss = { showMeditationDialog = false },
            onPick = {
                Settings.meditationMinutes.set(it)
                showMeditationDialog = false
            },
        )
    }

    if (showBellToneDialog) {
        val tones = Tones.all
        AlertDialog(
            onDismissRequest = { showBellToneDialog = false },
            title = { Text("Bell tone") },
            text = {
                Column {
                    tones.forEach { tone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Settings.wellnessBellTone.set(tone.id)
                                    com.osintgram.chronos.wellness.WellnessSound.playBell(context)
                                    showBellToneDialog = false
                                }
                                .semantics {
                                    role = Role.RadioButton
                                    this.selected = Settings.wellnessBellTone.value == tone.id
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = Settings.wellnessBellTone.value == tone.id,
                                onClick = null,
                            )
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(tone.title, fontSize = 15.sp)
                                Text(
                                    tone.category,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBellToneDialog = false }) { Text("Close") }
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
                    com.osintgram.chronos.wellness.WellnessStore.clearAll()
                    com.osintgram.chronos.tasks.TaskStore.clearAll()
                    com.osintgram.chronos.screentime.ScreenTimeStore.clearAll()
                    com.osintgram.chronos.screentime.LimitService.sync(context)
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


private val WidgetPalette = listOf(
    "Dark" to 0xE6171B23.toInt(),
    "Black" to 0xFF000000.toInt(),
    "Navy" to 0xFF1E3A66.toInt(),
    "White" to 0xFFF2F4F8.toInt(),
    "Cream" to 0xFFF6EFE2.toInt(),
    "Forest" to 0xFF17352A.toInt(),
)

private val TextPalette = listOf(
    "White" to 0xFFEDEFF3.toInt(),
    "Ink" to 0xFF10141B.toInt(),
    "Blue" to 0xFF4C8DFF.toInt(),
    "Teal" to 0xFF22C7A9.toInt(),
    "Amber" to 0xFFFFB020.toInt(),
    "Rose" to 0xFFFF6E8A.toInt(),
    "Green" to 0xFF4CD07D.toInt(),
    "Grey" to 0xFF9AA3B2.toInt(),
)

private fun minutesLabel(minutes: Int): String =
    String.format("%02d:%02d", (minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

/** Native time picker, so there is no custom dial to get wrong. */
private fun pickTime(
    context: android.content.Context,
    currentMinutes: Int,
    onPicked: (Int) -> Unit,
) {
    android.app.TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        (currentMinutes / 60).coerceIn(0, 23),
        (currentMinutes % 60).coerceIn(0, 59),
        true,
    ).show()
}

/** Some OEM ROMs throw from this check; treat a failure as "not allowed". */
private fun overlayAllowed(context: android.content.Context): Boolean =
    runCatching { android.provider.Settings.canDrawOverlays(context) }.getOrDefault(false)

private fun refreshWidgets(context: android.content.Context) {
    // A widget failure must never crash the settings screen.
    runCatching { com.osintgram.chronos.widget.WidgetUpdater.refreshAll(context) }
        .onFailure {
            com.osintgram.chronos.diagnostics.DiagnosticsLogger.log(
                "Settings", "Widget refresh failed: ${it.message}",
            )
        }
}

@Composable
private fun ColourDot(argb: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .pressable(scale = 0.85f, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.RadioButton
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = if (Color(argb).luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A live preview of the digital widget with the chosen style. */
@Composable
private fun WidgetStylePreview() {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.LocalTime.now()
            kotlinx.coroutines.delay(500)
        }
    }

    val pattern = when (Settings.widgetClockFormat.value) {
        "12" -> "h:mm a"
        "24s" -> "HH:mm:ss"
        "24" -> "HH:mm"
        else -> "h:mm:ss a"
    }
    val formatter = remember(pattern) {
        runCatching {
            java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.US)
        }.getOrNull()
    }
    val timeText = runCatching { now.format(formatter) }.getOrDefault("--:--")

    val bg = when (Settings.widgetBgStyle.value) {
        "transparent" -> Color.Transparent
        "white" -> Color(0xFFF2F4F8)
        "blue" -> Color(0xFF1E3A66)
        "custom" -> Color(Settings.widgetBgColor.value)
        else -> Color(0xCC171B23.toInt())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (bg == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                color = Color(Settings.widgetTextColor.value),
                fontSize = Settings.widgetTextSize.value.sp,
                fontWeight = FontWeight.Bold,
                style = com.osintgram.chronos.ui.theme.ClockTextStyle,
            )
            Text(
                // A LocalTime has no day-of-week field: formatting a date from it
                // throws UnsupportedTemporalTypeException (the 5.1.0 Settings crash).
                text = runCatching {
                    java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern(
                            "EEE, d MMM",
                            java.util.Locale.US,
                        ),
                    )
                }.getOrDefault(""),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
            )
        }
    }
}

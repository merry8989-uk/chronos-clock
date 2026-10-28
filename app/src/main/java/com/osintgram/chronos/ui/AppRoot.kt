package com.osintgram.chronos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.BuildConfig
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.diagnostics.LogExporter
import com.osintgram.chronos.ui.components.AnalogClock
import com.osintgram.chronos.ui.components.ChronosBackground
import com.osintgram.chronos.ui.components.DigitalClockLabel
import com.osintgram.chronos.ui.components.LiveClockDialog
import com.osintgram.chronos.ui.screens.AlarmEditScreen
import com.osintgram.chronos.ui.screens.AlarmListScreen
import com.osintgram.chronos.ui.screens.DiagnosticsScreen
import com.osintgram.chronos.ui.screens.SettingsScreen
import com.osintgram.chronos.ui.screens.SleepScreen
import com.osintgram.chronos.ui.screens.StopwatchScreen
import com.osintgram.chronos.ui.screens.TimerScreen
import com.osintgram.chronos.ui.screens.WorldClockScreen
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.pressable

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Alarm", Icons.Filled.Alarm),
    Tab("World", Icons.Filled.Public),
    Tab("Timer", Icons.Filled.HourglassBottom),
    Tab("Stopwatch", Icons.Filled.Timer),
    Tab("Sleep", Icons.Filled.Bedtime),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf(-1L) }
    var menuOpen by remember { mutableStateOf(false) }
    var showClock by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val keepScreenOn = Settings.keepScreenOn.value
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    when {
        showDiagnostics -> {
            ChronosBackground {
                DiagnosticsScreen(onBack = { showDiagnostics = false })
            }
            return
        }
        showSettings -> {
            ChronosBackground {
                SettingsScreen(onBack = { showSettings = false })
            }
            return
        }
        showEditor -> {
            ChronosBackground {
                AlarmEditScreen(
                    alarmId = editingId.takeIf { it >= 0 },
                    onClose = { showEditor = false },
                )
            }
            return
        }
    }

    if (showClock) {
        LiveClockDialog(onDismiss = { showClock = false })
    }

    ChronosBackground {
        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            // The logo is the clock: three hands, live.
                            if (Settings.logoStyle.value == "analog") {
                                AnalogClock(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pressable(scale = 0.9f) { showClock = true },
                                    showSecondHand = Settings.clockSecondHand.value,
                                    smoothSweep = Settings.clockSmooth.value,
                                    rimColor = MaterialTheme.colorScheme.primary,
                                    tickColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    hourHandColor = MaterialTheme.colorScheme.onBackground,
                                    minuteHandColor = MaterialTheme.colorScheme.onBackground,
                                    secondHandColor = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                DigitalClockLabel(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .pressable(scale = 0.94f) { showClock = true },
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = tabs[tabIndex].label,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    actions = {
                        if (tabIndex == 0) {
                            IconButton(onClick = {
                                Haptics.tap(context)
                                editingId = -1L
                                showEditor = true
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add alarm")
                            }
                        }
                        IconButton(onClick = {
                            Haptics.tick(context)
                            menuOpen = true
                        }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuOpen = false
                                    showSettings = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add alarm") },
                                onClick = {
                                    menuOpen = false
                                    tabIndex = 0
                                    editingId = -1L
                                    showEditor = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("About & updates") },
                                onClick = {
                                    menuOpen = false
                                    showSettings = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Diagnostics & logs") },
                                onClick = {
                                    menuOpen = false
                                    showDiagnostics = true
                                },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = {
                                if (tabIndex != index) Haptics.tick(context)
                                tabIndex = index
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CrashBanner()
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(
                        animationSpec = tween(Motion.MEDIUM, easing = Motion.easeOut),
                    ) { width -> if (forward) width / 7 else -width / 7 } + fadeIn(
                        animationSpec = tween(Motion.MEDIUM),
                    )
                    val exit = slideOutHorizontally(
                        animationSpec = tween(Motion.FAST, easing = Motion.easeInOut),
                    ) { width -> if (forward) -width / 7 else width / 7 } + fadeOut(
                        animationSpec = tween(Motion.FAST),
                    )
                    enter togetherWith exit
                },
                label = "tabContent",
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (index) {
                        0 -> AlarmListScreen(
                            onEdit = { id ->
                                editingId = id ?: -1L
                                showEditor = true
                            },
                        )
                        1 -> WorldClockScreen()
                        2 -> TimerScreen()
                        3 -> StopwatchScreen()
                        else -> SleepScreen()
                    }
                }
            }
            }
        }
    }
}

/**
 * If the app crashed last time, say so and offer the log — reachable even when
 * the screen that crashed cannot be opened.
 */
@Composable
private fun CrashBanner() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(DiagnosticsLogger.lastCrash()) }
    val info = crash ?: return

    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chronos crashed last time",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = info.second.ifBlank { "Unknown error" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            TextButton(onClick = {
                val file = LogExporter.export(
                    context, LogExporter.Format.TXT, "Chronos", BuildConfig.VERSION_NAME,
                )
                LogExporter.share(context, file, "text/plain")
            }) { Text("Send log") }
            TextButton(onClick = {
                DiagnosticsLogger.clearCrashMarker()
                crash = null
            }) { Text("Dismiss") }
        }
    }
}

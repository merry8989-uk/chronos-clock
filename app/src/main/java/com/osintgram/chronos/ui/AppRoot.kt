package com.osintgram.chronos.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.screens.AlarmEditScreen
import com.osintgram.chronos.ui.screens.AlarmListScreen
import com.osintgram.chronos.ui.screens.SettingsScreen
import com.osintgram.chronos.ui.screens.SleepScreen
import com.osintgram.chronos.ui.screens.StopwatchScreen
import com.osintgram.chronos.ui.screens.TimerScreen
import com.osintgram.chronos.ui.screens.WorldClockScreen

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Alarm", Icons.Filled.Alarm),
    Tab("World Clock", Icons.Filled.Public),
    Tab("Timer", Icons.Filled.HourglassBottom),
    Tab("Stopwatch", Icons.Filled.Timer),
    Tab("Sleep", Icons.Filled.Bedtime),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf(-1L) }
    var menuOpen by remember { mutableStateOf(false) }

    // Rule 4: honour the "keep screen on" option everywhere in the app.
    val keepScreenOn = Settings.keepScreenOn.value
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    when {
        showSettings -> {
            SettingsScreen(onBack = { showSettings = false })
            return
        }
        showEditor -> {
            AlarmEditScreen(
                alarmId = editingId.takeIf { it >= 0 },
                onClose = { showEditor = false },
            )
            return
        }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(tabs[tabIndex].label) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (tabIndex == 0) {
                        IconButton(onClick = {
                            editingId = -1L
                            showEditor = true
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add alarm")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tabIndex) {
            0 -> AlarmListScreen(
                modifier = contentModifier,
                onEdit = { id ->
                    editingId = id ?: -1L
                    showEditor = true
                },
            )
            1 -> WorldClockScreen(modifier = contentModifier)
            2 -> TimerScreen(modifier = contentModifier)
            3 -> StopwatchScreen(modifier = contentModifier)
            else -> SleepScreen(modifier = contentModifier)
        }
    }
}

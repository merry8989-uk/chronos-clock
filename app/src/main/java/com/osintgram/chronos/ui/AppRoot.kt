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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.BuildConfig
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.Breadcrumbs
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.diagnostics.LogExporter
import com.osintgram.chronos.ui.components.AnalogClock
import com.osintgram.chronos.ui.components.ChronosBackground
import com.osintgram.chronos.ui.components.DigitalClockLabel
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.LiveClockDialog
import com.osintgram.chronos.ui.screens.AlarmEditScreen
import com.osintgram.chronos.ui.screens.AlarmListScreen
import com.osintgram.chronos.ui.screens.DiagnosticsScreen
import com.osintgram.chronos.ui.screens.SettingsScreen
import com.osintgram.chronos.ui.screens.StopwatchScreen
import com.osintgram.chronos.ui.screens.TimerScreen
import com.osintgram.chronos.ui.screens.LifeScreen
import com.osintgram.chronos.ui.screens.SpecialAccessScreen
import com.osintgram.chronos.ui.screens.WorldClockScreen
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.pressable

/**
 * The smallest touch target the shell allows, in dp. The platform guideline is
 * 48 dp; controls keep their drawn size and grow their padding to reach it.
 */
private val MinTouchTarget = 48.dp

/** The size of the live clock logo inside its (larger) touch target. */
private val LogoSize = 38.dp

/**
 * One bottom-bar destination.
 *
 * Two glyphs per destination, so the selection reads even before the label is
 * parsed: the filled icon means "you are here", the outlined one means "you
 * could go here". `NavigationBarItem` swaps between them on selection.
 */
private data class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab("Alarm", Icons.Filled.Alarm, Icons.Outlined.Alarm),
    Tab("World", Icons.Filled.Public, Icons.Outlined.Public),
    Tab("Timer", Icons.Filled.HourglassBottom, Icons.Outlined.HourglassBottom),
    Tab("Stopwatch", Icons.Filled.Timer, Icons.Outlined.Timer),
    Tab("Life", Icons.Filled.Checklist, Icons.Outlined.Checklist),
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
    var showSpecialAccess by remember { mutableStateOf(false) }
    var settingsScrollTo by remember { mutableStateOf<String?>(null) }

    val keepScreenOn = Settings.keepScreenOn.value
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    when {
        showSpecialAccess -> {
            ChronosBackground {
                SpecialAccessScreen(onBack = { showSpecialAccess = false })
            }
            return
        }
        showDiagnostics -> {
            ChronosBackground {
                DiagnosticsScreen(onBack = { showDiagnostics = false })
            }
            return
        }
        showSettings -> {
            ChronosBackground {
                SettingsScreen(
                    onBack = {
                        showSettings = false
                        settingsScrollTo = null
                    },
                    scrollTo = settingsScrollTo,
                )
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The logo is the clock: three hands, live. It is also the
                            // shortcut to the clock screen, so it gets a full touch
                            // target around the artwork rather than the artwork's size.
                            if (Settings.logoStyle.value == "analog") {
                                Box(
                                    modifier = Modifier
                                        .size(MinTouchTarget)
                                        .pressable(scale = 0.9f) { showClock = true }
                                        .semantics {
                                            contentDescription = "Open live clock"
                                            role = Role.Button
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AnalogClock(
                                        modifier = Modifier.size(LogoSize),
                                        showSecondHand = Settings.clockSecondHand.value,
                                        smoothSweep = Settings.clockSmooth.value,
                                        rimColor = MaterialTheme.colorScheme.primary,
                                        tickColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        hourHandColor = MaterialTheme.colorScheme.onBackground,
                                        minuteHandColor = MaterialTheme.colorScheme.onBackground,
                                        secondHandColor = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = MinTouchTarget)
                                        .pressable(scale = 0.94f) { showClock = true }
                                        .semantics { role = Role.Button }
                                        .padding(end = Spacing.sm),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    DigitalClockLabel(
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = tabs[tabIndex].label,
                                style = ChronosType.title,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    actions = {
                        if (tabIndex == 0) {
                            IconButton(
                                modifier = Modifier.size(MinTouchTarget),
                                onClick = {
                                    Haptics.tap(context)
                                    editingId = -1L
                                    showEditor = true
                                    Breadcrumbs.action("open", "alarm_editor")
                                },
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add alarm")
                            }
                        }
                        IconButton(
                            modifier = Modifier.size(MinTouchTarget),
                            onClick = {
                                Haptics.tick(context)
                                menuOpen = true
                            },
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "App menu: settings, alarms, tools and diagnostics",
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings", style = ChronosType.body) },
                                onClick = {
                                    menuOpen = false
                                    settingsScrollTo = null
                                    showSettings = true
                                    Breadcrumbs.action("open", "settings")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add alarm", style = ChronosType.body) },
                                onClick = {
                                    menuOpen = false
                                    tabIndex = 0
                                    editingId = -1L
                                    showEditor = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("About & updates", style = ChronosType.body) },
                                onClick = {
                                    menuOpen = false
                                    settingsScrollTo = com.osintgram.chronos.ui.screens.SCROLL_ABOUT
                                    showSettings = true
                                    Breadcrumbs.action("open", "about")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Bedtime mode", style = ChronosType.body) },
                                onClick = {
                                    menuOpen = false
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                context,
                                                com.osintgram.chronos.bedtime.BedtimeActivity::class.java,
                                            ),
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Special access", style = ChronosType.body) },
                                onClick = {
                                    menuOpen = false
                                    showSpecialAccess = true
                                    Breadcrumbs.action("open", "special_access")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Diagnostics & logs", style = ChronosType.body) },
                                onClick = {
                                    menuOpen = false
                                    showDiagnostics = true
                                    Breadcrumbs.action("open", "diagnostics")
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
                        val selected = tabIndex == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (tabIndex != index) Haptics.tick(context)
                                tabIndex = index
                                Breadcrumbs.screen(tabs[index].label)
                            },
                            // Filled when selected, outlined otherwise: the shape of the
                            // glyph, not just the tint, says where you are.
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = ChronosType.label,
                                    maxLines = 1,
                                )
                            },
                            // Every destination keeps its label, so the bar reads the same
                            // whichever tab is open.
                            alwaysShowLabel = true,
                            modifier = Modifier.heightIn(min = MinTouchTarget),
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
                TabPane(tab = tabs[index]) {
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
                        else -> LifeScreen()
                    }
                }
            }
            }
        }
    }
}

/**
 * Hosts one destination's screen.
 *
 * The screen is composed exactly as before; this wrapper only guarantees that
 * the pane is never an empty rectangle. If a screen composes nothing at all —
 * a store that has not loaded yet, for instance — the shell draws a quiet
 * placeholder of its own instead of leaving the area blank. Screens keep their
 * own empty and loading states; this covers only the case where a screen emits
 * no visible content whatsoever.
 */
@Composable
private fun TabPane(
    tab: Tab,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        val contentPlaceables = subcompose(TabPaneSlot.Content, content)
            .map { measurable -> measurable.measure(constraints) }
        val hasVisibleContent = contentPlaceables.any { it.width > 0 && it.height > 0 }
        val fallbackPlaceables = if (hasVisibleContent) {
            emptyList()
        } else {
            subcompose(TabPaneSlot.Fallback) { TabFallback(tab) }
                .map { measurable -> measurable.measure(constraints) }
        }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            contentPlaceables.maxOfOrNull { it.width } ?: 0
        }
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            contentPlaceables.maxOfOrNull { it.height } ?: 0
        }
        layout(width, height) {
            contentPlaceables.forEach { placeable -> placeable.placeRelative(0, 0) }
            fallbackPlaceables.forEach { placeable -> placeable.placeRelative(0, 0) }
        }
    }
}

/** Subcompose slots used by [TabPane]. */
private enum class TabPaneSlot { Content, Fallback }

/**
 * What the shell shows when a destination has drawn nothing yet: the tab's own
 * name, and a sentence saying the area will fill in, rather than blank space.
 */
@Composable
private fun TabFallback(tab: Tab) {
    EmptyState(
        title = tab.label,
        subtitle = "This tab has nothing to show yet. It fills in as soon as its data is ready.",
    )
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
        shape = RoundedCornerShape(Radii.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chronos crashed last time",
                    style = ChronosType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = info.second.ifBlank { "Unknown error" },
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            TextButton(
                modifier = Modifier.heightIn(min = MinTouchTarget),
                onClick = {
                    val file = LogExporter.export(
                        context, LogExporter.Format.TXT, "Chronos", BuildConfig.VERSION_NAME,
                    )
                    LogExporter.share(context, file, "text/plain")
                },
            ) { Text("Send log", style = ChronosType.label) }
            TextButton(
                modifier = Modifier.heightIn(min = MinTouchTarget),
                onClick = {
                    DiagnosticsLogger.clearCrashMarker()
                    crash = null
                },
            ) { Text("Dismiss", style = ChronosType.label) }
        }
    }
}

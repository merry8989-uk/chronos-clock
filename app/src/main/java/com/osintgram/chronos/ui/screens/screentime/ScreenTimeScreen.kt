package com.osintgram.chronos.ui.screens.screentime

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.screentime.AppUsage
import com.osintgram.chronos.screentime.LimitService
import com.osintgram.chronos.screentime.NotificationWatcher
import com.osintgram.chronos.screentime.ScreenTimeLogic
import com.osintgram.chronos.screentime.ScreenTimeStore
import com.osintgram.chronos.screentime.UsageSource
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.ErrorState
import com.osintgram.chronos.ui.components.LoadingState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.ui.theme.heroSurfaceBrush
import com.osintgram.chronos.ui.theme.pressable

private enum class ScreenTab(val label: String) {
    Today("Today"),
    Apps("Apps"),
    Alerts("Alerts"),
    Limits("Limits"),
}

/**
 * How the usage read is going, for the screen only. The read itself is a plain
 * synchronous call into [UsageSource], so there is no job to observe: the phase
 * starts on [ReadPhase.Loading] for each read pass and is settled on the frame
 * after it, and a read that comes back with nothing at all while Usage access is
 * granted is a failed read rather than a zero day — a successful read always
 * fills all 24 hourly buckets, so "no buckets" can only mean the read did not
 * happen.
 */
private enum class ReadPhase { Loading, Ready, Failed }

// ---- Local sizes -----------------------------------------------------------
// Padding, radii and every type size come from Spacing, Radii and ChronosType.
// What is left is geometry those scales do not carry: two icon sizes, the round
// app badge, and the block a state card is given so it reads as a block rather
// than a stray line.

private val SmallIcon = 18.dp
private val HeroIcon = 20.dp
private val BadgeSize = 30.dp
private val StateBlockHeight = 168.dp

// Figures keep their width at sizes below the 26sp hero step: the same tabular
// numerals as ChronosType.numeric, taken down to the size each row needs.
private val NumericMid = ChronosType.numeric.copy(fontSize = 18.sp, lineHeight = 22.sp)
private val NumericSmall = ChronosType.numeric.copy(fontSize = 14.sp, lineHeight = 18.sp)
private val NumericTiny = ChronosType.numeric.copy(fontSize = 12.sp, lineHeight = 16.sp)

/**
 * Screen time: how long the phone was used, which app took the most, how often
 * each app was opened, how many notifications each app posted, and the daily
 * limits that block an app once its time is up.
 *
 * The numbers come from Android's own usage database (Usage access) and from a
 * notification listener that counts notifications without reading them. Without
 * those two grants the tab says so and shows nothing rather than zeros.
 *
 * The story runs in one order: today's total and the progress against the day's
 * limits, then the hour-by-hour chart, then the seven-day trend, then pickups
 * and sessions, then the per-app list.
 */
@Composable
fun ScreenTimeScreen() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(ScreenTab.Today.name) }
    var refresh by remember { mutableIntStateOf(0) }
    var limitTarget by remember { mutableStateOf<AppUsage?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    val current = ScreenTab.entries.firstOrNull { it.name == tab } ?: ScreenTab.Today
    val usageAccess = remember(tick, refresh) { UsageSource.hasAccess(context) }
    val listenerEnabled = remember(tick, refresh) { NotificationWatcher.isEnabled(context) }
    val day = remember(refresh, tick, listenerEnabled) {
        UsageSource.today(context, ScreenTimeStore.notificationsFor())
    }
    val limits = ScreenTimeStore.allLimits()
    val week = remember(refresh, tick, usageAccess) { UsageSource.week(context) }
    val use24Hour = Settings.use24Hour.value
    val currentHour = remember(refresh, tick) { java.time.LocalTime.now().hour }

    // The read above has already run by the time this composition exists, so the
    // phase is settled on the next frame: Ready when the read produced a day,
    // Failed when it produced nothing although access is granted.
    var readPhase by remember(refresh, tick, listenerEnabled) {
        mutableStateOf(ReadPhase.Loading)
    }
    LaunchedEffect(refresh, tick, listenerEnabled) {
        readPhase = if (
            usageAccess &&
            !day.hasHourly &&
            day.apps.isEmpty() &&
            day.totalForegroundMillis == 0L
        ) {
            ReadPhase.Failed
        } else {
            ReadPhase.Ready
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(ScreenTab.entries, key = { it.name }) { entry ->
                    Pill(
                        text = entry.label,
                        selected = entry == current,
                        onClick = {
                            Haptics.tick(context)
                            tab = entry.name
                            tick++
                        },
                    )
                }
            }
        }

        if (!usageAccess) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text(
                            text = "Usage access needed",
                            style = ChronosType.title,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        WellnessNote(
                            "Android keeps app usage behind a special switch. Turn Chronos on under " +
                                "Usage access and this tab fills itself from the phone's own records.",
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Button(
                            onClick = { UsageSource.openAccessSettings(context) },
                            shape = RoundedCornerShape(Radii.md),
                        ) {
                            Text("Open Usage access", style = ChronosType.label)
                        }
                    }
                }
            }
        } else if (readPhase != ReadPhase.Ready) {
            item {
                when (readPhase) {
                    ReadPhase.Loading -> LoadingState(
                        label = "Reading today's usage",
                        modifier = Modifier.height(StateBlockHeight),
                    )
                    else -> ErrorState(
                        title = "Could not read usage",
                        detail = "The phone's usage records did not come back this time. " +
                            "Nothing is wrong with the data already on screen.",
                        onRetry = { refresh++ },
                        modifier = Modifier.height(StateBlockHeight),
                    )
                }
            }
        } else {
            when (current) {
                ScreenTab.Today -> {
                    item { TodayHero(day, limits, onRefresh = { refresh++ }) }
                    item { WellnessLabel("TODAY, HOUR BY HOUR") }
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(Radii.lg),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(Spacing.lg)) {
                                if (day.hasHourly) {
                                    HourlyUsageChart(
                                        hours = day.hourlyMillis,
                                        currentHour = currentHour,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    WellnessNote(
                                        "Each bar is an hour of today, read off the phone's own " +
                                            "event log. The hour you are in is picked out and " +
                                            "marked now.",
                                    )
                                } else {
                                    WellnessNote("No hour-by-hour record for today yet.")
                                }
                            }
                        }
                    }
                    item { WellnessLabel("LAST 7 DAYS") }
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(Radii.lg),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(Spacing.lg)) {
                                if (week.days.isEmpty()) {
                                    WellnessNote("No week to compare against yet.")
                                } else {
                                    WeekTrendChart(week = week)
                                    Spacer(Modifier.height(Spacing.md))
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                                        MiniStat(
                                            ScreenTimeLogic.formatDuration(week.averageActiveMillis),
                                            "average day",
                                        )
                                        MiniStat(
                                            value = week.busiest
                                                ?.let { ScreenTimeLogic.formatDuration(it.foregroundMillis) }
                                                ?: "—",
                                            label = week.busiest?.let { "busiest, ${it.name}" } ?: "busiest day",
                                        )
                                    }
                                    WellnessNote("The average counts only the days with any use.")
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            PickupTile(
                                value = day.firstUnlockMillis
                                    ?.let { ScreenTimeLogic.clockTime(it, use24Hour) }
                                    ?: "—",
                                label = "first pickup",
                                sub = day.lastUnlockMillis
                                    ?.let { "last ${ScreenTimeLogic.clockTime(it, use24Hour)}" },
                                modifier = Modifier.weight(1f),
                            )
                            PickupTile(
                                value = day.longestSession
                                    ?.let { ScreenTimeLogic.formatDuration(it.durationMillis) }
                                    ?: "—",
                                label = "longest session",
                                sub = day.longestSession?.label,
                                modifier = Modifier.weight(1f),
                            )
                            PickupTile(
                                value = "${day.sessionsOverFiveMinutes}",
                                label = "over 5 min",
                                sub = "sessions",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item {
                        WellnessNote(
                            "Pickups and sessions come from the phone's own unlock and app-switch " +
                                "records — nothing here is estimated.",
                        )
                    }
                    item { WellnessLabel("MOST USED") }
                    if (day.topApps.isEmpty()) {
                        item {
                            EmptyState(
                                title = "Nothing yet today",
                                subtitle = "The first app you open shows up here, with its share " +
                                    "of the day.",
                                modifier = Modifier.height(StateBlockHeight),
                            )
                        }
                    } else {
                        items(day.topApps, key = { "top:${it.packageName}" }) { app ->
                            UsageCard(app, day.totalForegroundMillis, limits[app.packageName])
                        }
                    }
                }

                ScreenTab.Apps -> {
                    item { WellnessLabel("ALL APPS TODAY") }
                    if (day.apps.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No app usage yet",
                                subtitle = "Nothing has been opened today, so there is nothing to " +
                                    "list. Usage fills in as apps are used.",
                                modifier = Modifier.height(StateBlockHeight),
                            )
                        }
                    } else {
                        items(day.apps, key = { "app:${it.packageName}" }) { app ->
                            UsageCard(app, day.totalForegroundMillis, limits[app.packageName])
                        }
                    }
                }

                ScreenTab.Alerts -> {
                    item { WellnessLabel("NOTIFICATIONS TODAY") }
                    if (!listenerEnabled) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(Radii.lg),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(Spacing.lg)) {
                                    Text(
                                        text = "Notification access needed",
                                        style = ChronosType.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    WellnessNote(
                                        "Chronos counts how many notifications each app posted — it " +
                                            "never reads their text. Turn it on under Notification access.",
                                    )
                                    Spacer(Modifier.height(Spacing.md))
                                    Button(
                                        onClick = { NotificationWatcher.openSettings(context) },
                                        shape = RoundedCornerShape(Radii.md),
                                    ) {
                                        Text("Open Notification access", style = ChronosType.label)
                                    }
                                }
                            }
                        }
                    } else {
                        val counted = day.apps.filter { it.notifications > 0 }
                            .sortedByDescending { it.notifications }
                        if (counted.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "No notifications counted",
                                    subtitle = "The listener is on. Nothing has been posted today " +
                                        "so far.",
                                    modifier = Modifier.height(StateBlockHeight),
                                )
                            }
                        } else {
                            items(counted, key = { "alert:${it.packageName}" }) { app ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(Radii.md),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(Spacing.md),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppBadge(app.label)
                                        Spacer(Modifier.width(Spacing.sm))
                                        Text(
                                            text = app.label,
                                            style = ChronosType.body,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${app.notifications}",
                                            style = NumericSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        WellnessNote(
                            "Only a count is stored, per app per day, for the last 14 days. No " +
                                "notification title, text or sender is ever written to disk.",
                        )
                    }
                }

                ScreenTab.Limits -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(Radii.lg),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(Spacing.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "Limit watcher",
                                            style = ChronosType.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = if (Settings.screenTimeLimiter.value) {
                                                "Running · blocks an app when its time is up"
                                            } else {
                                                "Off · limits are not enforced"
                                            },
                                            style = ChronosType.label,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    androidx.compose.material3.Switch(
                                        checked = Settings.screenTimeLimiter.value,
                                        onCheckedChange = {
                                            Settings.screenTimeLimiter.set(it)
                                            LimitService.sync(context)
                                            tick++
                                        },
                                    )
                                }
                                WellnessNote(
                                    "The watcher polls which app is in front every few seconds, so an " +
                                        "app can be used briefly before the blocker appears. It needs the " +
                                        "draw-over-other-apps permission.",
                                )
                            }
                        }
                    }
                    item {
                        val usageOk = UsageSource.hasAccess(context)
                        val overlayOk = android.provider.Settings.canDrawOverlays(context)
                        val armed = Settings.screenTimeLimiter.value
                        if (!usageOk || !overlayOk || !armed) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(Radii.lg),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(Spacing.lg)) {
                                    Text(
                                        text = "Limits cannot be enforced yet",
                                        style = ChronosType.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(Spacing.sm))
                                    LimitCheck("Usage access", usageOk) {
                                        context.startActivity(
                                            Intent(
                                                android.provider.Settings
                                                    .ACTION_USAGE_ACCESS_SETTINGS,
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                    LimitCheck("Draw over other apps", overlayOk) {
                                        context.startActivity(
                                            Intent(
                                                android.provider.Settings
                                                    .ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                    LimitCheck("Limit watcher", armed) {
                                        Settings.screenTimeLimiter.set(true)
                                        LimitService.sync(context)
                                        tick++
                                    }
                                }
                            }
                        }
                    }
                    item { WellnessLabel("DAILY LIMITS") }
                    if (limits.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No limits yet",
                                subtitle = "Give an app a daily limit below and the watcher closes " +
                                    "it when the time is up.",
                                modifier = Modifier.height(StateBlockHeight),
                            )
                        }
                    } else {
                        items(limits.entries.toList(), key = { "limit:${it.key}" }) { entry ->
                            val app = day.apps.firstOrNull { it.packageName == entry.key }
                            val used = app?.foregroundMillis ?: 0L
                            val over = ScreenTimeLogic.limitReached(used, entry.value)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(Radii.md),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable { limitTarget = app ?: AppUsage(entry.key, UsageSource.labelFor(context, entry.key), 0, 0) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppBadge(app?.label ?: UsageSource.labelFor(context, entry.key))
                                    Spacer(Modifier.width(Spacing.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = app?.label ?: UsageSource.labelFor(context, entry.key),
                                            style = ChronosType.body,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "${ScreenTimeLogic.formatDuration(used)} of ${entry.value} min" +
                                                if (over) " · Over" else "",
                                            style = NumericTiny,
                                            color = if (over) {
                                                StatusColors.warning
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    TextButton(onClick = {
                                        ScreenTimeStore.removeLimit(entry.key)
                                        LimitService.sync(context)
                                        tick++
                                    }) { Text("Remove", style = ChronosType.label) }
                                }
                            }
                        }
                    }
                    item { WellnessLabel("SET A LIMIT") }
                    // Apps that already have a limit are listed above; keeping them
                    // here would put the same package in the list twice.
                    items(
                        day.apps.filter { !limits.containsKey(it.packageName) }.take(12),
                        key = { "app:${it.packageName}" },
                    ) { app ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(Radii.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable { limitTarget = app },
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppBadge(app.label)
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = app.label,
                                    style = ChronosType.body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = ScreenTimeLogic.formatDuration(app.foregroundMillis),
                                    style = NumericTiny,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    limitTarget?.let { target ->
        LimitDialog(
            app = target,
            current = limits[target.packageName],
            onDismiss = { limitTarget = null },
            onPick = { minutes ->
                ScreenTimeStore.setLimit(target.packageName, minutes)
                // A limit that nothing enforces is the same as no limit: switch
                // the watcher on as soon as one exists, and say so in the log.
                if (minutes > 0 && !Settings.screenTimeLimiter.value) {
                    Settings.screenTimeLimiter.set(true)
                    com.osintgram.chronos.diagnostics.DiagnosticsLogger.log(
                        "ScreenTime",
                        "Limit set for ${target.packageName}; limit watcher switched on",
                    )
                }
                limitTarget = null
                tick++
                LimitService.sync(context)
            },
        )
    }
}

/**
 * The hero: today's total as the one number the screen is about, with the
 * progress against the day's limits under it.
 *
 * The limits are the only ceiling the user has set for a day, so the day's
 * budget is the sum of them and the progress is measured against that. With no
 * limit set there is no budget to measure against, and the card says so in words
 * rather than drawing an empty bar that would read as zero progress.
 */
@Composable
private fun TodayHero(
    day: com.osintgram.chronos.screentime.DayScreenTime,
    limits: Map<String, Int>,
    onRefresh: () -> Unit,
) {
    val budgetMinutes = limits.values.sum()
    val budgetMillis = budgetMinutes * 60_000L
    val over = budgetMillis > 0L && day.totalForegroundMillis > budgetMillis

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(heroSurfaceBrush())
                .padding(Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(HeroIcon),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = ScreenTimeLogic.formatDuration(day.totalForegroundMillis),
                    style = ChronosType.numeric,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh usage",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "screen time today",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            if (budgetMillis > 0L) {
                val fraction = (day.totalForegroundMillis.toFloat() / budgetMillis)
                    .coerceIn(0f, 1f)
                ProgressLine(fraction)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "${ScreenTimeLogic.formatDuration(budgetMillis)} of app limits · " +
                        if (over) "over" else "under",
                    style = ChronosType.label,
                    color = if (over) StatusColors.warning else StatusColors.success,
                )
            } else {
                Text(
                    text = "No daily goal set. A limit on any app below turns this into a " +
                        "progress bar.",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MiniStat("${day.unlockCount}", "times unlocked")
                MiniStat("${day.screenOnCount}", "screen on")
                MiniStat("${day.apps.count { it.launches > 0 }}", "apps used")
            }
        }
    }
}

/** One "is this ready?" row with a fix button when it is not. */
@Composable
private fun LimitCheck(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (ok) StatusColors.success else StatusColors.warning,
            modifier = Modifier.size(SmallIcon),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = ChronosType.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!ok) {
            TextButton(onClick = onFix) { Text("Fix", style = ChronosType.label) }
        } else {
            Text(
                text = "ready",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = NumericMid,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A pickup or session tile: the value, what it is, and an optional detail. */
@Composable
private fun PickupTile(
    value: String,
    label: String,
    sub: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.md),
        modifier = modifier,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                text = value,
                style = NumericSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = label,
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AppBadge(label: String) {
    Box(
        modifier = Modifier
            .size(BadgeSize)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).uppercase(),
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One app: its share of the day, its time and opens, and its limit as a word.
 *
 * The limit is never colour alone — "Over" and "Close" are written out, so the
 * state survives a colour-blind reading of the row.
 */
@Composable
private fun UsageCard(app: AppUsage, totalMillis: Long, limitMinutes: Int?) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBadge(app.label)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = ChronosType.title,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${ScreenTimeLogic.formatDuration(app.foregroundMillis)} · " +
                            "${app.launches} open(s)" +
                            if (app.notifications > 0) " · ${app.notifications} alert(s)" else "",
                        style = NumericTiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (limitMinutes != null) {
                    val progress = ScreenTimeLogic.limitProgress(app.foregroundMillis, limitMinutes)
                    val reached = ScreenTimeLogic.limitReached(app.foregroundMillis, limitMinutes)
                    val close = !reached && progress >= CLOSE_TO_LIMIT
                    Text(
                        text = when {
                            reached -> "Over · ${limitMinutes}m"
                            close -> "Close · ${limitMinutes}m"
                            else -> "limit ${limitMinutes}m"
                        },
                        style = NumericTiny,
                        color = when {
                            reached -> StatusColors.warning
                            close -> StatusColors.info
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            ProgressLine(ScreenTimeLogic.share(app, totalMillis))
        }
    }
}

/** Where "close to the limit" starts, as a share of the limit already used. */
private const val CLOSE_TO_LIMIT = 0.8f

@Composable
private fun LimitDialog(
    app: AppUsage,
    current: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limit for ${app.label}", style = ChronosType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Used ${ScreenTimeLogic.formatDuration(app.foregroundMillis)} today" +
                        if (current != null) " · current limit ${current} min" else "",
                    style = NumericTiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                    OutlinedButton(
                        onClick = { onPick(minutes) },
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (current == minutes) "$minutes min (current)" else "$minutes min",
                            style = ChronosType.label,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

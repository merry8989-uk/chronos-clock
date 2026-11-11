package com.osintgram.chronos.ui.screens.screentime

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
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.screentime.AppUsage
import com.osintgram.chronos.screentime.LimitService
import com.osintgram.chronos.screentime.NotificationWatcher
import com.osintgram.chronos.screentime.ScreenTimeLogic
import com.osintgram.chronos.screentime.ScreenTimeStore
import com.osintgram.chronos.screentime.UsageSource
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.components.ProgressLine
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.pressable

private enum class ScreenTab(val label: String) {
    Today("Today"),
    Apps("Apps"),
    Alerts("Alerts"),
    Limits("Limits"),
}

/**
 * Screen time: how long the phone was used, which app took the most, how often
 * each app was opened, how many notifications each app posted, and the daily
 * limits that block an app once its time is up.
 *
 * The numbers come from Android's own usage database (Usage access) and from a
 * notification listener that counts notifications without reading them. Without
 * those two grants the tab says so and shows nothing rather than zeros.
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

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Usage access needed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        WellnessNote(
                            "Android keeps app usage behind a special switch. Turn Chronos on under " +
                                "Usage access and this tab fills itself from the phone's own records.",
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { UsageSource.openAccessSettings(context) }) {
                            Text("Open Usage access", fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            when (current) {
                ScreenTab.Today -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = ScreenTimeLogic.formatDuration(day.totalForegroundMillis),
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { refresh++ }) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Refresh usage",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = "screen time today",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    MiniStat("${day.unlockCount}", "times unlocked")
                                    MiniStat("${day.screenOnCount}", "screen on")
                                    MiniStat("${day.apps.count { it.launches > 0 }}", "apps used")
                                }
                            }
                        }
                    }
                    item { WellnessLabel("MOST USED") }
                    if (day.topApps.isEmpty()) {
                        item { WellnessNote("Nothing recorded yet today.") }
                    } else {
                        items(day.topApps, key = { it.packageName }) { app ->
                            UsageCard(app, day.totalForegroundMillis, limits[app.packageName])
                        }
                    }
                }

                ScreenTab.Apps -> {
                    item { WellnessLabel("ALL APPS TODAY") }
                    if (day.apps.isEmpty()) {
                        item { WellnessNote("No app usage recorded yet today.") }
                    } else {
                        items(day.apps, key = { it.packageName }) { app ->
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
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Notification access needed",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    WellnessNote(
                                        "Chronos counts how many notifications each app posted — it " +
                                            "never reads their text. Turn it on under Notification access.",
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Button(onClick = { NotificationWatcher.openSettings(context) }) {
                                        Text("Open Notification access", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        val counted = day.apps.filter { it.notifications > 0 }
                            .sortedByDescending { it.notifications }
                        if (counted.isEmpty()) {
                            item { WellnessNote("No notifications counted yet today.") }
                        } else {
                            items(counted, key = { it.packageName }) { app ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppBadge(app.label)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = app.label,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${app.notifications}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
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
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "Limit watcher",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = if (Settings.screenTimeLimiter.value) {
                                                "Running · blocks an app when its time is up"
                                            } else {
                                                "Off · limits are not enforced"
                                            },
                                            fontSize = 12.sp,
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
                    item { WellnessLabel("DAILY LIMITS") }
                    if (limits.isEmpty()) {
                        item { WellnessNote("No limits yet. Tap an app below to set one.") }
                    } else {
                        items(limits.entries.toList(), key = { it.key }) { entry ->
                            val app = day.apps.firstOrNull { it.packageName == entry.key }
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable { limitTarget = app ?: AppUsage(entry.key, UsageSource.labelFor(context, entry.key), 0, 0) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppBadge(app?.label ?: UsageSource.labelFor(context, entry.key))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = app?.label ?: UsageSource.labelFor(context, entry.key),
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "${ScreenTimeLogic.formatDuration(app?.foregroundMillis ?: 0L)} of ${entry.value} min",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = {
                                        ScreenTimeStore.removeLimit(entry.key)
                                        LimitService.sync(context)
                                        tick++
                                    }) { Text("Remove", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                    item { WellnessLabel("SET A LIMIT") }
                    items(day.apps.take(12), key = { it.packageName }) { app ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable { limitTarget = app },
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppBadge(app.label)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = app.label,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = ScreenTimeLogic.formatDuration(app.foregroundMillis),
                                    fontSize = 12.sp,
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
                limitTarget = null
                tick++
                LimitService.sync(context)
            },
        )
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppBadge(label: String) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun UsageCard(app: AppUsage, totalMillis: Long, limitMinutes: Int?) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBadge(app.label)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${ScreenTimeLogic.formatDuration(app.foregroundMillis)} · " +
                            "${app.launches} open(s)" +
                            if (app.notifications > 0) " · ${app.notifications} alert(s)" else "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (limitMinutes != null) {
                    Text(
                        text = "limit ${limitMinutes}m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ScreenTimeLogic.limitReached(app.foregroundMillis, limitMinutes)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ProgressLine(ScreenTimeLogic.share(app, totalMillis))
        }
    }
}

@Composable
private fun LimitDialog(
    app: AppUsage,
    current: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limit for ${app.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Used ${ScreenTimeLogic.formatDuration(app.foregroundMillis)} today" +
                        if (current != null) " · current limit ${current} min" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                    OutlinedButton(
                        onClick = { onPick(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (current == minutes) "$minutes min (current)" else "$minutes min",
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

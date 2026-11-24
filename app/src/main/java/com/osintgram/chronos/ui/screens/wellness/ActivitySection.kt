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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.wellness.ActivitySession
import com.osintgram.chronos.wellness.HealthConnectSource
import com.osintgram.chronos.wellness.WellnessStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Activity that other apps already recorded.
 *
 * Chronos does not run its own ride or gym tracker. On Android the phone
 * already has one place where Google Fit, Samsung Health, a watch and a bike
 * computer all write what they measured — Health Connect — so this screen reads
 * exercise sessions, distance and active energy from there. When nothing has
 * been recorded it says so instead of inventing a number.
 */
@Composable
fun ActivitySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var distanceMeters by remember { mutableStateOf<Double?>(null) }
    var energyKcal by remember { mutableStateOf<Double?>(null) }
    var granted by remember { mutableStateOf(false) }

    val sessions = remember(refresh) { WellnessStore.allActivities() }

    LaunchedEffect(refresh) {
        granted = HealthConnectSource.granted(context)
        if (!granted) return@LaunchedEffect
        val today = HealthConnectSource.readToday(context)
        distanceMeters = today?.distanceMeters
        energyKcal = today?.energyKcal
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { WellnessLabel("TODAY, FROM YOUR PHONE") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityTile(
                    icon = Icons.Filled.DirectionsWalk,
                    value = distanceMeters?.let { formatDistance(it) } ?: "—",
                    label = "distance",
                    modifier = Modifier.weight(1f),
                )
                ActivityTile(
                    icon = Icons.Filled.LocalFireDepartment,
                    value = energyKcal?.let { "${it.toInt()} kcal" } ?: "—",
                    label = "active energy",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!granted) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Health Connect is not connected",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        WellnessNote(
                            "Walks, rides, runs and gym sessions reach Chronos through Health " +
                                "Connect — the same place Google Fit, Samsung Health and your watch " +
                                "write to. Connect it on the Today card, then come back here.",
                        )
                    }
                }
            }
        }

        item { WellnessLabel("RECORDED ACTIVITY") }
        if (sessions.isEmpty()) {
            item {
                WellnessNote(
                    if (granted) "No workout recorded by another app in the last week."
                    else "Nothing to show yet.",
                )
            }
        } else {
            items(sessions, key = { it.id }) { session -> ActivityRow(session) }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        Haptics.tick(context)
                        scope.launch {
                            busy = true
                            val read = HealthConnectSource.readActivities(context)
                            WellnessStore.mergeActivities(read)
                            refresh++
                            message = when {
                                !granted -> "Connect Health Connect first"
                                read.isEmpty() -> "Nothing new from Health Connect"
                                else -> "${read.size} session(s) synced"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sync from Health Connect", fontSize = 12.sp)
                }
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            WellnessNote(
                "These sessions are read from Health Connect and stored with the rest of your " +
                    "wellness data, so the backup file carries them too. Chronos never writes a " +
                    "session you did not record.",
            )
        }
    }
}

@Composable
private fun ActivityTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityRow(session: ActivitySession) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(session),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${whenLabel(session.startAt)} · ${session.minutes} min",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = session.source,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A rough icon for the session. Chosen from the readable label rather than the
 * raw Health Connect type code, so an unknown code cannot pick the wrong icon
 * and the two can never disagree.
 */
private fun iconFor(session: ActivitySession): ImageVector {
    val label = session.label.lowercase(Locale.US)
    return when {
        label.contains("cycl") || label.contains("bike") -> Icons.Filled.DirectionsBike
        label.contains("run") || label.contains("treadmill") -> Icons.Filled.DirectionsRun
        label.contains("yoga") || label.contains("swim") -> Icons.Filled.SelfImprovement
        label.contains("strength") || label.contains("gym") -> Icons.Filled.FitnessCenter
        else -> Icons.Filled.DirectionsWalk
    }
}

private val WHEN_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

private fun whenLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(WHEN_FORMAT)

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000.0)
    else "${meters.toInt()} m"

package com.osintgram.chronos.ui.screens

import android.Manifest
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.SleepSession
import com.osintgram.chronos.data.SleepStore
import com.osintgram.chronos.sleep.SleepTracker
import com.osintgram.chronos.ui.components.SectionCard
import com.osintgram.chronos.ui.theme.Motion
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun durationText(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun clockText(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

@Composable
fun SleepScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tracker by SleepTracker.state.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    val sessions = remember(version) { SleepStore.all() }
    var selectedDate by remember { mutableStateOf(sessions.firstOrNull()?.date() ?: LocalDate.now()) }
    var playing by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) SleepTracker.start(context)
    }

    fun stopPlayback() {
        runCatching { player?.release() }
        player = null
        playing = null
    }

    fun play(path: String) {
        if (playing == path) {
            stopPlayback()
            return
        }
        stopPlayback()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener { stopPlayback() }
            }
            playing = path
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // ---- Live recorder -------------------------------------------------
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = if (tracker.recording) "Recording tonight" else "Sleep & snoring",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (tracker.recording) {
                        "Keep the phone charging near your bed. Snore clips are saved on this device."
                    } else {
                        "Chronos listens for snoring and watches movement while you sleep, then " +
                            "shows you what it heard. It does not claim to read sleep stages."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                if (tracker.recording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = durationText(tracker.elapsedMs),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${tracker.snoreCount} snore(s) · mic level ${tracker.level}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Screen off ${durationText(tracker.screenOffMs)} · " +
                                    "${tracker.wakeUps} wake-up(s) · " +
                                    if (tracker.screenIsOff) "screen is off" else "screen is on",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { SleepTracker.stop(context) }) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Text("  Stop")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LevelMeter(level = tracker.level)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            if (SleepTracker.canRecord(context)) {
                                SleepTracker.start(context)
                            } else {
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Text("  Start recording")
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Microphone permission is needed",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No nights recorded yet.\nStart a recording before you sleep and your " +
                        "report will appear here in the morning.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // ---- Week strip ----------------------------------------------------
        val today = LocalDate.now()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            (6 downTo 0).forEach { back ->
                val date = today.minusDays(back.toLong())
                val has = SleepStore.forDate(date) != null
                val selected = date == selectedDate
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { selectedDate = date }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 13.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                CircleShape,
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(5.dp)
                            .background(
                                if (has) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                CircleShape,
                            ),
                    )
                }
            }
        }

        val session = SleepStore.forDate(selectedDate)
        if (session == null) {
            Text(
                text = "No recording for ${selectedDate.format(DateTimeFormatter.ofPattern("d MMMM"))}.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        SessionReport(
            session = session,
            playing = playing,
            onPlay = { path -> play(path) },
            onDelete = {
                stopPlayback()
                session.snores.forEach { s -> s.clipPath?.let { File(it).delete() } }
                SleepStore.delete(session.id)
                version += 1
                selectedDate = LocalDate.now()
            },
        )
    }
}

@Composable
private fun LevelMeter(level: Int) {
    val animated by animateFloatAsState(
        targetValue = (level / 100f).coerceIn(0.02f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "meter",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(8.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun SessionReport(
    session: SleepSession,
    playing: String?,
    onPlay: (String) -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(title = session.date().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Time in bed",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = durationText(session.durationMs),
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${clockText(session.startMillis)} → ${clockText(session.endMillis)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric("Snore events", "${session.snores.size}", Modifier.weight(1f))
                Metric("Snore time", durationText(session.snoreMs), Modifier.weight(1f))
                Metric(
                    "Movement",
                    "${session.movement.sum() / 10}",
                    Modifier.weight(1f),
                )
            }
        }
    }

    SectionCard(title = "SCREEN ACTIVITY") {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric(
                    "Screen off (sleep proxy)",
                    durationText(session.screenOffMillis),
                    Modifier.weight(1f),
                )
                Metric("Wake-ups", "${session.wakeUps}", Modifier.weight(1f))
                Metric("Unlocks", "${session.unlocks}", Modifier.weight(1f))
            }

            val onset = session.sleepOnsetMillis()
            if (onset != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "First screen-off at ${clockText(onset)} — " +
                        "${durationText((onset - session.startMillis).coerceAtLeast(0L))} " +
                        "after recording started.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (session.screenOnEvents.isNotEmpty() || session.screenOffEvents.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                ScreenTimeline(session)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Dark = screen off, blue = screen on. Every block is a real " +
                        "screen event from the device, so this shows when the phone was in " +
                        "use — a proxy for wake-ups, not a measurement of sleep stages.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (session.wakeUps > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Screen-on moments",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                session.screenOnEvents.take(12).forEach { at ->
                    Text(
                        text = "· ${clockText(at)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.screenOnEvents.size > 12) {
                    Text(
                        text = "… and ${session.screenOnEvents.size - 12} more",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (session.noise.isNotEmpty()) {
        SectionCard(title = "ROOM NOISE") {
            Column(modifier = Modifier.padding(16.dp)) {
                val reveal by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(700, easing = Motion.easeOut),
                    label = "noiseReveal",
                )
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                ) {
                    val max = (session.noise.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                    val stepX = size.width / session.noise.size.coerceAtLeast(1)
                    session.noise.forEachIndexed { index, value ->
                        val h = (value / max) * size.height * reveal
                        drawRect(
                            color = Color(0xFF4C8DFF),
                            topLeft = Offset(index * stepX, size.height - h),
                            size = androidx.compose.ui.geometry.Size(
                                width = (stepX - 1f).coerceAtLeast(1f),
                                height = h,
                            ),
                        )
                    }
                }
                Text(
                    text = "One bar per 30 seconds. Measured from the microphone, not estimated.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    SectionCard(title = "SNORE CLIPS") {
        Column(modifier = Modifier.padding(16.dp)) {
            if (session.snores.isEmpty()) {
                Text(
                    text = "No snoring detected in this session.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                session.snores.forEach { snore ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = clockText(snore.atMillis),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "level ${snore.peakLevel}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val clip = snore.clipPath
                        if (clip != null && File(clip).exists()) {
                            IconButton(onClick = { onPlay(clip) }) {
                                Icon(
                                    imageVector = if (playing == clip) Icons.Filled.Pause
                                    else Icons.Filled.PlayArrow,
                                    contentDescription = "Play clip",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            Text(
                                text = "clip removed",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("  Delete this night", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Draws the night as one bar: screen-off stretches muted, screen-on stretches
 * highlighted. Built from the real screen events recorded overnight.
 */
@Composable
private fun ScreenTimeline(session: SleepSession) {
    val start = session.startMillis
    val total = (session.endMillis - start).coerceAtLeast(1L)

    val onSpans = remember(session.id) {
        val marks = mutableListOf<Pair<Long, Boolean>>()
        session.screenOffEvents.forEach { marks.add(it to false) }
        session.screenOnEvents.forEach { marks.add(it to true) }
        val sorted = marks.sortedBy { it.first }
        val spans = mutableListOf<Pair<Float, Float>>()
        var onFrom: Long? = null
        sorted.forEach { (at, on) ->
            if (on) {
                onFrom = at
            } else if (onFrom != null) {
                spans.add(
                    ((onFrom!! - start).toFloat() / total) to ((at - start).toFloat() / total),
                )
                onFrom = null
            }
        }
        onFrom?.let { from ->
            spans.add(((from - start).toFloat() / total) to 1f)
        }
        spans
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp),
            ),
    ) {
        onSpans.forEach { (from, to) ->
            val left = (from.coerceIn(0f, 1f)) * size.width
            val right = (to.coerceIn(0f, 1f)) * size.width
            val width = (right - left).coerceAtLeast(2f)
            drawRect(
                color = Color(0xFF4C8DFF),
                topLeft = Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(width, size.height),
            )
        }
    }
}

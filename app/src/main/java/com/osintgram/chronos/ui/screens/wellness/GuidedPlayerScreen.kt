package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.ui.theme.pressable
import com.osintgram.chronos.wellness.GuidedSession
import com.osintgram.chronos.wellness.SadhanaSession
import com.osintgram.chronos.wellness.WellnessSound
import com.osintgram.chronos.wellness.WellnessStore
import kotlinx.coroutines.delay

/**
 * The guided player: one step at a time, timed, with the cue kept on screen.
 *
 * A guided practice is only usable if the screen tells you what to do *now* —
 * so the step title and its cue are the largest things on screen, the countdown
 * is secondary, and the bell marks each change of step. The whole session's
 * progress is a thin bar at the top, so you always know how much is left
 * without reading anything.
 */
@Composable
fun GuidedPlayerScreen(session: GuidedSession, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var index by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(session.steps.firstOrNull()?.seconds ?: 0) }
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf<SadhanaSession?>(null) }

    val step = session.steps.getOrNull(index)
    val elapsedBefore = session.steps.take(index).sumOf { it.seconds }
    val total = session.totalSeconds.coerceAtLeast(1)

    // A posture is not readable if the screen sleeps.
    LaunchedEffect(running) {
        view.keepScreenOn = running
    }

    LaunchedEffect(running, index) {
        while (running) {
            delay(1_000)
            if (remaining > 1) {
                remaining -= 1
            } else {
                // End of this step: bell, then on to the next one.
                WellnessSound.playBell(context)
                Haptics.tick(context)
                if (index < session.steps.lastIndex) {
                    index += 1
                    remaining = session.steps[index].seconds
                } else {
                    running = false
                    val record = SadhanaSession(
                        id = System.currentTimeMillis(),
                        kind = "guided:${session.id}",
                        startedAt = if (startedAt > 0) startedAt else System.currentTimeMillis(),
                        seconds = total,
                    )
                    WellnessStore.addGuided(record)
                    done = record
                    Haptics.success(context)
                }
            }
        }
    }

    fun start() {
        Haptics.tick(context)
        done = null
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        running = true
    }

    fun finishEarly() {
        running = false
        val held = elapsedBefore + (session.steps.getOrNull(index)?.seconds ?: 0) - remaining
        if (held > 0) {
            val record = SadhanaSession(
                id = System.currentTimeMillis(),
                kind = "guided:${session.id}",
                startedAt = if (startedAt > 0) startedAt else System.currentTimeMillis(),
                seconds = held,
            )
            WellnessStore.addGuided(record)
            done = record
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        LinearProgressIndicator(
            progress = { ((elapsedBefore + (step?.seconds ?: 0) - remaining).toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
        )
        Spacer(Modifier.height(Spacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            ) { Text("Back", style = ChronosType.label) }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(session.title, style = ChronosType.title, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Step ${index + 1} of ${session.steps.size} · ${session.lengthLabel}",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radii.lg),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = step?.title.orEmpty(),
                    style = ChronosType.headline,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = step?.instruction.orEmpty(),
                    style = ChronosType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!step?.cue.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        text = step?.cue.orEmpty(),
                        style = ChronosType.numeric,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = SadhanaSession.formatSeconds(remaining),
                    style = ChronosType.display,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        if (!running) {
            Button(
                onClick = { start() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) {
                Text(
                    if (done != null) "Again" else if (index == 0 && remaining == step?.seconds) "Begin" else "Continue",
                    style = ChronosType.title,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = {
                        Haptics.tick(context)
                        if (index > 0) {
                            index -= 1
                            remaining = session.steps[index].seconds
                        }
                    },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = Spacing.sm),
                ) { Text("Previous", style = ChronosType.label) }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(context)
                        if (index < session.steps.lastIndex) {
                            index += 1
                            remaining = session.steps[index].seconds
                        } else {
                            finishEarly()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = Spacing.sm),
                ) { Text("Next", style = ChronosType.label) }
                OutlinedButton(
                    onClick = { finishEarly() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = Spacing.sm),
                ) { Text("End", style = ChronosType.label) }
            }
        }

        done?.let { record ->
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Recorded ${SadhanaSession.formatSeconds(record.seconds)} of ${session.title}",
                style = ChronosType.label,
                color = StatusColors.success,
            )
        }

        if (session.note.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            WellnessNote(session.note)
        }
    }
}

/**
 * The library: every guided practice of one kind, with its length and level.
 *
 * It is a plain list on purpose — someone who wants to practise should be one
 * tap from starting, not three screens deep in a catalogue.
 */
@Composable
fun GuidedLibraryList(
    sessions: List<GuidedSession>,
    onOpen: (GuidedSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (sessions.isEmpty()) {
        WellnessNote("No guided sessions in this build yet.")
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.sm),
    ) {
        items(sessions, key = { it.id }) { session ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { Haptics.tick(context); onOpen(session) },
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = ChronosType.title,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = session.subtitle,
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "${session.steps.size} steps · ${session.level}",
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = session.lengthLabel,
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

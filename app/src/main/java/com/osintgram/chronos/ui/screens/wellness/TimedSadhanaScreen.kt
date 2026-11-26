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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.wellness.SadhanaKind
import com.osintgram.chronos.wellness.SadhanaSession
import com.osintgram.chronos.wellness.WellnessSound
import com.osintgram.chronos.wellness.WellnessStore
import kotlinx.coroutines.delay

/**
 * A timed sit — Dhyana, Mauna, Yoga Nidra, Chakra and Om all use this.
 *
 * They share a mechanic (sit for a length of time, opened and closed by the
 * bell) and differ only in what the screen tells you to do, which is data in
 * [SadhanaKind]. The clock counts **up** rather than down: a sit is not a
 * countdown to escape, and a person who sits longer than the bell has still
 * done the practice.
 */
@Composable
fun TimedSadhanaScreen(kind: SadhanaKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var minutes by remember { mutableIntStateOf((kind.defaultSeconds / 60).coerceAtLeast(1)) }
    var saved by remember { mutableStateOf<SadhanaSession?>(null) }

    val best = remember { WellnessStore.sadhanaBest(kind.id) }
    val history = remember { WellnessStore.sadhanaFor(kind.id, 5) }

    LaunchedEffect(running) {
        view.keepScreenOn = running
        while (running) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(200)
        }
    }

    fun begin() {
        Haptics.tick(context)
        saved = null
        startedAt = System.currentTimeMillis()
        elapsed = 0
        running = true
        WellnessSound.playBell(context)
    }

    fun end() {
        running = false
        WellnessSound.playBell(context)
        val session = SadhanaSession(
            id = System.currentTimeMillis(),
            kind = kind.id,
            startedAt = startedAt,
            seconds = (elapsed / 1000).toInt(),
        )
        if (session.seconds > 0) {
            WellnessStore.addSadhana(session)
            saved = session
            Haptics.success(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            ) { Text("Back", style = ChronosType.label) }
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(kind.title, style = ChronosType.headline, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    kind.subtitle,
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = SadhanaSession.formatSeconds((elapsed / 1000).toInt()),
            style = ChronosType.display,
            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (running) "Sitting · bell in ${minutes} min" else "Ready when you are",
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.lg))

        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(5, 10, 15, 20, 30).forEach { option ->
                    OutlinedButton(
                        onClick = { minutes = option },
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) {
                        Text(
                            text = "$option",
                            style = ChronosType.label,
                            color = if (minutes == option) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Button(
                onClick = { begin() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) { Text(if (saved == null) "Begin" else "Sit again", style = ChronosType.title) }
        } else {
            OutlinedButton(
                onClick = { end() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) { Text("End the sit", style = ChronosType.body) }
        }

        saved?.let { session ->
            Spacer(Modifier.height(Spacing.md))
            WellnessLabel("SAVED")
            Text(
                text = "${session.summary} of ${kind.title.lowercase()}",
                style = ChronosType.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        WellnessNote(
            "The bell opens and closes the sit, using the tone chosen in Settings → Wellness. " +
                "The clock counts up, so sitting longer than the bell is not a failure.",
        )
        if (kind == SadhanaKind.Chakra) {
            WellnessNote(
                "Move through the seven centres slowly — root to crown — letting attention rest " +
                    "at each one for about a minute.",
            )
        }
        if (kind == SadhanaKind.YogaNidra) {
            WellnessNote(
                "Lie down, stay still, and keep the attention on the body rather than on sleep. " +
                    "Falling asleep is common and not a failure.",
            )
        }
        if (kind == SadhanaKind.Mauna) {
            WellnessNote("Silence for the length of the sit: no speech, no typing, no scrolling.")
        }

        if (best != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Longest sit so far: ${best.summary}",
                style = ChronosType.label,
                color = StatusColors.success,
            )
        }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            WellnessLabel("RECENT")
            history.forEach { session ->
                Text(
                    text = session.summary,
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

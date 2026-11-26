package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.osintgram.chronos.wellness.SadhanaKind
import com.osintgram.chronos.wellness.SadhanaSession
import com.osintgram.chronos.wellness.WellnessStore
import kotlinx.coroutines.delay

/**
 * Trataka — steady gaze on one point, without blinking.
 *
 * The number that matters is how long the gaze was held, so the timer restarts
 * on every blink and the session records the **longest** unbroken hold, not the
 * total time spent looking. Tapping "I blinked" is the only input: Chronos has
 * no way to see your eyes, and a camera watching them would be a worse trade
 * than an honest button. The screen says that in as many words.
 */
@Composable
fun TratakaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var running by remember { mutableStateOf(false) }
    var holdStart by remember { mutableLongStateOf(0L) }
    var holdMillis by remember { mutableLongStateOf(0L) }
    var bestMillis by remember { mutableLongStateOf(0L) }
    var blinks by remember { mutableIntStateOf(0) }
    var sessionStart by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf<SadhanaSession?>(null) }

    val best = remember { WellnessStore.sadhanaBest(SadhanaKind.Trataka.id) }
    val history = remember { WellnessStore.sadhanaFor(SadhanaKind.Trataka.id, 5) }

    // A gaze practice is ruined by the screen going dark.
    LaunchedEffect(running) {
        view.keepScreenOn = running
    }

    LaunchedEffect(running, holdStart) {
        while (running) {
            holdMillis = System.currentTimeMillis() - holdStart
            delay(100)
        }
    }

    fun blink() {
        Haptics.tick(context)
        val held = System.currentTimeMillis() - holdStart
        if (held > bestMillis) bestMillis = held
        blinks += 1
        holdStart = System.currentTimeMillis()
        holdMillis = 0
    }

    fun finish() {
        val held = if (running) {
            (System.currentTimeMillis() - holdStart).coerceAtLeast(holdMillis)
        } else {
            bestMillis
        }
        if (held > bestMillis) bestMillis = held
        running = false
        val session = SadhanaSession(
            id = System.currentTimeMillis(),
            kind = SadhanaKind.Trataka.id,
            startedAt = if (sessionStart > 0) sessionStart else System.currentTimeMillis(),
            seconds = (bestMillis / 1000).toInt(),
            blinks = blinks,
        )
        WellnessStore.addSadhana(session)
        saved = session
        Haptics.success(context)
    }

    fun start() {
        Haptics.tick(context)
        saved = null
        blinks = 0
        bestMillis = 0
        holdMillis = 0
        holdStart = System.currentTimeMillis()
        sessionStart = holdStart
        running = true
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
                Text("Trataka", style = ChronosType.headline, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Steady gaze, no blinking",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // The point to look at: a warm, steady glow. It does not animate —
        // a moving target would defeat the practice.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radii.xl),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Canvas(Modifier.size(220.dp)) {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFE9B0),
                                Color(0xFFFFC24B),
                                Color(0x00FFC24B),
                            ),
                            center = centre,
                            radius = radius,
                        ),
                        radius = radius,
                        center = centre,
                    )
                    drawCircle(
                        color = Color(0xFFFFF4D6),
                        radius = radius * 0.12f,
                        center = centre,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = SadhanaSession.formatSeconds((holdMillis / 1000).toInt()),
            style = ChronosType.display,
            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = when {
                !running && saved == null -> "This hold restarts at every blink"
                running -> "Hold the gaze. Tap the button the moment you blink"
                else -> "Best hold this session"
            },
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = "Best ${SadhanaSession.formatSeconds((bestMillis / 1000).toInt())}",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (blinks == 1) "1 blink" else "$blinks blinks",
                style = ChronosType.label,
                color = if (blinks > 0) StatusColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.md))

        if (running) {
            Button(
                onClick = { blink() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) { Text("I blinked", style = ChronosType.title) }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = { finish() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) { Text("Finish", style = ChronosType.body) }
        } else {
            Button(
                onClick = { start() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) { Text(if (saved == null) "Begin" else "Begin again", style = ChronosType.title) }
        }

        saved?.let { session ->
            Spacer(Modifier.height(Spacing.md))
            WellnessLabel("SAVED")
            Text(
                text = "Longest hold ${session.summary}",
                style = ChronosType.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(Spacing.md))
        WellnessLabel("BEFORE YOU START")
        WellnessNote(
            "Sit with the point at eye level, an arm's length away, and let the gaze rest " +
                "there without straining. Blinking is counted by you, not by the phone — " +
                "Chronos cannot see your eyes.",
        )
        WellnessNote(
            "Stop if your eyes water heavily, sting, or you feel dizzy. Trataka is a gaze " +
                "practice, not a test of endurance.",
        )

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            WellnessLabel("RECENT HOLDS")
            history.forEach { session ->
                Text(
                    text = "${session.summary} · ${session.blinks} blink" +
                        if (session.blinks == 1) "" else "s",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (best != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Your longest hold so far: ${best.summary}",
                style = ChronosType.label,
                color = StatusColors.success,
            )
        }
    }
}

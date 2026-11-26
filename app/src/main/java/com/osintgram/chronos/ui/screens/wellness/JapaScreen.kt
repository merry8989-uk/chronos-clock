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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.ui.components.RingStat
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

/**
 * Japa — mantra repetition counted on a mala of 108.
 *
 * One tap per bead. The ring shows the round in progress and the bell marks
 * every completed round of 108, because a mala is counted in rounds, not in
 * taps: "3 rounds and 42 beads" is the honest result, and that is what is saved.
 */
@Composable
fun JapaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var beads by remember { mutableIntStateOf(0) }
    var rounds by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf<SadhanaSession?>(null) }

    val best = remember { WellnessStore.sadhanaBest(SadhanaKind.Japa.id) }
    val history = remember { WellnessStore.sadhanaFor(SadhanaKind.Japa.id, 5) }
    val perRound = 108

    fun tap() {
        Haptics.tick(context)
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        beads += 1
        if (beads >= perRound) {
            beads = 0
            rounds += 1
            WellnessSound.playBell(context)
            Haptics.success(context)
        }
    }

    fun finish() {
        val seconds = if (startedAt > 0) {
            ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        } else {
            0
        }
        // A round is the unit; a partial round still counts as the beads tapped.
        val session = SadhanaSession(
            id = System.currentTimeMillis(),
            kind = SadhanaKind.Japa.id,
            startedAt = if (startedAt > 0) startedAt else System.currentTimeMillis(),
            seconds = seconds,
            rounds = rounds,
        )
        if (rounds > 0) WellnessStore.addSadhana(session)
        saved = session
        startedAt = 0
        beads = 0
        rounds = 0
        Haptics.success(context)
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
                Text("Japa", style = ChronosType.headline, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "108 beads to a round",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RingStat(
                progress = beads / perRound.toFloat(),
                modifier = Modifier.size(180.dp),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "$beads / $perRound",
                style = ChronosType.numeric,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (rounds == 1) "1 round done" else "$rounds rounds done",
                style = ChronosType.label,
                color = if (rounds > 0) StatusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Button(
            onClick = { tap() },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentPadding = PaddingValues(vertical = Spacing.md),
        ) { Text("One bead", style = ChronosType.title) }

        Spacer(Modifier.height(Spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = {
                    beads = 0
                    startedAt = 0
                    rounds = 0
                    saved = null
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = Spacing.sm),
            ) { Text("Reset", style = ChronosType.label) }
            OutlinedButton(
                onClick = { finish() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = Spacing.sm),
            ) { Text("Finish", style = ChronosType.label) }
        }

        saved?.let { session ->
            Spacer(Modifier.height(Spacing.md))
            WellnessLabel("SAVED")
            Text(
                text = if (session.rounds > 0) {
                    "${session.rounds} round${if (session.rounds == 1) "" else "s"} " +
                        "in ${SadhanaSession.formatSeconds(session.seconds)}"
                } else {
                    "No completed round, so nothing was saved"
                },
                style = ChronosType.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        WellnessNote(
            "Tap once per repetition, not once per bead of a physical mala — the count is yours. " +
                "A round of 108 is marked with the bell from Settings → Wellness.",
        )

        if (best != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Best so far: ${best.summary}",
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

package com.osintgram.chronos.ui.screens.wellness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import com.osintgram.chronos.ui.theme.pressable
import com.osintgram.chronos.wellness.SadhanaKind
import com.osintgram.chronos.wellness.WellnessStore
import androidx.compose.ui.platform.LocalContext

/**
 * Sadhana — the practices, each with the number that practice is measured by.
 *
 * The list is the whole feature's front door: every practice shows the best
 * result you have recorded for it, so the screen answers "what have I actually
 * done" before it asks you to do more.
 */
@Composable
fun SadhanaSection(onOpen: (SadhanaKind) -> Unit) {
    val context = LocalContext.current
    val sessions = WellnessStore.allSadhana()
    val totalMinutes = sessions.sumOf { it.seconds } / 60

    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { WellnessLabel("SADHANA") }
        item {
            Text(
                text = if (totalMinutes == 0) {
                    "Nothing practised yet. Each practice keeps its own measure — seconds of " +
                        "unbroken gaze, rounds of a mala, minutes of sitting."
                } else {
                    "$totalMinutes minute${if (totalMinutes == 1) "" else "s"} of practice recorded, " +
                        "across ${sessions.size} session${if (sessions.size == 1) "" else "s"}."
                },
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(SadhanaKind.entries, key = { it.id }) { kind ->
            val best = WellnessStore.sadhanaBest(kind.id)
            val count = WellnessStore.sadhanaFor(kind.id, limit = 1000).size
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radii.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { Haptics.tick(context); onOpen(kind) },
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = kind.title,
                            style = ChronosType.title,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = kind.subtitle,
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = best?.summary ?: "—",
                            style = ChronosType.label,
                            fontWeight = FontWeight.SemiBold,
                            color = if (best != null) {
                                StatusColors.success
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = if (count == 0) "not started" else "$count session${if (count == 1) "" else "s"}",
                            style = ChronosType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.sm)) }
        item {
            WellnessNote(
                "A practice is only recorded when you finish it, and the number kept is the one " +
                    "the practice is measured by: your longest unbroken gaze, your rounds, or the " +
                    "minutes you sat. Nothing is estimated.",
            )
        }
    }
}

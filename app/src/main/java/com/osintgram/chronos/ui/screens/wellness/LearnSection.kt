package com.osintgram.chronos.ui.screens.wellness

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.ui.components.RowDivider
import com.osintgram.chronos.ui.screens.WellnessLabel
import com.osintgram.chronos.ui.screens.WellnessNote
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.pressable
import com.osintgram.chronos.wellness.WellnessPlans

/**
 * Learn: pose references drawn from icons, and follow-along suggestions that
 * open a search on the service they name. Nothing here pretends to be bundled
 * media — the app ships no video, audio or photo content of its own.
 *
 * The words on this screen are the content, and they are accurate, so only the
 * presentation is styled here: each group is one card holding a list of rows,
 * every row headed in [ChronosType.title] over [ChronosType.body] text, so the
 * page can be scanned by heading instead of read card by card.
 */
@Composable
fun LearnSection(onOpenGuided: (com.osintgram.chronos.wellness.GuidedSession) -> Unit = {}) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            WellnessLabel("GUIDED YOGA")
            Text(
                text = "Step-by-step sessions you can follow with the phone on the floor: one " +
                    "posture at a time, timed, with the alignment that matters on screen.",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            GuidedLibraryList(
                sessions = com.osintgram.chronos.wellness.GuidedLibrary.byKind(
                    com.osintgram.chronos.wellness.GuidedKind.Yoga,
                ),
                onOpen = onOpenGuided,
                modifier = Modifier.heightIn(max = 320.dp),
            )
        }
        item { WellnessLabel("YOGA POSES") }
        item {
            ListCard {
                WellnessPlans.poses.forEachIndexed { index, pose ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    RoundedCornerShape(Radii.md),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                poseIcon(pose.icon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = pose.name,
                                style = ChronosType.title,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = pose.benefit,
                                style = ChronosType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = pose.cue,
                                style = ChronosType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { WellnessLabel("FOLLOW ALONG") }
        item {
            ListCard {
                WellnessPlans.suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable {
                                Haptics.tick(context)
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(suggestion.url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = suggestion.title,
                                style = ChronosType.title,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${suggestion.detail} · ${suggestion.source}",
                                style = ChronosType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        item { WellnessLabel("BREATHING PATTERNS") }
        item {
            ListCard {
                WellnessPlans.patterns.forEachIndexed { index, pattern ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Air,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = pattern.title,
                                style = ChronosType.title,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = pattern.subtitle,
                                style = ChronosType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (pattern.caution.isNotBlank()) {
                                Text(
                                    text = pattern.caution,
                                    style = ChronosType.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            WellnessNote(
                "These cards are drawn in the app and described in words; the follow-along links " +
                    "open a search on YouTube in your browser, so the results are whatever that " +
                    "service shows at the time. Nothing is downloaded or played inside Chronos.",
            )
        }
    }
}

/** One card holding a group of rows, each separated by a divider. */
@Composable
private fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radii.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

private fun poseIcon(key: String): ImageVector = when (key) {
    "mountain" -> Icons.Filled.AccessibilityNew
    "tree" -> Icons.Filled.SelfImprovement
    "dog" -> Icons.Filled.Spa
    "child" -> Icons.Filled.SelfImprovement
    "cat" -> Icons.Filled.Spa
    "wall" -> Icons.Filled.AccessibilityNew
    "twist" -> Icons.Filled.Spa
    else -> Icons.Filled.SelfImprovement
}

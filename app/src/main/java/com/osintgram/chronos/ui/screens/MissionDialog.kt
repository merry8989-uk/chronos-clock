package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osintgram.chronos.data.MissionInfo
import com.osintgram.chronos.data.MissionSpec
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors

/** How many missions an alarm may require before it stops asking for more. */
private const val MaxMissions = 5

/** The tallest the catalog may grow before it scrolls, so the buttons stay on screen. */
private val CatalogMaxHeight = 320.dp

/** Side of the letter badge in front of a catalog row: square, so it is its own constant. */
private val CatalogBadgeSize = 38.dp

/** Tracking on the group headings, which read as small caps over the catalog. */
private val GroupTracking = 1.sp

/**
 * Chooses the missions that must be completed before this alarm can be
 * dismissed. Up to five, run in the order shown.
 *
 * Save is the only filled button: it is the one action that leaves the dialog
 * with a change applied. Cancel, and the per-row controls, are text or icon
 * affordances so nothing competes with it.
 */
@Composable
fun MissionDialog(
    current: List<MissionSpec>,
    onDismiss: () -> Unit,
    onSave: (List<MissionSpec>) -> Unit,
) {
    var missions by remember { mutableStateOf(current) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radii.xl),
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = "Wake-up missions",
                    style = ChronosType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Complete these before the alarm can be snoozed or dismissed. " +
                        "They run in order, up to five.",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.lg))

                if (missions.isEmpty()) {
                    Text(
                        text = "No mission selected. The alarm is dismissed with one tap.",
                        style = ChronosType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    missions.forEachIndexed { index, spec ->
                        val info = MissionSpec.info(spec.type)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Radii.lg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Mission ${index + 1}/${missions.size}",
                                        style = ChronosType.label,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        text = info?.title ?: spec.type,
                                        style = ChronosType.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        missions = missions.filterIndexed { i, _ -> i != index }
                                    }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove mission",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    (info?.countOptions ?: listOf(spec.count)).forEach { option ->
                                        Pill(
                                            text = "$option ${info?.unit ?: ""}".trim(),
                                            selected = spec.count == option,
                                            onClick = {
                                                missions = missions.toMutableList().also {
                                                    it[index] = spec.copy(count = option)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                if (missions.size < MaxMissions) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = CatalogMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        listOf(MissionInfo.BRAIN, MissionInfo.BODY).forEach { group ->
                            Text(
                                text = group,
                                style = ChronosType.label.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = GroupTracking,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    top = Spacing.sm,
                                    bottom = Spacing.xs,
                                ),
                            )
                            MissionSpec.catalog.filter { it.group == group }.forEach { info ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClickLabel = "Add ${info.title}",
                                            role = Role.Button,
                                        ) {
                                            missions = missions + MissionSpec(
                                                type = info.type,
                                                count = info.defaultCount,
                                            )
                                        }
                                        .padding(vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(CatalogBadgeSize)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(Radii.md),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = info.title.take(1),
                                            style = ChronosType.title.copy(
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Spacer(Modifier.width(Spacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = info.title,
                                            style = ChronosType.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = info.subtitle,
                                            style = ChronosType.label,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = "Add",
                                        style = ChronosType.body,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                // The dialog's own state, in words first: what will happen when
                // Save is pressed. Colour is the second signal, never the only one.
                Spacer(Modifier.height(Spacing.md))
                val statusColor = when {
                    missions.isEmpty() -> StatusColors.info
                    missions.size >= MaxMissions -> StatusColors.warning
                    else -> StatusColors.success
                }
                Text(
                    text = when {
                        missions.isEmpty() ->
                            "Nothing to solve yet. The alarm can be dismissed with one tap."
                        missions.size >= MaxMissions ->
                            "Five missions is the maximum. Remove one before adding another."
                        missions.size == 1 ->
                            "Ready. One mission will run before the alarm can be dismissed."
                        else ->
                            "Ready. ${missions.size} missions will run in order before the " +
                                "alarm can be dismissed."
                    },
                    style = ChronosType.label,
                    color = statusColor,
                )

                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(Spacing.sm))
                    Button(onClick = { onSave(missions) }) { Text("Save") }
                }
            }
        }
    }
}

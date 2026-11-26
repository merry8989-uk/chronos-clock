package com.osintgram.chronos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.screens.wellness.ActivitySection
import com.osintgram.chronos.ui.screens.wellness.BodySection
import com.osintgram.chronos.ui.screens.wellness.LearnSection
import com.osintgram.chronos.ui.screens.wellness.MeditateSection
import com.osintgram.chronos.ui.screens.wellness.GuidedPlayerScreen
import com.osintgram.chronos.ui.screens.wellness.JapaScreen
import com.osintgram.chronos.ui.screens.wellness.ProgressSection
import com.osintgram.chronos.ui.screens.wellness.SadhanaSection
import com.osintgram.chronos.ui.screens.wellness.TimedSadhanaScreen
import com.osintgram.chronos.ui.screens.wellness.TratakaScreen
import com.osintgram.chronos.ui.screens.wellness.SleepSection
import com.osintgram.chronos.ui.screens.wellness.TodaySection
import com.osintgram.chronos.ui.screens.wellness.WorkoutSection
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import androidx.compose.ui.platform.LocalContext

/**
 * The parts of the Wellness tab. Six of them are on the bar; [Workout] and
 * [Learn] are kept in the enum but marked [hidden], because they are reached
 * from the two buttons at the bottom of Today rather than from the bar.
 */
enum class WellnessSection(val label: String, val hidden: Boolean = false) {
    Today("Today"),
    Sleep("Sleep"),
    Activity("Activity"),
    Body("Body"),
    Meditate("Mind"),
    Sadhana("Sadhana"),
    Progress("Progress"),
    Workout("Workout", hidden = true),
    Learn("Learn", hidden = true),
}

/** The six entries the bar shows, in reading order. */
private val barSections: List<WellnessSection> = WellnessSection.entries.filterNot { it.hidden }

/**
 * No step of the type scale carries letter spacing, so the tracking the small
 * upper-case labels use is declared once here instead of at each call site.
 */
private val LABEL_TRACKING = 1.sp

/**
 * The Wellness tab. One bottom-bar entry holds the sections behind a segmented
 * bar, because eight extra bottom tabs would crowd the clock.
 */
@Composable
fun WellnessScreen() {
    var current by rememberSaveable { mutableStateOf(WellnessSection.Today.name) }
    // Which practice is open, if any. A practice takes the whole area: a gaze
    // hold or a sit is not something to do inside a scrolling list.
    var practice by rememberSaveable { mutableStateOf<String?>(null) }
    // A guided session takes the whole area, like a practice: you cannot follow
    // a posture from inside a scrolling list.
    var guided by rememberSaveable { mutableStateOf<String?>(null) }
    val section = WellnessSection.entries.firstOrNull { it.name == current } ?: WellnessSection.Today
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        WellnessSectionBar(selected = section) { picked ->
            if (picked != section) Haptics.tick(context)
            current = picked.name
        }
        if (section.hidden) {
            // Workout and Learn have no slot on the bar, so they carry their own
            // way back rather than leaving the user on a section they cannot leave.
            TextButton(
                onClick = {
                    Haptics.tick(context)
                    current = WellnessSection.Today.name
                },
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            ) {
                Text("Back to Today", style = ChronosType.label)
            }
        }
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                fadeIn(tween(Motion.MEDIUM, easing = Motion.easeOut)) togetherWith
                    fadeOut(tween(Motion.FAST))
            },
            label = "wellnessSection",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { target ->
            Box(modifier = Modifier.fillMaxSize()) {
                val openGuided = guided?.let { id ->
                    com.osintgram.chronos.wellness.GuidedLibrary.find(id)
                }
                if (openGuided != null) {
                    GuidedPlayerScreen(session = openGuided, onBack = { guided = null })
                    return@Box
                }
                val openKind = practice
                    ?.takeIf { target == WellnessSection.Sadhana }
                    ?.let { id -> com.osintgram.chronos.wellness.SadhanaKind.entries.firstOrNull { it.id == id } }
                if (openKind != null) {
                    when (openKind) {
                        com.osintgram.chronos.wellness.SadhanaKind.Trataka ->
                            TratakaScreen(onBack = { practice = null })
                        com.osintgram.chronos.wellness.SadhanaKind.Japa ->
                            JapaScreen(onBack = { practice = null })
                        else -> TimedSadhanaScreen(kind = openKind, onBack = { practice = null })
                    }
                    return@Box
                }
                when (target) {
                    WellnessSection.Today -> TodaySection(
                        onOpenWorkout = { current = WellnessSection.Workout.name },
                        onOpenSleep = { current = WellnessSection.Sleep.name },
                        onOpenBody = { current = WellnessSection.Body.name },
                        onOpenActivity = { current = WellnessSection.Activity.name },
                        onOpenLearn = { current = WellnessSection.Learn.name },
                        onOpenProgress = { current = WellnessSection.Progress.name },
                    )
                    WellnessSection.Sleep -> SleepSection()
                    WellnessSection.Activity -> ActivitySection()
                    WellnessSection.Body -> BodySection()
                    WellnessSection.Meditate -> MeditateSection(onOpenGuided = { guided = it.id })
                    WellnessSection.Sadhana -> SadhanaSection(onOpen = { practice = it.id })
                    WellnessSection.Progress -> ProgressSection()
                    WellnessSection.Workout -> WorkoutSection()
                    WellnessSection.Learn -> LearnSection(onOpenGuided = { guided = it.id })
                }
            }
        }
    }
}

/**
 * The section bar. It is the same [Pill] control as the Life area switcher —
 * filled and SemiBold when selected — and it scrolls sideways, because six
 * labels do not fit across a phone without being squeezed.
 */
@Composable
private fun WellnessSectionBar(selected: WellnessSection, onSelect: (WellnessSection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        barSections.forEach { entry ->
            Pill(
                text = entry.label,
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

/** A small labelled number used inside the wellness cards. */
@Composable
internal fun MetricBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = ChronosType.headline,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = label,
            style = ChronosType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Section title used by every wellness list. */
@Composable
internal fun WellnessLabel(text: String) {
    Text(
        text = text,
        style = ChronosType.label,
        letterSpacing = LABEL_TRACKING,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs, top = Spacing.md),
    )
}

/** Explanatory paragraph, kept quiet so it never competes with the numbers. */
@Composable
internal fun WellnessNote(text: String) {
    Text(
        text = text,
        // The label step carries the size; a note is quiet, so it is not set in
        // the label's medium weight.
        style = ChronosType.label.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

/** A row of two metric tiles. */
@Composable
internal fun MetricRow(
    leftValue: String,
    leftLabel: String,
    rightValue: String,
    rightLabel: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radii.lg),
            modifier = Modifier.weight(1f),
        ) {
            Box(Modifier.padding(Spacing.md)) {
                MetricBlock(leftValue, leftLabel)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radii.lg),
            modifier = Modifier.weight(1f),
        ) {
            Box(Modifier.padding(Spacing.md)) {
                MetricBlock(rightValue, rightLabel)
            }
        }
    }
}

/** Fixed-width spacer used between inline chips. */
@Composable
internal fun ChipSpacer() = Spacer(Modifier.width(Spacing.sm))

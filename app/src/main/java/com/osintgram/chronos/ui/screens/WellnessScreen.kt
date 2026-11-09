package com.osintgram.chronos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.ui.screens.wellness.LearnSection
import com.osintgram.chronos.ui.screens.wellness.MeditateSection
import com.osintgram.chronos.ui.screens.wellness.ProgressSection
import com.osintgram.chronos.ui.screens.wellness.TodaySection
import com.osintgram.chronos.ui.screens.wellness.WorkoutSection
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.pressable
import androidx.compose.ui.platform.LocalContext

/** The five parts of the Wellness tab. */
enum class WellnessSection(val label: String) {
    Today("Today"),
    Workout("Workout"),
    Meditate("Breathe"),
    Progress("Progress"),
    Learn("Learn"),
}

/**
 * The Wellness tab. One bottom-bar entry holds five sections behind a
 * segmented bar, because four extra bottom tabs would crowd the clock.
 */
@Composable
fun WellnessScreen() {
    var current by rememberSaveable { mutableStateOf(WellnessSection.Today.name) }
    val section = WellnessSection.entries.firstOrNull { it.name == current } ?: WellnessSection.Today
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        WellnessSectionBar(selected = section) { picked ->
            if (picked != section) Haptics.tick(context)
            current = picked.name
        }
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                fadeIn(tween(Motion.MEDIUM, easing = Motion.easeOut)) togetherWith
                    fadeOut(tween(Motion.FAST))
            },
            label = "wellnessSection",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (target) {
                    WellnessSection.Today -> TodaySection(
                        onOpenWorkout = { current = WellnessSection.Workout.name },
                        onOpenMeditate = { current = WellnessSection.Meditate.name },
                    )
                    WellnessSection.Workout -> WorkoutSection()
                    WellnessSection.Meditate -> MeditateSection()
                    WellnessSection.Progress -> ProgressSection()
                    WellnessSection.Learn -> LearnSection()
                }
            }
        }
    }
}

@Composable
private fun WellnessSectionBar(selected: WellnessSection, onSelect: (WellnessSection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        WellnessSection.entries.forEach { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    )
                    .pressable(scale = 0.94f) { onSelect(entry) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
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
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Section title used by every wellness list. */
@Composable
internal fun WellnessLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 14.dp),
    )
}

/** Explanatory paragraph, kept quiet so it never competes with the numbers. */
@Composable
internal fun WellnessNote(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(Modifier.padding(14.dp)) {
                MetricBlock(leftValue, leftLabel)
            }
        }
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(Modifier.padding(14.dp)) {
                MetricBlock(rightValue, rightLabel)
            }
        }
    }
}

/** Fixed-width spacer used between inline chips. */
@Composable
internal fun ChipSpacer() = Spacer(Modifier.width(8.dp))

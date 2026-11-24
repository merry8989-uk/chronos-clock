package com.osintgram.chronos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.screens.life.TasksSection
import com.osintgram.chronos.ui.screens.screentime.ScreenTimeScreen
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.Spacing

/**
 * The Life hub: tasks, wellness and screen time behind one bottom-bar entry, so the bar
 * stays at five items while both areas keep their own internal navigation.
 */
@Composable
fun LifeScreen() {
    var area by rememberSaveable { mutableStateOf(Area.Tasks.name) }
    val current = Area.entries.firstOrNull { it.name == area } ?: Area.Tasks
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // The area switcher is the same Pill control as the Wellness section bar,
        // so the two bars read as one app rather than two. The selected entry is
        // the filled one and the only one set in SemiBold, so the choice survives
        // a colour-blind reader or a very bright screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Area.entries.forEach { entry ->
                val active = entry == current
                Pill(
                    text = entry.label,
                    selected = active,
                    onClick = {
                        if (!active) Haptics.tick(context)
                        area = entry.name
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AnimatedContent(
            targetState = current,
            transitionSpec = {
                fadeIn(tween(Motion.MEDIUM, easing = Motion.easeOut)) togetherWith
                    fadeOut(tween(Motion.FAST))
            },
            label = "lifeArea",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            when (target) {
                Area.Tasks -> TasksSection()
                Area.Wellness -> WellnessScreen()
                Area.Screen -> ScreenTimeScreen()
            }
        }
    }
}

/** The three areas of the Life hub. */
enum class Area(val label: String) {
    Tasks("Tasks"),
    Wellness("Wellness"),
    Screen("Screen"),
}

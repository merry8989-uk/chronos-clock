package com.osintgram.chronos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.ui.screens.life.TasksSection
import com.osintgram.chronos.ui.screens.screentime.ScreenTimeScreen
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.pressable

/**
 * The Life hub: tasks and wellness behind one bottom-bar entry, so the bar
 * stays at five items while both areas keep their own internal navigation.
 */
@Composable
fun LifeScreen() {
    var area by rememberSaveable { mutableStateOf(Area.Tasks.name) }
    val current = Area.entries.firstOrNull { it.name == area } ?: Area.Tasks
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Area.entries.forEach { entry ->
                val active = entry == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .pressable(scale = 0.95f) {
                            if (!active) Haptics.tick(context)
                            area = entry.name
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
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

/** The two halves of the Life hub. */
enum class Area(val label: String) {
    Tasks("Tasks"),
    Wellness("Wellness"),
    Screen("Screen"),
}

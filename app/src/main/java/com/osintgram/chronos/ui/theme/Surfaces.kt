package com.osintgram.chronos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A short spacing scale, so surfaces that are built together line up with each
 * other instead of each one picking its own padding.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 22.dp
}

/**
 * The soft wash used behind a hero surface: the user's accent at low alpha
 * fading into nothing, so the card reads as raised without introducing a
 * second background colour. It is translucent on purpose — draw it over
 * [MaterialTheme.colorScheme.surface] and it composites into the normal card
 * colour, following whatever accent the user picked.
 *
 * @param topAlpha how strong the accent is at the top edge, 0f..1f.
 */
@Composable
fun heroSurfaceBrush(topAlpha: Float = 0.16f): Brush =
    Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = topAlpha.coerceIn(0f, 1f)),
            Color.Transparent,
        ),
    )

package com.osintgram.chronos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.osintgram.chronos.data.Settings

/** Accent choices offered in Settings. */
val AccentOptions: List<Triple<String, String, Color>> = listOf(
    Triple("blue", "Chronos blue", Color(0xFF4C8DFF)),
    Triple("violet", "Violet", Color(0xFF8B7CFF)),
    Triple("teal", "Teal", Color(0xFF22C7A9)),
    Triple("amber", "Amber", Color(0xFFFFB020)),
    Triple("rose", "Rose", Color(0xFFFF6E8A)),
    Triple("green", "Green", Color(0xFF4CD07D)),
)

fun accentColor(key: String): Color =
    AccentOptions.firstOrNull { it.first == key }?.third ?: AccentOptions.first().third

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C6BE0),
    onPrimary = Color.White,
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF14171C),
    surface = Color.White,
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = Color(0xFF5B6472),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4C8DFF),
    onPrimary = Color(0xFF08121F),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFEDEFF3),
    surface = Color(0xFF171B23),
    onSurface = Color(0xFFEDEFF3),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFF9AA3B2),
)

/**
 * Applies the user's theme mode, accent colour and text size. Every value is
 * read from [Settings], so changing one redraws the whole app at once.
 */
@Composable
fun ChronosTheme(content: @Composable () -> Unit) {
    val mode = Settings.themeMode.value
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accent = accentColor(Settings.accent.value)
    val base = if (dark) DarkColors else LightColors
    val scheme = base.copy(
        primary = accent,
        onPrimary = if (dark) Color(0xFF07101C) else Color.White,
        background = if (dark && Settings.amoledBlack.value) Color.Black else base.background,
        surface = if (dark && Settings.amoledBlack.value) Color(0xFF101319) else base.surface,
    )

    val density = LocalDensity.current
    val scale = Settings.textScale.value.coerceIn(0.8f, 1.4f)
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * scale,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}

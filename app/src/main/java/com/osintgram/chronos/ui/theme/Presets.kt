package com.osintgram.chronos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The named looks offered in Settings.
 *
 * The design direction asks for semantic tokens and a small set of named
 * themes rather than a colour picker that can produce an unreadable app. Each
 * preset therefore fixes the accent *and* the surface tint, so a preset cannot
 * end up with body text that fails contrast: the light and dark surfaces are
 * chosen here, not by the user.
 */
data class ChronosPreset(
    val id: String,
    val name: String,
    val character: String,
    val accent: Color,
    /** Background and surface for the dark rendering. */
    val darkBackground: Color,
    val darkSurface: Color,
    /** Background and surface for the light rendering. */
    val lightBackground: Color,
    val lightSurface: Color,
    /** True when this preset is meant to run on pure black. */
    val amoledFriendly: Boolean = false,
)

val ChronosPresets: List<ChronosPreset> = listOf(
    ChronosPreset(
        id = "midnight", name = "Chronos Midnight", character = "Premium, focused",
        accent = Color(0xFF4C8DFF),
        darkBackground = Color(0xFF0B0E14), darkSurface = Color(0xFF171B23),
        lightBackground = Color(0xFFF5F6F8), lightSurface = Color.White,
    ),
    ChronosPreset(
        id = "obsidian", name = "Obsidian", character = "Minimal AMOLED",
        accent = Color(0xFFA9B4C6),
        darkBackground = Color.Black, darkSurface = Color(0xFF0E1014),
        lightBackground = Color(0xFFF2F3F5), lightSurface = Color.White,
        amoledFriendly = true,
    ),
    ChronosPreset(
        id = "aurora", name = "Aurora", character = "Teal and violet",
        accent = Color(0xFF22C7A9),
        darkBackground = Color(0xFF0A1116), darkSurface = Color(0xFF14202A),
        lightBackground = Color(0xFFF1F7F6), lightSurface = Color.White,
    ),
    ChronosPreset(
        id = "ocean", name = "Ocean", character = "Calm and clear",
        accent = Color(0xFF2E9BE6),
        darkBackground = Color(0xFF080F16), darkSurface = Color(0xFF12202C),
        lightBackground = Color(0xFFF0F6FB), lightSurface = Color.White,
    ),
    ChronosPreset(
        id = "forest", name = "Forest", character = "Natural and grounded",
        accent = Color(0xFF4CD07D),
        darkBackground = Color(0xFF0A120E), darkSurface = Color(0xFF132019),
        lightBackground = Color(0xFFF2F8F4), lightSurface = Color.White,
    ),
    ChronosPreset(
        id = "sunset", name = "Sunset", character = "Warm and expressive",
        accent = Color(0xFFFFB020),
        darkBackground = Color(0xFF130E0A), darkSurface = Color(0xFF211812),
        lightBackground = Color(0xFFFDF6EF), lightSurface = Color.White,
    ),
    ChronosPreset(
        id = "lavender", name = "Lavender", character = "Soft and distinctive",
        accent = Color(0xFF8B7CFF),
        darkBackground = Color(0xFF0D0B14), darkSurface = Color(0xFF1A1726),
        lightBackground = Color(0xFFF6F4FD), lightSurface = Color.White,
    ),
    ChronosPreset(
        id = "minimal", name = "Minimal", character = "Neutral and restrained",
        accent = Color(0xFF8C97A6),
        darkBackground = Color(0xFF0C0D0F), darkSurface = Color(0xFF17191D),
        lightBackground = Color(0xFFF4F5F6), lightSurface = Color.White,
    ),
)

fun presetFor(id: String): ChronosPreset =
    ChronosPresets.firstOrNull { it.id == id } ?: ChronosPresets.first()

/**
 * Status colours, which the palette above does not cover.
 *
 * A status is never shown by colour alone: every caller pairs these with a word
 * or an icon, as the design direction requires.
 */
object StatusColors {
    val success = Color(0xFF3FBF7F)
    val onSuccess = Color(0xFF062015)
    val warning = Color(0xFFE8A33D)
    val onWarning = Color(0xFF231603)
    val info = Color(0xFF4C8DFF)
}

/**
 * The spacing scale and corner radii the screens should use, so padding and
 * shape stop being invented per screen.
 */
object Radii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** One step of the type scale, with tabular numerals for anything numeric. */
object ChronosType {
    val display = androidx.compose.ui.text.TextStyle(
        fontSize = 48.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Light,
        fontFeatureSettings = "tnum",
    )
    val numeric = androidx.compose.ui.text.TextStyle(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    )
    val headline = androidx.compose.ui.text.TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val title = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val label = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    )
}

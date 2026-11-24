package com.osintgram.chronos.widget

import android.graphics.Color
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.osintgram.chronos.R
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.theme.accentColor
import com.osintgram.chronos.ui.theme.presetFor

/**
 * The one place a widget gets its colours.
 *
 * Two rules, both enforced here rather than in nine layouts:
 *
 * 1. The accent is the app's accent. It is read from the same two settings the
 *    app's theme reads — [Settings.themePreset] and, for the "custom" look,
 *    [Settings.accent] — through the app's own [presetFor] and [accentColor]
 *    helpers, so there is no second list of hex values to drift out of step.
 * 2. Every foreground is checked against the background the user picked and,
 *    where it would not read, replaced with one that does. Nothing here can
 *    throw: an unreadable setting falls back to a colour that is measurable.
 */
object WidgetTheme {

    const val STYLE_DARK = "half"
    const val STYLE_TRANSPARENT = "transparent"
    const val STYLE_LIGHT = "white"
    const val STYLE_BLUE = "blue"
    const val STYLE_CUSTOM = "custom"

    /** The accent the app ships with; used only if the settings cannot be read. */
    val fallbackAccent: Int = 0xFF4C8DFF.toInt()

    /** Ink pairs taken from the app's own dark and light colour schemes. */
    private val darkInk = 0xFFEDEFF3.toInt()
    private val darkInkMuted = 0xFF9AA3B2.toInt()
    private val lightInk = 0xFF14171C.toInt()
    private val lightInkMuted = 0xFF5B6472.toInt()
    private val black = Color.BLACK
    private val white = Color.WHITE

    /** The look that lets the user pick a colour instead of a preset. */
    private const val CUSTOM_PRESET = "custom"

    /** Above this background luminance black reads better than white and vice versa. */
    private const val INK_FLIP_LUMINANCE = 0.179

    /** WCAG AA for normal-size text. */
    private const val MIN_TEXT_CONTRAST = 4.5

    /** Surfaces of the two button drawables, which the widgets cannot recolour. */
    private val buttonSurfaces = listOf(0xFF232833.toInt(), 0xFF2A3E66.toInt())

    /**
     * Everything a widget needs to paint itself, already checked against the
     * chosen background.
     */
    data class Palette(
        /** The background as an ARGB colour; fully transparent for the see-through style. */
        val surface: Int,
        /** Body and headline text. */
        val ink: Int,
        /** Labels and captions: the quietest colour that still reads. */
        val mutedInk: Int,
        /** The accent, darkened or lightened only as far as reading requires. */
        val accentInk: Int,
        /** Label colour on the fixed button drawables. */
        val buttonInk: Int,
    )

    fun palette(): Palette {
        val surface = surface()
        val ink = inkOn(surface)
        return Palette(
            surface = surface,
            ink = ink,
            mutedInk = mutedInkOn(surface, ink),
            accentInk = accentInkOn(surface, accent()),
            buttonInk = readableOn(buttonSurfaces, listOf(userInk(), darkInk, lightInk)),
        )
    }

    /**
     * The app's accent: the preset's colour, or the legacy accent setting when
     * the user chose "custom". Wrapped so a settings failure cannot break a widget.
     */
    fun accent(): Int = runCatching {
        val preset = Settings.themePreset.value
        if (preset == CUSTOM_PRESET) {
            accentColor(Settings.accent.value).toArgb()
        } else {
            presetFor(preset).accent.toArgb()
        }
    }.getOrDefault(fallbackAccent)

    /** The user's background choice, as the style name Settings stores. */
    fun style(): String = runCatching { Settings.widgetBgStyle.value }.getOrDefault(STYLE_DARK)

    /** The background colour behind the widget, matching [surfaceResource]. */
    fun surface(): Int = when (style()) {
        STYLE_TRANSPARENT -> Color.TRANSPARENT
        STYLE_LIGHT -> 0xFFF2F4F8.toInt()
        STYLE_BLUE -> 0xFF1E3A66.toInt()
        STYLE_CUSTOM -> runCatching { Settings.widgetBgColor.value }.getOrDefault(0xCC171B23.toInt())
        else -> 0xCC171B23.toInt()
    }

    /** Rounded preset drawable, or 0 when the user picked a custom colour. */
    fun surfaceResource(): Int = when (style()) {
        STYLE_TRANSPARENT -> R.drawable.widget_bg_transparent
        STYLE_LIGHT -> R.drawable.widget_bg_white
        STYLE_BLUE -> R.drawable.widget_bg_blue
        STYLE_CUSTOM -> 0
        else -> R.drawable.widget_bg
    }

    /** Paints the chosen background on a widget root, preset drawable or flat colour. */
    fun applyBackground(views: RemoteViews, rootId: Int) {
        val resource = surfaceResource()
        if (resource != 0) {
            views.setInt(rootId, "setBackgroundResource", resource)
        } else {
            views.setInt(rootId, "setBackgroundColor", surface())
        }
    }

    // ------------------------------------------------------------------ colour

    /**
     * Body text for a background: the user's own text colour when it reads,
     * otherwise the app's ink for that background, otherwise pure black or
     * white — one of which always reads.
     */
    fun inkOn(surface: Int): Int = readableOn(surface, listOf(userInk(), darkInk, lightInk))

    /**
     * Caption text: the app's muted token where it reads, otherwise the quietest
     * version of the body ink that still passes. Never a colour that disappears.
     */
    fun mutedInkOn(surface: Int, ink: Int = inkOn(surface)): Int {
        if (isTransparent(surface)) return darkInkMuted
        return listOf(darkInkMuted, lightInkMuted)
            .firstOrNull { contrast(it, surface) >= MIN_TEXT_CONTRAST }
            ?: quietestReadable(ink, surface)
    }

    /** The accent as ink on a background, adjusted only as far as it must be. */
    fun accentInkOn(surface: Int, accent: Int = accent()): Int {
        if (isTransparent(surface)) return accent
        if (contrast(accent, surface) >= MIN_TEXT_CONTRAST) return accent
        val target = flipInkFor(surface)
        for (step in 1..20) {
            val candidate = blend(accent, target, step / 20f)
            if (contrast(candidate, surface) >= MIN_TEXT_CONTRAST) return candidate
        }
        return target
    }

    /** The user's text colour; the dark-scheme ink if the setting cannot be read. */
    fun userInk(): Int = runCatching { Settings.widgetTextColor.value }.getOrDefault(darkInk)

    fun isTransparent(surface: Int): Boolean = Color.alpha(surface) == 0

    // ------------------------------------------------------------------- maths

    /** First candidate that reads on every one of [surfaces], else black or white. */
    private fun readableOn(surfaces: List<Int>, candidates: List<Int>): Int {
        // A see-through background has no colour to measure, so the user's own
        // choice is the only honest answer there.
        if (surfaces.size == 1 && isTransparent(surfaces.first())) return candidates.first()
        candidates.firstOrNull { candidate ->
            surfaces.all { contrast(candidate, it) >= MIN_TEXT_CONTRAST }
        }?.let { return it }
        return flipInkFor(surfaces.first())
    }

    /** Black or white, whichever reads better on this background. */
    private fun flipInkFor(surface: Int): Int =
        if (luminance(surface) > INK_FLIP_LUMINANCE) black else white

    /** The most washed-out blend of [ink] toward [surface] that still reads. */
    private fun quietestReadable(ink: Int, surface: Int): Int {
        var best = ink
        for (step in 1..100) {
            val candidate = blend(ink, surface, step / 100f)
            if (contrast(candidate, surface) < MIN_TEXT_CONTRAST) break
            best = candidate
        }
        return best
    }

    /** WCAG 2.1 relative luminance, ignoring alpha. */
    private fun luminance(color: Int): Double =
        0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))

    private fun channel(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [amount] of the way from [from] to [to]; the result is opaque. */
    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun mix(a: Int, b: Int): Int = (a + (b - a) * amount).toInt().coerceIn(0, 255)
        return Color.argb(
            255,
            mix(Color.red(from), Color.red(to)),
            mix(Color.green(from), Color.green(to)),
            mix(Color.blue(from), Color.blue(to)),
        )
    }
}

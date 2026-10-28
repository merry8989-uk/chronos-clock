package com.osintgram.chronos.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFeatureSettings
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings

/** Shared motion vocabulary so every screen moves at the same tempo. */
object Motion {
    const val FAST = 140
    const val MEDIUM = 260
    const val SLOW = 420

    val easeOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val easeInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    fun <T> fast(spec: androidx.compose.animation.core.FiniteAnimationSpec<T>? = null) =
        tween<T>(FAST, easing = easeOut)

    val springSnappy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/** Monospaced digits, so a running clock never shifts its own layout. */
val ClockTextStyle = TextStyle(
    fontFeatureSettings = FontFeatureSettings("tnum"),
    letterSpacing = 0.sp,
)

/** Light haptics, obeying the user's Haptic feedback setting. */
object Haptics {

    fun tap(context: Context) = buzz(context, 10L, 70)

    fun tick(context: Context) = buzz(context, 6L, 40)

    fun success(context: Context) = buzz(context, 22L, 120)

    private fun buzz(context: Context, ms: Long, amplitude: Int) {
        if (!Settings.haptics.value) return
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }
}

/**
 * Clickable with a spring press-scale and a light haptic. This is what makes
 * the app feel responsive rather than flat.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    scale: Float = 0.965f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val context = LocalContext.current
    val current by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1400f),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = current
            scaleY = current
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
        ) {
            if (haptic) Haptics.tap(context)
            onClick()
        }
}

/** Composable helper for screens that want the shared press feedback. */
@Composable
fun rememberPressScale(): Float = 1f

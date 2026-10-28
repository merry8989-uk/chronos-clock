package com.osintgram.chronos.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Motion
import com.osintgram.chronos.ui.theme.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The app's background: a soft accent wash at the top fading into the theme
 * background, so cards read as raised surfaces instead of floating on flat black.
 */
@Composable
fun ChronosBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.14f),
                    0.35f to accent.copy(alpha = 0.04f),
                    1f to background,
                ),
            ),
    ) {
        content()
    }
}

/** A spring toggle that looks like it belongs to this app. */
@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "switchTrack",
    )
    val onColor = MaterialTheme.colorScheme.primary
    val offColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                Color(
                    red = offColor.red + (onColor.red - offColor.red) * track,
                    green = offColor.green + (onColor.green - offColor.green) * track,
                    blue = offColor.blue + (onColor.blue - offColor.blue) * track,
                    alpha = 1f,
                ),
            )
            .pressable(enabled = enabled, scale = 0.94f) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .offset(x = (24.dp * track))
                .size(24.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** Sliding-pill segmented control. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "segment",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val itemWidth = maxWidth / options.size
        Box(
            modifier = Modifier
                .offset(x = itemWidth * animatedIndex)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { (value, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(scale = 0.96f) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (value == selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Circular progress ring with an animated sweep. */
@Composable
fun CircleProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550, easing = Motion.easeOut),
        label = "circle",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Text that slides each new value in, used for countdowns and the clock. */
@Composable
fun TickerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(Motion.FAST)) { height -> height / 2 } +
                fadeIn(animationSpec = tween(Motion.FAST))) togetherWith
                (slideOutVertically(animationSpec = tween(Motion.FAST)) { height -> -height / 2 } +
                    fadeOut(animationSpec = tween(Motion.FAST)))
        },
        label = "ticker",
        modifier = modifier,
    ) { value ->
        Text(text = value, style = style, color = color)
    }
}

/**
 * Swipe a card to the left to delete it. The row springs back if the swipe is
 * too short, and the delete action fires only past the threshold.
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 132.dp.toPx() }
    val errorColor = MaterialTheme.colorScheme.error

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(errorColor.copy(alpha = 0.9f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier.padding(end = 26.dp).size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-threshold, 0f))
                        }
                    },
                    onDragStopped = {
                        if (offset.value <= -threshold * 0.62f) {
                            Haptics.success(context)
                            onDelete()
                        }
                        offset.animateTo(0f, spring(stiffness = 500f))
                    },
                ),
        ) {
            content()
        }
    }
}

/** Fades and lifts a row into place, staggered by [index]. */
@Composable
fun Modifier.staggerIn(index: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 45).toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.easeOut),
        label = "staggerAlpha",
    )
    val lift by animateFloatAsState(
        targetValue = if (shown) 0f else 18f,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.easeOut),
        label = "staggerLift",
    )
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = lift
    }
}

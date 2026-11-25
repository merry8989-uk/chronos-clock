package com.osintgram.chronos.ui.missions

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.MissionSpec
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

// ------------------------------------------------------------- tokens -----

/**
 * Geometry the design tokens do not cover.
 *
 * [Spacing] and [Radii] describe the rhythm of a screen: padding and corners.
 * The side of a square tile is a different kind of number, so each grid that
 * draws squares names its own here rather than borrowing a padding step and
 * silently changing the size of the puzzle.
 */
private val OddTileSize = 74.dp
private val FindTileSize = 72.dp
private val ShapeTileSize = 70.dp
private val PatternTileSize = 88.dp

/** The numeral badge on a tile: big enough to read, small enough to leave the colour visible. */
private val TileBadgeSize = 20.dp

/** The answer field on the math mission, wide enough for a signed three-digit answer. */
private val AnswerFieldWidth = 200.dp

/** Tracking on the mission heading, which sits above the puzzle as a small caps label. */
private val HeadingTracking = 2.sp

/**
 * The colours the two colour puzzles draw, defined once.
 *
 * This is the only place in the mission code that names a colour of its own.
 * The odd-one-out grid and the pattern grid are *about* telling tiles apart, so
 * they cannot borrow a pair of surface roles: a preset can put `surfaceVariant`
 * and a low-contrast accent close enough together that the tile the user is
 * looking for disappears. The list is therefore the puzzle itself, and every
 * entry has to stay visibly different from the others.
 *
 * The base hue is the theme accent (see [oddTileBases]), so the grid still
 * belongs to the look the user picked; the entries below are the fixed hues it
 * falls back to, and any the accent already matches are dropped so no two tiles
 * are ever drawn identically. A numeral is drawn on every odd-one-out tile, so
 * the colour is never the only cue on screen.
 */
private val missionTileColors = listOf(
    // The pattern grid's resting plate: the dark ground its lit tile reads against.
    Color(0xFF232833),
    // The odd-one-out grid's fixed base hues, used after the theme accent.
    Color(0xFF8B7CFF), Color(0xFF22C7A9), Color(0xFFFFB020),
    Color(0xFFFF6E8A), Color(0xFF4CD07D),
)

/** How far apart two tile hues must be before they can both be drawn. */
private const val MinTileSeparation = 0.25f

/** How far the lit tile is pulled towards the accent, 0f..1f. */
private const val PatternLitMix = 0.78f

/** Rough per-channel distance between two colours, used only to keep tiles apart. */
private fun channelsApart(a: Color, b: Color): Float =
    abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

/** The odd-one-out bases: the theme accent first, then the fixed hues it does not already match. */
@Composable
private fun oddTileBases(): List<Color> {
    val accent = MaterialTheme.colorScheme.primary
    return buildList {
        add(accent)
        // Index 0 is the pattern grid's plate, not a base hue, so it is skipped.
        missionTileColors.drop(1).filterTo(this) { channelsApart(it, accent) > MinTileSeparation }
    }
}

/** The pattern grid's two shades: the resting plate and the lit tile the accent produces. */
@Composable
private fun patternLitTile(): Color =
    lerp(patternRestingTile(), MaterialTheme.colorScheme.primary, PatternLitMix)

/** The pattern grid's resting plate, the first entry of the drawn set. */
private fun patternRestingTile(): Color = missionTileColors.first()

/** How the most recent attempt went. [None] means nothing has been answered yet. */
private enum class Attempt { None, Wrong, Right }

/**
 * Reports the last attempt in words, with colour as a second signal only.
 *
 * A wrong answer is amber and says what happened and what to do next; a right
 * one is green and says the step was accepted. The line is a polite live region
 * so a screen reader announces the change instead of leaving the user to hunt
 * for it.
 */
@Composable
private fun AttemptFeedback(
    attempt: Attempt,
    wrongText: String,
    rightText: String,
) {
    if (attempt != Attempt.None) {
        val wrong = attempt == Attempt.Wrong
        Text(
            text = if (wrong) wrongText else rightText,
            color = if (wrong) StatusColors.warning else StatusColors.success,
            style = ChronosType.label,
            modifier = Modifier
                .padding(top = Spacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * The numeral drawn in the corner of a grid tile.
 *
 * It sits on a theme surface rather than straight on the tile colour so it
 * stays legible whatever hue the puzzle drew, and it gives a user who cannot
 * separate the hues something to name the tiles by.
 */
@Composable
private fun TileBadge(label: String) {
    Box(
        modifier = Modifier
            .size(TileBadgeSize)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radii.sm)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = ChronosType.label,
        )
    }
}

/**
 * Renders the mission an alarm requires. [onSolved] fires only when the user
 * has genuinely finished it. Missions that cannot run on this device (no step
 * sensor, permission refused) say so and offer [onUnavailable] instead of
 * trapping the user with an alarm they cannot stop.
 */
@Composable
fun MissionRunner(
    spec: MissionSpec,
    onSolved: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = MissionSpec.info(spec.type)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = info?.title ?: spec.type,
            color = MaterialTheme.colorScheme.primary,
            style = ChronosType.label.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = HeadingTracking,
            ),
        )
        Text(
            text = info?.subtitle ?: "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = ChronosType.label,
            modifier = Modifier.padding(bottom = Spacing.md),
        )

        when (spec.type) {
            "math" -> MathMission(spec, onSolved)
            "typing" -> TypingMission(spec, onSolved)
            "color_tiles" -> ColorTilesMission(spec, onSolved)
            "arrange_shapes" -> ArrangeShapesMission(spec, onSolved)
            "find_numbers" -> FindGridMission(spec, onSolved, letters = false)
            "find_alphabets" -> FindGridMission(spec, onSolved, letters = true)
            "memory" -> MemoryMission(spec, onSolved)
            "shake" -> ShakeMission(spec, onSolved, onUnavailable)
            "step" -> StepMission(spec, onSolved, onUnavailable)
            else -> {
                Text(
                    "This mission is not available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = ChronosType.label,
                )
                TextButton(onClick = onUnavailable) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun Progress(label: String, value: String) {
    Text(
        text = "$label  $value",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = ChronosType.label,
        modifier = Modifier.padding(top = Spacing.md),
    )
}

// ---------------------------------------------------------------- math -----

private data class Problem(val text: String, val answer: Int)

private fun newProblem(): Problem {
    val kind = Random.nextInt(0, 3)
    return when (kind) {
        0 -> {
            val a = Random.nextInt(11, 99)
            val b = Random.nextInt(11, 99)
            Problem("$a + $b", a + b)
        }
        1 -> {
            val a = Random.nextInt(30, 120)
            val b = Random.nextInt(5, a - 1)
            Problem("$a − $b", a - b)
        }
        else -> {
            val a = Random.nextInt(3, 13)
            val b = Random.nextInt(3, 13)
            Problem("$a × $b", a * b)
        }
    }
}

@Composable
private fun MathMission(spec: MissionSpec, onSolved: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    var input by remember(seed) { mutableStateOf("") }
    var attempt by remember { mutableStateOf(Attempt.None) }
    val problem = remember(seed) { newProblem() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${problem.text} = ?",
            color = MaterialTheme.colorScheme.onSurface,
            style = ChronosType.display,
        )
        Spacer(Modifier.height(Spacing.lg))
        OutlinedTextField(
            value = input,
            onValueChange = { text ->
                if (text.length <= 5 && text.all { it.isDigit() || it == '-' }) input = text
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(AnswerFieldWidth),
        )
        AttemptFeedback(
            attempt = attempt,
            wrongText = "Not quite. Check the sum and try again.",
            rightText = "Correct. Next problem.",
        )
        Spacer(Modifier.height(Spacing.lg))
        Button(onClick = {
            if (input.trim().toIntOrNull() == problem.answer) {
                solved += 1
                attempt = Attempt.Right
                if (solved >= spec.count) onSolved() else seed += 1
            } else {
                attempt = Attempt.Wrong
                input = ""
            }
        }) { Text("Check") }
        Progress("Solved", "$solved / ${spec.count}")
    }
}

// -------------------------------------------------------------- typing -----

private val phrases = listOf(
    "I am awake now",
    "Good morning sunshine",
    "Time to get up",
    "Wake up and shine",
    "Rise and grind today",
    "The early bird catches the worm",
    "One more day one more chance",
)

@Composable
private fun TypingMission(spec: MissionSpec, onSolved: () -> Unit) {
    var done by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    val phrase = remember(seed) { phrases[Random.nextInt(phrases.size)] }
    var input by remember(seed) { mutableStateOf("") }
    var attempt by remember { mutableStateOf(Attempt.None) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = phrase,
            color = MaterialTheme.colorScheme.onSurface,
            style = ChronosType.headline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        Spacer(Modifier.height(Spacing.lg))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            placeholder = { Text("Type it exactly") },
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        AttemptFeedback(
            attempt = attempt,
            wrongText = "That does not match yet. Type the phrase exactly as it is shown.",
            rightText = "Matched. Next phrase.",
        )
        Spacer(Modifier.height(Spacing.lg))
        Button(onClick = {
            if (input.trim().equals(phrase, ignoreCase = true)) {
                done += 1
                attempt = Attempt.Right
                if (done >= spec.count) onSolved() else seed += 1
            } else {
                attempt = Attempt.Wrong
            }
        }) { Text("Done") }
        Progress("Typed", "$done / ${spec.count}")
    }
}

// --------------------------------------------------------- colour tiles -----

@Composable
private fun ColorTilesMission(spec: MissionSpec, onSolved: () -> Unit) {
    var done by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableStateOf(Attempt.None) }

    val bases = oddTileBases()
    val base = remember(seed, bases) { bases[Random.nextInt(bases.size)] }
    val oddIndex = remember(seed) { Random.nextInt(9) }
    val odd = remember(seed, base) {
        val shift = 0.16f + Random.nextInt(0, 8) / 100f
        Color(
            red = (base.red + shift).coerceAtMost(1f),
            green = (base.green + shift * 0.6f).coerceAtMost(1f),
            blue = (base.blue - shift * 0.4f).coerceAtLeast(0f),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0 until 3) {
            Row {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    Box(
                        modifier = Modifier
                            .padding(Spacing.xs)
                            .size(OddTileSize)
                            .clip(RoundedCornerShape(Radii.md))
                            .background(if (index == oddIndex) odd else base)
                            // Merged so the numeral is not read out as a second
                            // node: the tile is announced once, by name.
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Tile ${index + 1} of 9"
                                role = Role.Button
                            }
                            .clickable {
                                if (index == oddIndex) {
                                    done += 1
                                    attempt = Attempt.Right
                                    if (done >= spec.count) onSolved() else seed += 1
                                } else {
                                    attempt = Attempt.Wrong
                                }
                            },
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Box(modifier = Modifier.padding(Spacing.xs)) {
                            TileBadge(label = "${index + 1}")
                        }
                    }
                }
            }
        }
        AttemptFeedback(
            attempt = attempt,
            wrongText = "That tile matches the others. Look for the one that does not.",
            rightText = "Found the odd tile.",
        )
        Progress("Found", "$done / ${spec.count}")
    }
}

// ------------------------------------------------------ arrange shapes -----

private val shapeGlyphs = listOf("■", "●", "▲", "★", "◆", "⬢", "✚", "⬟")

@Composable
private fun ArrangeShapesMission(spec: MissionSpec, onSolved: () -> Unit) {
    val count = spec.count.coerceIn(2, 5)
    var seed by remember { mutableIntStateOf(0) }
    val target = remember(seed) {
        shapeGlyphs.shuffled().take(count)
    }
    val options = remember(seed) { target.shuffled() }
    var tapped by remember(seed) { mutableStateOf(0) }
    var attempt by remember { mutableStateOf(Attempt.None) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Tap in this order",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = ChronosType.label,
        )
        Text(
            text = target.joinToString("  "),
            color = MaterialTheme.colorScheme.onSurface,
            style = ChronosType.display,
            modifier = Modifier.padding(vertical = Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            options.forEach { glyph ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(Radii.lg),
                    modifier = Modifier
                        .size(ShapeTileSize)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Shape $glyph"
                            role = Role.Button
                        }
                        .clickable {
                            if (target.getOrNull(tapped) == glyph) {
                                tapped += 1
                                attempt = Attempt.Right
                                if (tapped >= target.size) {
                                    if (seed + 1 >= spec.count) onSolved() else seed += 1
                                }
                            } else {
                                tapped = 0
                                attempt = Attempt.Wrong
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            glyph,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = ChronosType.display,
                        )
                    }
                }
            }
        }
        AttemptFeedback(
            attempt = attempt,
            wrongText = "That is out of order. Start again from the first shape.",
            rightText = "In order so far. Keep going.",
        )
        Progress("Placed", "$tapped / ${target.size}")
    }
}

// ------------------------------------------------- find numbers/letters -----

@Composable
private fun FindGridMission(spec: MissionSpec, onSolved: () -> Unit, letters: Boolean) {
    var done by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableStateOf(Attempt.None) }

    val pool = remember(letters) {
        if (letters) ('A'..'Z').map { it.toString() } else (1..99).map { it.toString() }
    }
    val cells = remember(seed) { pool.shuffled().take(9) }
    val target = remember(seed) { cells[Random.nextInt(cells.size)] }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Find  $target",
            color = MaterialTheme.colorScheme.onSurface,
            style = ChronosType.numeric,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        for (row in 0 until 3) {
            Row {
                for (col in 0 until 3) {
                    val value = cells[row * 3 + col]
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Radii.md),
                        modifier = Modifier
                            .padding(Spacing.xs)
                            .size(FindTileSize)
                            .semantics {
                                contentDescription = "Tile $value"
                                role = Role.Button
                            }
                            .clickable {
                                if (value == target) {
                                    done += 1
                                    attempt = Attempt.Right
                                    if (done >= spec.count) onSolved() else seed += 1
                                } else {
                                    attempt = Attempt.Wrong
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                value,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = ChronosType.headline,
                            )
                        }
                    }
                }
            }
        }
        AttemptFeedback(
            attempt = attempt,
            wrongText = "That is not $target. Look again.",
            rightText = "Found $target.",
        )
        Progress("Found", "$done / ${spec.count}")
    }
}

// -------------------------------------------------------- memory tiles -----

@Composable
private fun MemoryMission(spec: MissionSpec, onSolved: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val length = (2 + round).coerceAtMost(6)
    var sequence by remember(round) {
        mutableStateOf(List(length) { Random.nextInt(4) })
    }
    var showing by remember(round) { mutableStateOf(true) }
    var litTile by remember(round) { mutableIntStateOf(-1) }
    var entered by remember(round) { mutableStateOf(0) }
    var attempt by remember(round) { mutableStateOf(Attempt.None) }
    val litColor = patternLitTile()
    val restingTile = patternRestingTile()

    LaunchedEffect(round) {
        showing = true
        entered = 0
        attempt = Attempt.None
        sequence = List(length) { Random.nextInt(4) }
        delay(500)
        sequence.forEach { tile ->
            litTile = tile
            delay(500)
            litTile = -1
            delay(220)
        }
        showing = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (showing) "Watch the sequence" else "Repeat it",
            color = if (showing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = ChronosType.label,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        for (row in 0 until 2) {
            Row {
                for (col in 0 until 2) {
                    val tile = row * 2 + col
                    val isLit = litTile == tile
                    Box(
                        modifier = Modifier
                            .padding(Spacing.sm)
                            .size(PatternTileSize)
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(if (isLit) litColor else restingTile)
                            // The lit state is part of the name, so the pattern
                            // is available to a screen reader and not only to
                            // the eye watching the grid.
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (isLit) {
                                    "Tile ${tile + 1}, lit"
                                } else {
                                    "Tile ${tile + 1}"
                                }
                                role = Role.Button
                            }
                            .clickable(enabled = !showing) {
                                if (sequence.getOrNull(entered) == tile) {
                                    entered += 1
                                    attempt = Attempt.Right
                                    if (entered >= sequence.size) {
                                        if (round + 1 >= spec.count) onSolved() else round += 1
                                    }
                                } else {
                                    attempt = Attempt.Wrong
                                    entered = 0
                                }
                            },
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Box(modifier = Modifier.padding(Spacing.sm)) {
                            TileBadge(label = "${tile + 1}")
                        }
                    }
                }
            }
        }
        AttemptFeedback(
            attempt = attempt,
            wrongText = "That is out of sequence. Watch the pattern again.",
            rightText = "Right tile. Keep going.",
        )
        Progress("Rounds", "$round / ${spec.count}")
    }
}

// -------------------------------------------------------------- sensors -----

/** Counts deliberate shakes: a strong jolt followed by a settle. */
@Composable
private fun ShakeMission(spec: MissionSpec, onSolved: () -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var available by remember { mutableStateOf(true) }
    var armed by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            available = false
            return@DisposableEffect onDispose { }
        }
        var lastShakeAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val e = event ?: return
                val x = e.values.getOrElse(0) { 0f }
                val y = e.values.getOrElse(1) { 0f }
                val z = e.values.getOrElse(2) { 0f }
                val magnitude = sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (armed && magnitude > 13f && now - lastShakeAt > 320) {
                    lastShakeAt = now
                    count += 1
                    if (count >= spec.count) onSolved()
                }
                if (magnitude < 11f) armed = true
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!available) {
            Text(
                "This device has no accelerometer, so the shake mission cannot run.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = ChronosType.label,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onUnavailable) { Text("Continue") }
            return@Column
        }
        Text(
            text = "$count / ${spec.count}",
            color = MaterialTheme.colorScheme.onSurface,
            style = ChronosType.display,
        )
        Text(
            "Shake the phone",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = ChronosType.label,
        )
    }
}

/** Counts steps taken since the alarm started ringing. */
@Composable
private fun StepMission(spec: MissionSpec, onSolved: () -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    var steps by remember { mutableIntStateOf(0) }
    var available by remember { mutableStateOf(true) }
    var baseline by remember { mutableIntStateOf(-1) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            available = false
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val e = event ?: return
                val total = e.values.getOrElse(0) { 0f }.toInt()
                if (baseline < 0) {
                    baseline = total
                } else {
                    val walked = (total - baseline).coerceAtLeast(0)
                    steps = walked
                    if (walked >= spec.count) onSolved()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!available) {
            Text(
                "This device has no step counter. Walk-off missions need one, " +
                    "so grant activity permission or pick another mission.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = ChronosType.label,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onUnavailable) { Text("Continue") }
            return@Column
        }
        Text(
            text = "$steps / ${spec.count}",
            color = MaterialTheme.colorScheme.onSurface,
            style = ChronosType.display,
        )
        Text(
            "Walk to dismiss",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = ChronosType.label,
        )
    }
}

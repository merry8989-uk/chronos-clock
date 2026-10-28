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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.MissionSpec
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

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
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Text(
            text = info?.subtitle ?: "",
            color = Color(0xFF9AA3B2),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 14.dp),
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
                Text("This mission is not available.", color = Color(0xFF9AA3B2), fontSize = 13.sp)
                TextButton(onClick = onUnavailable) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun Progress(label: String, value: String) {
    Text(
        text = "$label  $value",
        color = Color(0xFF9AA3B2),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 12.dp),
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
    var wrong by remember(seed) { mutableStateOf(false) }
    val problem = remember(seed) { newProblem() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${problem.text} = ?",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { text ->
                if (text.length <= 5 && text.all { it.isDigit() || it == '-' }) input = text
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(200.dp),
        )
        if (wrong) {
            Text(
                "Not quite — try again",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            if (input.trim().toIntOrNull() == problem.answer) {
                solved += 1
                if (solved >= spec.count) onSolved() else seed += 1
            } else {
                wrong = true
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
    var wrong by remember(seed) { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = phrase,
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            placeholder = { Text("Type it exactly") },
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        if (wrong) {
            Text(
                "Not matching yet",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            if (input.trim().equals(phrase, ignoreCase = true)) {
                done += 1
                if (done >= spec.count) onSolved() else seed += 1
            } else {
                wrong = true
            }
        }) { Text("Done") }
        Progress("Typed", "$done / ${spec.count}")
    }
}

// --------------------------------------------------------- colour tiles -----

private val tileColors = listOf(
    Color(0xFF4C8DFF), Color(0xFF8B7CFF), Color(0xFF22C7A9),
    Color(0xFFFFB020), Color(0xFFFF6E8A), Color(0xFF4CD07D),
)

@Composable
private fun ColorTilesMission(spec: MissionSpec, onSolved: () -> Unit) {
    var done by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    var wrong by remember(seed) { mutableStateOf(false) }

    val base = remember(seed) { tileColors[Random.nextInt(tileColors.size)] }
    val oddIndex = remember(seed) { Random.nextInt(9) }
    val odd = remember(seed) {
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
                            .padding(5.dp)
                            .size(74.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (index == oddIndex) odd else base)
                            .clickable {
                                if (index == oddIndex) {
                                    done += 1
                                    if (done >= spec.count) onSolved() else seed += 1
                                } else {
                                    wrong = true
                                }
                            },
                    )
                }
            }
        }
        if (wrong) {
            Text(
                "Wrong tile",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
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
    var wrong by remember(seed) { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Tap in this order",
            color = Color(0xFF9AA3B2),
            fontSize = 12.sp,
        )
        Text(
            text = target.joinToString("  "),
            color = Color.White,
            fontSize = 30.sp,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { glyph ->
                Surface(
                    color = Color(0xFF232833),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(70.dp).clickable {
                        if (target.getOrNull(tapped) == glyph) {
                            tapped += 1
                            wrong = false
                            if (tapped >= target.size) {
                                if (seed + 1 >= spec.count) onSolved() else seed += 1
                            }
                        } else {
                            tapped = 0
                            wrong = true
                        }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(glyph, color = Color.White, fontSize = 30.sp)
                    }
                }
            }
        }
        if (wrong) {
            Text(
                "Start again from the first shape",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Progress("Placed", "$tapped / ${target.size}")
    }
}

// ------------------------------------------------- find numbers/letters -----

@Composable
private fun FindGridMission(spec: MissionSpec, onSolved: () -> Unit, letters: Boolean) {
    var done by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    var wrong by remember(seed) { mutableStateOf(false) }

    val pool = remember(letters) {
        if (letters) ('A'..'Z').map { it.toString() } else (1..99).map { it.toString() }
    }
    val cells = remember(seed) { pool.shuffled().take(9) }
    val target = remember(seed) { cells[Random.nextInt(cells.size)] }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Find  $target",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        for (row in 0 until 3) {
            Row {
                for (col in 0 until 3) {
                    val value = cells[row * 3 + col]
                    Surface(
                        color = Color(0xFF232833),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.padding(5.dp).size(72.dp).clickable {
                            if (value == target) {
                                done += 1
                                if (done >= spec.count) onSolved() else seed += 1
                            } else {
                                wrong = true
                            }
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(value, color = Color.White, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
        if (wrong) {
            Text(
                "That is not $target",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
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
    var wrong by remember(round) { mutableStateOf(false) }

    LaunchedEffect(round) {
        showing = true
        entered = 0
        wrong = false
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
            color = if (showing) MaterialTheme.colorScheme.primary else Color(0xFF9AA3B2),
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        for (row in 0 until 2) {
            Row {
                for (col in 0 until 2) {
                    val tile = row * 2 + col
                    val isLit = litTile == tile
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(88.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isLit) Color(0xFF9FC4FF) else Color(0xFF232833),
                            )
                            .clickable(enabled = !showing) {
                                if (sequence.getOrNull(entered) == tile) {
                                    entered += 1
                                    if (entered >= sequence.size) {
                                        if (round + 1 >= spec.count) onSolved() else round += 1
                                    }
                                } else {
                                    wrong = true
                                    entered = 0
                                }
                            },
                    )
                }
            }
        }
        if (wrong) {
            Text(
                "Wrong — watch it again",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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
                color = Color(0xFF9AA3B2),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onUnavailable) { Text("Continue") }
            return@Column
        }
        Text(
            text = "$count / ${spec.count}",
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Light,
        )
        Text("Shake the phone", color = Color(0xFF9AA3B2), fontSize = 13.sp)
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
                "This device has no step counter. Walk-off missions need one — " +
                    "grant activity permission or pick another mission.",
                color = Color(0xFF9AA3B2),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onUnavailable) { Text("Continue") }
            return@Column
        }
        Text(
            text = "$steps / ${spec.count}",
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Light,
        )
        Text("Walk to dismiss", color = Color(0xFF9AA3B2), fontSize = 13.sp)
    }
}

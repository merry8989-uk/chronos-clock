package com.osintgram.chronos.bedtime

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.components.AnalogClock
import com.osintgram.chronos.ui.components.DigitalClockLabel
import com.osintgram.chronos.ui.theme.ChronosTheme

/**
 * The nightstand screen: a dim clock for bedtime, with the next alarm on show so
 * the user knows the phone will still wake them.
 */
class BedtimeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ChronosTheme {
                val context = LocalContext.current
                var dimmed by remember { mutableStateOf(false) }
                var dndOn by remember { mutableStateOf(BedtimeDnd.isOn(context)) }

                // Dimming is done on our own window, so no special permission is
                // needed and nothing is changed outside this screen.
                val brightness = if (dimmed) 0.02f else 0.45f
                window.attributes = window.attributes.apply {
                    screenBrightness = brightness
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (dimmed) Color.Black else Color(0xFF07090D)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "BEDTIME",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                        )
                        Spacer(Modifier.height(18.dp))

                        AnalogClock(
                            modifier = Modifier.size(200.dp),
                            showSecondHand = Settings.clockSecondHand.value,
                            smoothSweep = Settings.clockSmooth.value,
                            rimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            tickColor = Color(0xFF6C7686),
                            hourHandColor = Color(0xFFB9C2D0),
                            minuteHandColor = Color(0xFFB9C2D0),
                            secondHandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        )

                        Spacer(Modifier.height(16.dp))
                        DigitalClockLabel(color = Color(0xFF9AA3B2))

                        Spacer(Modifier.height(26.dp))
                        Text(
                            text = "Next alarm",
                            fontSize = 11.sp,
                            color = Color(0xFF6C7686),
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = remember { BedtimeScheduler.nextWakeAlarmLabel() },
                            fontSize = 15.sp,
                            color = Color(0xFFD5DBE4),
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(30.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { dimmed = !dimmed },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(52.dp),
                            ) {
                                Text(if (dimmed) "Brighten" else "Dim screen", fontSize = 14.sp)
                            }
                            if (BedtimeDnd.canControl(context)) {
                                OutlinedButton(
                                    onClick = {
                                        if (dndOn) {
                                            BedtimeDnd.disable(context)
                                        } else {
                                            BedtimeDnd.enable(context)
                                        }
                                        dndOn = BedtimeDnd.isOn(context)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(52.dp),
                                ) {
                                    Text(
                                        if (dndOn) "DND on" else "DND off",
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { finish() },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(0.6f).height(52.dp),
                        ) {
                            Text("Close", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Alarms still ring during bedtime.",
                            fontSize = 11.sp,
                            color = Color(0xFF6C7686),
                        )
                    }
                }
            }
        }
    }
}

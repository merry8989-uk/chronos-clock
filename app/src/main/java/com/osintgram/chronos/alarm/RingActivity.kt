package com.osintgram.chronos.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.theme.ChronosTheme

/**
 * The screen that takes over the display when an alarm rings — over the lock
 * screen, with the screen turned on, so a sleeping user actually sees it.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.requestDismissKeyguard(this, null)

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)

        setContent {
            ChronosTheme {
                var dismissed by remember { mutableStateOf(false) }
                val alarm = remember { AlarmStore.byId(alarmId) }
                val use24 = Settings.use24Hour.value

                Surface(color = Color(0xFF0B0E14), modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "ALARM",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = alarm?.timeText(use24) ?: "--:--",
                            color = Color.White,
                            fontSize = 84.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center,
                        )
                        if (use24.not()) {
                            Text(
                                text = alarm?.meridiem(false) ?: "",
                                color = Color(0xFFB9C0CC),
                                fontSize = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = alarm?.label?.takeIf { it.isNotBlank() } ?: "Wake up",
                            color = Color(0xFFB9C0CC),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                        if (snoozeCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Snoozed $snoozeCount time(s)",
                                color = Color(0xFF7E8794),
                                fontSize = 13.sp,
                            )
                        }

                        Spacer(Modifier.height(56.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (!dismissed) {
                                        dismissed = true
                                        AlarmService.requestSnooze(this@RingActivity)
                                        finish()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(60.dp),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text("Snooze ${alarm?.snoozeMinutes ?: 5} min", fontSize = 16.sp)
                            }
                            Button(
                                onClick = {
                                    if (!dismissed) {
                                        dismissed = true
                                        AlarmService.dismiss(this@RingActivity)
                                        finish()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(60.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text("Dismiss", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
    }
}

package com.osintgram.chronos.alarm

import android.app.KeyguardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.AlarmStore
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.ui.missions.MissionRunner
import com.osintgram.chronos.ui.theme.ChronosTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * The screen that takes over the display when an alarm rings. Missions, when
 * the alarm has any, must be completed before the alarm can be snoozed or
 * dismissed.
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
                val alarm = remember { AlarmStore.byId(alarmId) }
                val use24 = Settings.use24Hour.value
                val missions = alarm?.missions ?: emptyList()

                var missionIndex by remember { mutableIntStateOf(0) }
                var dismissed by remember { mutableStateOf(false) }
                var skipAllowed by remember { mutableStateOf(false) }

                // A way out for a genuinely stuck user, after three minutes.
                LaunchedEffect(Unit) {
                    delay(180_000)
                    skipAllowed = true
                }

                val missionsDone = missionIndex >= missions.size

                val wallpaper = remember {
                    val paths = alarm?.wallpaperPaths ?: emptyList()
                    if (paths.isEmpty()) null
                    else {
                        val chosen = if (alarm?.randomWallpaper == true) paths.random() else paths.first()
                        runCatching { BitmapFactory.decodeFile(File(chosen).absolutePath) }.getOrNull()
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0E14))) {
                    if (wallpaper != null) {
                        Image(
                            bitmap = wallpaper.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.62f)),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "ALARM",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = alarm?.timeText(use24) ?: "--:--",
                            color = Color.White,
                            fontSize = 74.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center,
                        )
                        if (!use24) {
                            Text(
                                text = alarm?.meridiem(false) ?: "",
                                color = Color(0xFFD5DBE4),
                                fontSize = 18.sp,
                            )
                        }
                        Text(
                            text = alarm?.label?.takeIf { it.isNotBlank() } ?: "Wake up",
                            color = Color(0xFFD5DBE4),
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (snoozeCount > 0) {
                            Text(
                                text = "Snoozed $snoozeCount time(s)",
                                color = Color(0xFF9AA3B2),
                                fontSize = 12.sp,
                            )
                        }

                        Spacer(Modifier.height(26.dp))

                        if (!missionsDone) {
                            Text(
                                text = "Mission ${missionIndex + 1}/${missions.size}",
                                color = Color(0xFF9AA3B2),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            MissionRunner(
                                spec = missions[missionIndex],
                                onSolved = { missionIndex += 1 },
                                onUnavailable = { missionIndex += 1 },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            if (skipAllowed) {
                                TextButton(onClick = {
                                    missionIndex = missions.size
                                }) {
                                    Text(
                                        "Skip remaining missions",
                                        color = Color(0xFF9AA3B2),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!dismissed) {
                                            dismissed = true
                                            AlarmService.requestSnooze(this@RingActivity)
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(58.dp),
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text("Snooze ${alarm?.snoozeMinutes ?: 5} min", fontSize = 15.sp)
                                }
                                Button(
                                    onClick = {
                                        if (!dismissed) {
                                            dismissed = true
                                            AlarmService.dismiss(this@RingActivity)
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(58.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text(
                                        "Dismiss",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
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

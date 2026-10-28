package com.osintgram.chronos.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.osintgram.chronos.alarm.AlarmService
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.Tones
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.ui.components.Pill
import kotlinx.coroutines.delay
import java.io.File

/**
 * Alarm sound picker: the tones bundled with the app (by character), any audio
 * file on the device, or a sound the user records themselves.
 */
@Composable
fun SoundPickerDialog(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf(Tones.categories.first()) }
    var selected by remember { mutableStateOf(current) }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    var recordedVersion by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordSeconds by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { preview?.release() }
            preview = null
        }
    }

    LaunchedEffect(recording) {
        if (recording != null) {
            recordSeconds = 0
            while (true) {
                delay(1000)
                recordSeconds += 1
            }
        }
    }

    fun play(uriString: String) {
        runCatching {
            preview?.release()
            preview = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, Uri.parse(uriString))
                isLooping = false
                prepare()
                start()
            }
        }.onFailure { DiagnosticsLogger.log("SoundPicker", "Preview failed: ${it.message}") }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            selected = uri.toString()
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // The user can tap record again now that permission is granted.
            DiagnosticsLogger.log("SoundPicker", "Microphone permission granted")
        }
    }

    val recorded = remember(recordedVersion) { recordedTones(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(14.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Sound", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Ringtones", tab == 0, { tab = 0 })
                    Pill("My music", tab == 1, { tab = 1 })
                    Pill("Recorded", tab == 2, { tab = 2 })
                }

                Spacer(Modifier.height(14.dp))

                Box(modifier = Modifier.heightIn(min = 260.dp, max = 380.dp)) {
                    when (tab) {
                        0 -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tones.categories.forEach { c ->
                                    Pill(c, category == c, { category = c })
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            SoundRow(
                                title = "Device default alarm",
                                subtitle = "Whatever your phone uses",
                                selected = selected == null,
                                onSelect = { selected = null },
                                onPreview = null,
                            )
                            Tones.inCategory(category).forEach { tone ->
                                val uri = Tones.uriFor(context, tone)
                                SoundRow(
                                    title = tone.title,
                                    subtitle = tone.category,
                                    selected = selected == uri,
                                    onSelect = { selected = uri },
                                    onPreview = { play(uri) },
                                )
                            }
                        }
                        1 -> Column {
                            Text(
                                "Pick any audio file on this device — a song, a podcast clip, " +
                                    "a voice note. It plays as the alarm tone.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                                Text("Choose an audio file")
                            }
                            Spacer(Modifier.height(12.dp))
                            if (selected?.startsWith("android.resource") == false) {
                                Text(
                                    text = "Selected: ${AlarmService.ringtoneTitle(context, selected)}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { selected?.let { play(it) } }) {
                                    Text("Preview")
                                }
                            }
                        }
                        else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                "Record your own alarm sound. It is saved on this device only.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            if (recording == null) {
                                Button(onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        val result = startRecording(context)
                                        recording = result?.first
                                        recordingFile = result?.second
                                    }
                                }) {
                                    Icon(Icons.Filled.Mic, contentDescription = null)
                                    Text("  Record a sound")
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Stop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        "  Recording… ${recordSeconds}s",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    TextButton(onClick = {
                                        val file = recordingFile
                                        runCatching {
                                            recording?.stop()
                                            recording?.release()
                                        }
                                        recording = null
                                        recordingFile = null
                                        recordedVersion += 1
                                        if (file != null) {
                                            selected = Uri.fromFile(file).toString()
                                        }
                                    }) { Text("Stop and use") }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            if (recorded.isEmpty()) {
                                Text(
                                    "No recordings yet.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            recorded.forEach { file ->
                                val uri = Uri.fromFile(file).toString()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, fontSize = 14.sp)
                                        Text(
                                            "${file.length() / 1024} KB",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { play(uri) }) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Preview",
                                        )
                                    }
                                    TextButton(onClick = { selected = uri }) { Text("Use") }
                                    IconButton(onClick = {
                                        file.delete()
                                        recordedVersion += 1
                                    }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete recording",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        runCatching { preview?.release() }
                        preview = null
                        onDismiss()
                    }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        runCatching { preview?.release() }
                        preview = null
                        onPick(selected)
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun SoundRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPreview != null) {
            IconButton(onClick = onPreview) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Preview $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun recordedTones(context: Context): List<File> {
    val dir = File(context.filesDir, "tones")
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.isFile && f.length() > 0 }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

@Suppress("DEPRECATION")
private fun startRecording(context: Context): Pair<MediaRecorder, File>? {
    return runCatching {
        val dir = File(context.filesDir, "tones").apply { mkdirs() }
        val file = File(dir, "recorded_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(96_000)
        recorder.setAudioSamplingRate(44_100)
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        recorder.start()
        recorder to file
    }.onFailure {
        DiagnosticsLogger.log("SoundPicker", "Recording failed: ${it.message}")
    }.getOrNull()
}

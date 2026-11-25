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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.osintgram.chronos.alarm.AlarmService
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.Tones
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.ErrorState
import com.osintgram.chronos.ui.components.Pill
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.io.File

/**
 * Height of the inline empty state inside the scrolling tab body. The spacing
 * scale carries no vertical size step, so the one value this screen needs is
 * named here rather than inlined.
 */
private val EmptyStateHeight = 150.dp

/**
 * Alarm sound picker: the tones bundled with the app (by character), any audio
 * file on the device, or a sound the user records themselves.
 *
 * One action confirms the choice — Save — so it is the only filled button in
 * the dialog; choosing a file, recording and previewing are all quiet.
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
    // Presentation only: the sentence shown in the picker's error state when the
    // file picker or the microphone fails. It never feeds the selection itself.
    var errorText by remember { mutableStateOf<String?>(null) }

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
            }.onFailure {
                // The picker itself failed, so say so instead of leaving the
                // screen looking as if nothing happened.
                errorText = "That file could not be opened. Pick a different audio file."
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
        } else {
            errorText = "Microphone permission was refused. Allow it in system settings " +
                "to record a sound, or pick a bundled tone."
        }
    }

    val recorded = remember(recordedVersion) { recordedTones(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radii.xl),
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text("Sound", style = ChronosType.headline)

                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Pill("Ringtones", tab == 0, { tab = 0 })
                    Pill("My music", tab == 1, { tab = 1 })
                    Pill("Recorded", tab == 2, { tab = 2 })
                }

                Spacer(Modifier.height(Spacing.md))

                Box(modifier = Modifier.heightIn(min = 260.dp, max = 380.dp)) {
                    val failure = errorText
                    if (failure != null) {
                        // The picker or the microphone failed: the error is said
                        // in words, and clearing it puts the tab back.
                        ErrorState(
                            title = "Sound unavailable",
                            detail = failure,
                            onRetry = { errorText = null },
                        )
                    } else {
                        when (tab) {
                            0 -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    Tones.categories.forEach { c ->
                                        Pill(c, category == c, { category = c })
                                    }
                                }
                                Spacer(Modifier.height(Spacing.sm))
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
                                    style = ChronosType.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Spacing.md))
                                OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                                    Text("Choose an audio file", style = ChronosType.body)
                                }
                                Spacer(Modifier.height(Spacing.md))
                                if (selected?.startsWith("android.resource") == false) {
                                    Text(
                                        text = "Selected: ${AlarmService.ringtoneTitle(context, selected)}",
                                        style = ChronosType.body,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(Spacing.sm))
                                    TextButton(onClick = { selected?.let { play(it) } }) {
                                        Text("Preview", style = ChronosType.label)
                                    }
                                }
                            }
                            else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    "Record your own alarm sound. It is saved on this device only.",
                                    style = ChronosType.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Spacing.md))
                                if (recording == null) {
                                    OutlinedButton(onClick = {
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
                                            if (result == null) {
                                                errorText = "The microphone could not start. " +
                                                    "Close any app that is recording and try again."
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Mic, contentDescription = null)
                                        Text("  Record a sound", style = ChronosType.body)
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Stop,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        // The tint is never the only signal: the
                                        // word "Recording" carries the state.
                                        Text(
                                            "  Recording… ${recordSeconds}s",
                                            style = ChronosType.body,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(Modifier.width(Spacing.md))
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
                                        }) { Text("Stop and use", style = ChronosType.label) }
                                    }
                                }
                                Spacer(Modifier.height(Spacing.lg))
                                if (recorded.isEmpty()) {
                                    // Empty state: nothing recorded yet, said in
                                    // words rather than as a blank list.
                                    EmptyState(
                                        title = "No recordings yet",
                                        subtitle = "Tap Record a sound to make one. Recordings stay " +
                                            "on this device.",
                                        modifier = Modifier.height(EmptyStateHeight),
                                    )
                                }
                                recorded.forEach { file ->
                                    val uri = Uri.fromFile(file).toString()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, style = ChronosType.body)
                                            Text(
                                                "${file.length() / 1024} KB",
                                                style = ChronosType.label,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { play(uri) }) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Preview recording",
                                            )
                                        }
                                        TextButton(onClick = { selected = uri }) {
                                            Text("Use", style = ChronosType.label)
                                        }
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
                }

                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        runCatching { preview?.release() }
                        preview = null
                        onDismiss()
                    }) { Text("Cancel", style = ChronosType.label) }
                    Spacer(Modifier.width(Spacing.sm))
                    // The one filled action of the dialog.
                    Button(onClick = {
                        runCatching { preview?.release() }
                        preview = null
                        onPick(selected)
                    }) { Text("Save", style = ChronosType.title) }
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
            .padding(vertical = Spacing.sm),
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
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ChronosType.title,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = ChronosType.label,
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

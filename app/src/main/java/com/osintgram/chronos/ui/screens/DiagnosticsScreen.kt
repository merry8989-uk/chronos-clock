package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.BuildConfig
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.diagnostics.LogExporter
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.components.LoadingState
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The dense monospace lines sit below the type scale: [ChronosType.label] at
 * 12sp is too large for the device block and the captured log, so they keep
 * their own named step instead of an inline literal.
 */
private val MonoDetailSize = 11.sp
private val LogBodySize = 10.sp

/**
 * Height of the log block, and of the loading and empty states that stand in
 * for it. The spacing scale has no vertical size step, so it is named here.
 */
private val LogBlockHeight = 260.dp

/**
 * Diagnostics that does not depend on the Settings screen: reachable straight
 * from the ⋮ menu, so a crash inside Settings can still be reported.
 *
 * Exporting the log is the one action of the screen, so it is the only filled
 * button; the other actions are quiet, and the screen says in words when it is
 * collecting the log or when there is nothing to export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val log = remember(version) { DiagnosticsLogger.readAll() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Presentation only: true while logcat is being captured, so the screen can
    // show what it is waiting for instead of a frozen button row.
    var collecting by remember { mutableStateOf(false) }
    val device = remember(version) {
        com.osintgram.chronos.diagnostics.DeviceMetrics.snapshot(context, BuildConfig.VERSION_NAME)
    }
    val crash = remember(version) {
        log.lineSequence()
            .filter { it.contains("[CRASH]") || it.contains("failed:") }
            .toList()
            .takeLast(6)
            .joinToString("\n")
    }
    val lineCount = log.lineSequence().count { it.isNotBlank() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Text(
                text = "Chronos ${BuildConfig.VERSION_NAME} · $lineCount log lines · " +
                    "${DiagnosticsLogger.sizeBytes() / 1024} KB",
                style = ChronosType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = com.osintgram.chronos.diagnostics.DeviceMetrics.describe(device),
                fontSize = MonoDetailSize,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "session ${DiagnosticsLogger.sessionId}",
                fontSize = MonoDetailSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.md))
            when {
                // Collecting: the spinner is labelled, so it is clear what the
                // wait is for.
                collecting -> LoadingState(
                    label = "Collecting logcat…",
                    modifier = Modifier.height(LogBlockHeight),
                )

                // Nothing logged means nothing to export: an empty state says so
                // in words rather than showing an empty box and live buttons.
                lineCount == 0 -> EmptyState(
                    title = "Nothing to export",
                    subtitle = "No log lines have been recorded yet. Capture logcat, or use the " +
                        "app for a while and come back.",
                    modifier = Modifier.height(LogBlockHeight),
                )

                else -> {
                    Text(
                        text = "Last errors",
                        style = ChronosType.title,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = crash.ifBlank { "No errors logged. Good sign." },
                        fontSize = MonoDetailSize,
                        fontFamily = FontFamily.Monospace,
                        color = if (crash.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(Radii.md),
                            )
                            .padding(Spacing.md),
                    )

                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        text = "Full log",
                        style = ChronosType.title,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = log.takeLast(6000).ifBlank { "Nothing logged yet." },
                        fontSize = LogBodySize,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(Radii.md),
                            )
                            .padding(Spacing.md),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            // Export is the one filled action; the rest are quiet.
            Button(
                enabled = lineCount > 0 && !collecting,
                onClick = {
                    val file = LogExporter.export(
                        context, LogExporter.Format.TXT, "Chronos", BuildConfig.VERSION_NAME,
                    )
                    LogExporter.share(context, file, "text/plain")
                },
            ) { Text("Share log", style = ChronosType.title) }

            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(
                    enabled = lineCount > 0 && !collecting,
                    onClick = {
                        val file = LogExporter.export(
                            context, LogExporter.Format.ZIP, "Chronos", BuildConfig.VERSION_NAME,
                        )
                        LogExporter.share(context, file, "application/zip")
                    },
                ) { Text("Share .zip", style = ChronosType.label) }
                TextButton(
                    enabled = !collecting,
                    onClick = {
                        scope.launch {
                            collecting = true
                            com.osintgram.chronos.diagnostics.LogcatCollector.capture(context)
                            version += 1
                            collecting = false
                        }
                    },
                ) { Text("Capture logcat", style = ChronosType.label) }
                TextButton(
                    enabled = !collecting,
                    onClick = {
                        DiagnosticsLogger.clear()
                        version += 1
                    },
                ) { Text("Clear", style = ChronosType.label) }
            }
        }
    }
}

package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.background
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

/**
 * Diagnostics that does not depend on the Settings screen: reachable straight
 * from the ⋮ menu, so a crash inside Settings can still be reported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val log = remember(version) { DiagnosticsLogger.readAll() }
    val crash = remember(version) {
        log.lineSequence()
            .filter { it.contains("[CRASH]") || it.contains("failed:") }
            .toList()
            .takeLast(6)
            .joinToString("\n")
    }

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
                .padding(16.dp),
        ) {
            Text(
                text = "Chronos ${BuildConfig.VERSION_NAME} · ${log.lineSequence().count { it.isNotBlank() }} log lines",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Last errors",
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = crash.ifBlank { "No errors logged. Good sign." },
                fontSize = 11.sp,
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
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
            )

            Spacer(Modifier.height(16.dp))
            Row {
                TextButton(onClick = {
                    val file = LogExporter.export(
                        context, LogExporter.Format.TXT, "Chronos", BuildConfig.VERSION_NAME,
                    )
                    LogExporter.share(context, file, "text/plain")
                }) { Text("Share log") }
                TextButton(onClick = {
                    val file = LogExporter.export(
                        context, LogExporter.Format.ZIP, "Chronos", BuildConfig.VERSION_NAME,
                    )
                    LogExporter.share(context, file, "application/zip")
                }) { Text("Share .zip") }
                TextButton(onClick = {
                    DiagnosticsLogger.clear()
                    version += 1
                }) { Text("Clear") }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Full log",
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = log.takeLast(6000).ifBlank { "Nothing logged yet." },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
            )
        }
    }
}

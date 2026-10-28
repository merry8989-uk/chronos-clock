package com.osintgram.chronos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * Renders a section, and if anything inside it throws, shows a small error card
 * instead of taking the whole screen down. The failure is written to the log so
 * it can be reported.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Column {
                try {
                    content()
                } catch (t: Throwable) {
                    DiagnosticsLogger.log(
                        "SectionCard",
                        "Section '${title ?: "untitled"}' failed: ${t.stackTraceToString()}",
                    )
                    SectionError(title ?: "This section", t)
                }
            }
        }
    }
}

/** Shows which section failed and why, in place of its content. */
@Composable
fun SectionError(name: String, error: Throwable) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "$name could not be shown",
            color = MaterialTheme.colorScheme.error,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = error.message ?: error.javaClass.simpleName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "The details are in Diagnostics → Logs.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

/**
 * A whole-screen guard, used for Settings: if the screen throws while composing,
 * the app stays alive and says what happened.
 */
@Composable
fun SafeScreen(name: String, content: @Composable () -> Unit) {
    try {
        content()
    } catch (t: Throwable) {
        DiagnosticsLogger.log(
            "SafeScreen",
            "Screen '$name' failed: ${t.stackTraceToString()}",
        )
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Text(
                    text = "$name hit an error",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.message ?: t.javaClass.simpleName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Open Diagnostics from the ⋮ menu to share the log so this can be fixed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
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
}

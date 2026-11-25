package com.osintgram.chronos.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import com.osintgram.chronos.ui.components.EmptyState
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import java.io.File

/**
 * Heights of the thumbnail grid and of the empty state that replaces it. The
 * spacing scale has no vertical size step, so they are named here.
 */
private val GridHeight = 240.dp
private val EmptyStateHeight = 150.dp

/**
 * Picks the picture shown behind the clock when this alarm rings. Images are
 * copied into the app's own storage, so they keep working if the original is
 * deleted or the gallery permission is revoked later.
 *
 * Save is the one filled action: adding, removing and the random switch are all
 * quiet, and an empty selection is explained in words rather than shown as a
 * blank area.
 */
@Composable
fun WallpaperDialog(
    currentPaths: List<String>,
    currentRandom: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var paths by remember { mutableStateOf(currentPaths) }
    var random by remember { mutableStateOf(currentRandom) }
    var version by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(8),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val added = uris.mapNotNull { uri ->
                runCatching {
                    val target = File(dir, "wp_${System.currentTimeMillis()}_${paths.size}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath.takeIf { target.length() > 0 }
                }.onFailure {
                    DiagnosticsLogger.log("Wallpaper", "Import failed: ${it.message}")
                }.getOrNull()
            }
            paths = (paths + added).distinct().take(8)
            version += 1
        }
    }

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
                Text("Background for the alarm", style = ChronosType.headline)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Shown behind the clock when this alarm rings.",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add images")
                        }
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Text(
                        text = "Add image",
                        style = ChronosType.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Random",
                        style = ChronosType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Switch(
                        checked = random,
                        onCheckedChange = { random = it },
                        enabled = paths.size > 1,
                    )
                }

                Spacer(Modifier.height(Spacing.md))
                if (paths.isEmpty()) {
                    // Empty state: no picture chosen yet.
                    EmptyState(
                        title = "No background yet",
                        subtitle = "The ring screen uses the plain dark theme until you add an " +
                            "image.",
                        modifier = Modifier.height(EmptyStateHeight),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(GridHeight),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(paths, key = { it }) { path ->
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(Radii.md))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                val bitmap = remember(path, version) {
                                    runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(96.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        paths = paths - path
                                        version += 1
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove image",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
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
                    TextButton(onClick = onDismiss) { Text("Cancel", style = ChronosType.label) }
                    Spacer(Modifier.size(Spacing.sm))
                    // The one filled action of the dialog.
                    Button(onClick = { onSave(paths, random && paths.size > 1) }) {
                        Text("Save", style = ChronosType.title)
                    }
                }
            }
        }
    }
}

package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osintgram.chronos.permissions.SpecialAccess
import com.osintgram.chronos.ui.theme.ChronosType
import com.osintgram.chronos.ui.theme.Haptics
import com.osintgram.chronos.ui.theme.Radii
import com.osintgram.chronos.ui.theme.Spacing
import com.osintgram.chronos.ui.theme.pressable

/**
 * The state of one access, said in words. A row never relies on its tint alone:
 * "Granted" and "Not granted" are the shared vocabulary, and the Android
 * wording from [SpecialAccess] stays underneath as the detail.
 */
private fun statusWord(granted: Boolean?): String = when (granted) {
    true -> "Granted"
    false -> "Not granted"
    // A null state means the platform does not expose the switch at all.
    null -> "Unknown"
}

/**
 * The special accesses Android keeps outside the permission dialog: what each
 * one is, whether it is on, and one tap to the system screen that changes it.
 *
 * The state is read fresh on every recomposition, so coming back from system
 * settings shows the new state without a refresh button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialAccessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Read fresh every time the screen is shown *and* every time the app comes
    // back to the foreground: the user leaves to a system screen and returns,
    // and without this the row kept showing the old state.
    var tick by remember { mutableIntStateOf(0) }
    val items = remember(tick) { SpecialAccess.items(context) }
    val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Special access") },
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
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(
                    text = "Android keeps these switches in system settings, so Chronos can only " +
                        "show the state and take you to the right screen. Tap a row to open it.",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(items, key = { it.title }) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radii.lg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(enabled = item.openSettings != null) {
                            Haptics.tick(context)
                            item.openSettings?.invoke()
                            tick++
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (item.granted == true) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                    },
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (item.granted == true) Icons.Filled.CheckCircle
                                else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (item.granted == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = ChronosType.body,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            // The word comes first and the tint supports it, so
                            // the state is readable without seeing the colour.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = statusWord(item.granted),
                                    style = ChronosType.label,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.granted == true) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = item.state,
                                    style = ChronosType.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = item.usedBy,
                                style = ChronosType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item.openSettings != null) {
                            if (item.granted == true) {
                                // Nothing to fix, so the row keeps a quiet
                                // affordance rather than a button.
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                // The one action of the row: open the system
                                // screen that grants this access.
                                Spacer(Modifier.width(Spacing.sm))
                                Button(
                                    onClick = {
                                        Haptics.tick(context)
                                        item.openSettings?.invoke()
                                        tick++
                                    },
                                    contentPadding = PaddingValues(
                                        horizontal = Spacing.md,
                                        vertical = Spacing.xs,
                                    ),
                                ) { Text("Open settings", style = ChronosType.label) }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Notification access is used only by Screen time, and only to count how " +
                        "many notifications each app posted. The listener never stores a title, " +
                        "text or sender.",
                    style = ChronosType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

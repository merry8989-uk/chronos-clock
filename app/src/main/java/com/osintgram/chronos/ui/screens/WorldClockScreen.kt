package com.osintgram.chronos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.WorldClockStore
import com.osintgram.chronos.ui.components.EmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/** One selectable city, derived from the device's own timezone database. */
data class CityOption(val zoneId: String, val city: String, val region: String)

private fun buildCityIndex(): List<CityOption> =
    ZoneId.getAvailableZoneIds()
        .filter { it.contains('/') }
        .map { id ->
            val parts = id.split('/')
            val city = parts.last().replace('_', ' ')
            val region = parts.dropLast(1).joinToString(" / ").replace('_', ' ')
            CityOption(id, city, region)
        }
        .sortedBy { it.city.lowercase(Locale.US) }

@Composable
fun WorldClockScreen(modifier: Modifier = Modifier) {
    val zones = WorldClockStore.all()
    val use24 = Settings.use24Hour.value
    val showSeconds = Settings.showSeconds.value
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (zones.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    title = "No cities yet",
                    subtitle = "Add the cities you care about to see their time at a glance.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(zones, key = { it }) { zoneId ->
                    CityCard(
                        zoneId = zoneId,
                        nowMillis = nowMillis,
                        use24 = use24,
                        showSeconds = showSeconds,
                        onRemove = { WorldClockStore.remove(zoneId) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add city")
            }
            TextButton(onClick = {
                WorldClockStore.add(TimeZone.getDefault().id)
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Text("  Use my location")
            }
        }
    }

    if (showPicker) {
        CityPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { city ->
                WorldClockStore.add(city.zoneId)
                showPicker = false
            },
        )
    }
}

@Composable
private fun CityCard(
    zoneId: String,
    nowMillis: Long,
    use24: Boolean,
    showSeconds: Boolean,
    onRemove: () -> Unit,
) {
    val zone = remember(zoneId) { runCatching { ZoneId.of(zoneId) }.getOrNull() }
    if (zone == null) return
    val instant = Instant.ofEpochMilli(nowMillis)
    val local = ZonedDateTime.now()
    val there = ZonedDateTime.ofInstant(instant, zone)

    val pattern = when {
        use24 && showSeconds -> "HH:mm:ss"
        use24 -> "HH:mm"
        showSeconds -> "hh:mm:ss"
        else -> "hh:mm"
    }
    val timeText = there.format(DateTimeFormatter.ofPattern(pattern))
    val meridiem = if (use24) "" else there.format(DateTimeFormatter.ofPattern("a")).lowercase(Locale.US)
    val dateText = there.format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    val hereOffset: ZoneOffset = local.offset
    val thereOffset: ZoneOffset = there.offset
    val diffSeconds = thereOffset.totalSeconds - hereOffset.totalSeconds
    val sign = if (diffSeconds >= 0) "+" else "−"
    val absSeconds = kotlin.math.abs(diffSeconds)
    val diffText = String.format(
        "%s%d:%02d",
        sign,
        absSeconds / 3600,
        (absSeconds % 3600) / 60,
    )
    val dayDiff = there.toLocalDate().toEpochDay() - local.toLocalDate().toEpochDay()
    val dayText = when {
        dayDiff > 0 -> " · tomorrow"
        dayDiff < 0 -> " · yesterday"
        else -> ""
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zoneId.split('/').last().replace('_', ' '),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$dateText$dayText · GMT$diffText from you",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (meridiem.isNotEmpty()) {
                        Text(
                            text = meridiem,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove city",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CityPickerDialog(
    onDismiss: () -> Unit,
    onPick: (CityOption) -> Unit,
) {
    val allCities = remember { buildCityIndex() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) allCities
        else allCities.filter {
            it.city.contains(query, ignoreCase = true) || it.region.contains(query, ignoreCase = true)
        }
    }

    // "A", "B", ... headers, and where each letter starts in the list.
    val grouped = remember(filtered) {
        filtered.groupBy { it.city.firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap()
    }
    val letterIndex = remember(grouped) {
        val map = mutableMapOf<Char, Int>()
        var running = 0
        grouped.forEach { (letter, cities) ->
            map[letter] = running
            running += cities.size + 1
        }
        map
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a city") },
        text = {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Search for cities") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filtered.isEmpty()) {
                        Text(
                            text = "No search result",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.height(320.dp),
                        ) {
                            grouped.forEach { (letter, cities) ->
                                item(key = "header-$letter") {
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                }
                                items(cities, key = { it.zoneId }) { city ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = city.city,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "${city.region} · ${offsetLabel(city.zoneId)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = { onPick(city) }) { Text("Add") }
                                    }
                                }
                            }
                        }
                    }
                }
                // Alphabet rail: tap a letter to jump to that group.
                Column(
                    modifier = Modifier.width(22.dp).height(320.dp).padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    letterIndex.keys.forEach { letter ->
                        Text(
                            text = letter.toString(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(vertical = 1.dp)
                                .clickable {
                                    val target = letterIndex[letter] ?: 0
                                    scope.launch {
                                        listState.scrollToItem(target.coerceAtLeast(0))
                                    }
                                },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun offsetLabel(zoneId: String): String = runCatching {
    val zone = ZoneId.of(zoneId)
    val offset = zone.rules.getOffset(Instant.now())
    val total = offset.totalSeconds
    val sign = if (total >= 0) "+" else "-"
    val abs = kotlin.math.abs(total)
    String.format(Locale.US, "GMT%s%02d:%02d", sign, abs / 3600, (abs % 3600) / 60)
}.getOrDefault("GMT")

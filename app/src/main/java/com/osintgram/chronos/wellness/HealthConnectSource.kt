package com.osintgram.chronos.wellness

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Optional sync from Health Connect: steps, water and sleep already recorded by
 * another app on the phone.
 *
 * Three kinds of access are requested, and each one is used:
 *  - the three record reads, for the Today card;
 *  - background read, so the six-hourly sync can run while the app is closed;
 *  - history read, so the one-off import can reach past Health Connect's
 *    default 30-day window (the import covers 84 days).
 *
 * Everything is guarded: a missing Health Connect, an old provider or a refused
 * permission is reported, never crashed on, and the Wellness tab keeps working
 * on its own numbers.
 */
object HealthConnectSource {

    /** The record reads the Today card needs. */
    val dataPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    /** Lets the six-hourly worker read while the app is not on screen. */
    val backgroundPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** Lets the import read older than Health Connect's default 30 days. */
    val historyPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    /** Everything the permission screen asks for, in one request. */
    val permissions: Set<String> = dataPermissions + backgroundPermission + historyPermission

    /** How far back the one-off import reaches. */
    const val HISTORY_DAYS = 84

    enum class Status { Unavailable, UpdateRequired, Ready }

    /** What one read produced. Nulls mean "that record type had no data". */
    data class Snapshot(
        val steps: Int?,
        val waterMl: Int?,
        val sleepMinutes: Int?,
    ) {
        val hasAny: Boolean get() = steps != null || waterMl != null || sleepMinutes != null
    }

    fun status(context: Context): Status = when (
        HealthConnectClient.getSdkStatus(context)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> Status.Ready
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UpdateRequired
        else -> Status.Unavailable
    }

    suspend fun grantedPermissions(context: Context): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (status(context) != Status.Ready) return@runCatching emptySet()
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.onFailure {
            DiagnosticsLogger.log("HealthConnect", "Permission read failed: ${it.message}")
        }.getOrDefault(emptySet())
    }

    /** The record reads are what the Today card needs to show synced numbers. */
    suspend fun granted(context: Context): Boolean =
        grantedPermissions(context).containsAll(dataPermissions)

    /** True only when the background read was granted as well. */
    suspend fun hasBackground(context: Context): Boolean =
        grantedPermissions(context).contains(backgroundPermission)

    suspend fun hasHistory(context: Context): Boolean =
        grantedPermissions(context).contains(historyPermission)

    /** Steps, water and sleep for one day, as Health Connect sees them. */
    suspend fun readDay(context: Context, date: LocalDate): Snapshot? = withContext(Dispatchers.IO) {
        runCatching {
            if (status(context) != Status.Ready) return@runCatching null
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
            val now = Instant.now()
            val end = if (endOfDay.isAfter(now)) now else endOfDay
            if (!end.isAfter(start)) return@runCatching null
            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        HydrationRecord.VOLUME_TOTAL,
                        SleepSessionRecord.SLEEP_DURATION_TOTAL,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            Snapshot(
                steps = result[StepsRecord.COUNT_TOTAL]?.toInt(),
                waterMl = result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.toInt(),
                sleepMinutes = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toInt(),
            )
        }.onFailure {
            DiagnosticsLogger.log("HealthConnect", "Read failed for $date: ${it.message}")
        }.getOrNull()
    }

    suspend fun readToday(context: Context): Snapshot? = readDay(context, LocalDate.now())

    /**
     * Imports the last [days] days, one day at a time. Values only ever go up,
     * so a day the user typed by hand is never overwritten. Returns how many
     * days actually had data.
     */
    suspend fun backfillHistory(context: Context, days: Int = HISTORY_DAYS): Int {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        var imported = 0
        for (offset in (days - 1) downTo 0) {
            val date = today.minusDays(offset.toLong())
            val snapshot = readDay(context, date) ?: continue
            if (!snapshot.hasAny) continue
            val key = date.format(WellnessLogic.ISO_DATE)
            withContext(Dispatchers.Main) {
                WellnessStore.updateDay(key) { day ->
                    WellnessLogic.mergeHealthSnapshot(
                        day = day,
                        steps = snapshot.steps,
                        waterMl = snapshot.waterMl,
                        sleepMinutes = snapshot.sleepMinutes,
                    )
                }
            }
            imported++
        }
        DiagnosticsLogger.log("HealthConnect", "History import wrote $imported day(s)")
        return imported
    }

    /** Opens Health Connect's own settings screen, for the uninstall/manage path. */
    fun openSettings(context: Context): Boolean = runCatching {
        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrElse {
        DiagnosticsLogger.log("HealthConnect", "Settings screen unavailable: ${it.message}")
        false
    }
}

/** The state the Today card shows for Health Connect. */
data class HealthConnectState(
    val status: HealthConnectSource.Status,
    val granted: Boolean,
    val hasBackground: Boolean,
    val hasHistory: Boolean,
    val message: String,
    val busy: Boolean,
    val importedDays: Int?,
)

/** What the Today card can ask Health Connect to do. */
data class HealthConnectActions(
    val connect: () -> Unit,
    val sync: () -> Unit,
    val importHistory: () -> Unit,
)

/**
 * Wires the permission launcher, the one-shot read and the history import
 * together, so the screen only has to say "connect", "sync" or "import" and
 * render the resulting numbers.
 */
@Composable
fun rememberHealthConnect(enabled: Boolean = true): Pair<HealthConnectState, HealthConnectActions> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = remember(context) { HealthConnectSource.status(context) }
    var granted by remember { mutableStateOf(false) }
    var hasBackground by remember { mutableStateOf(false) }
    var hasHistory by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var importedDays by remember { mutableIntStateOf(-1) }

    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { result ->
        granted = result.containsAll(HealthConnectSource.dataPermissions)
        hasBackground = result.contains(HealthConnectSource.backgroundPermission)
        hasHistory = result.contains(HealthConnectSource.historyPermission)
        message = when {
            granted && hasBackground -> "Health Connect connected, background sync on"
            granted -> "Connected, but background read was not allowed"
            else -> "Permission not granted"
        }
        if (granted) {
            scope.launch {
                val read = HealthConnectSource.readToday(context)
                read?.let { WellnessSync.apply(context, it) }
                WellnessSync.scheduleIfEnabled(context)
            }
        }
    }

    LaunchedEffect(status, enabled) {
        if (status != HealthConnectSource.Status.Ready || !enabled) return@LaunchedEffect
        val grantedNow = HealthConnectSource.grantedPermissions(context)
        granted = grantedNow.containsAll(HealthConnectSource.dataPermissions)
        hasBackground = grantedNow.contains(HealthConnectSource.backgroundPermission)
        hasHistory = grantedNow.contains(HealthConnectSource.historyPermission)
        if (granted) {
            HealthConnectSource.readToday(context)?.let { WellnessSync.apply(context, it) }
        }
    }

    val state = HealthConnectState(
        status = status,
        granted = granted,
        hasBackground = hasBackground,
        hasHistory = hasHistory,
        message = message,
        busy = busy,
        importedDays = importedDays.takeIf { it >= 0 },
    )

    val actions = HealthConnectActions(
        connect = {
            when {
                status != HealthConnectSource.Status.Ready ->
                    message = "Health Connect is not available on this device"
                else -> runCatching { launcher.launch(HealthConnectSource.permissions) }
                    .onFailure { message = "Permission screen could not open" }
            }
        },
        sync = {
            scope.launch {
                busy = true
                val read = HealthConnectSource.readToday(context)
                if (read == null) {
                    message = "Nothing to read yet"
                } else {
                    WellnessSync.apply(context, read)
                    message = "Synced just now"
                }
                busy = false
            }
        },
        importHistory = {
            scope.launch {
                busy = true
                message = "Importing ${HealthConnectSource.HISTORY_DAYS} days…"
                importedDays = runCatching { HealthConnectSource.backfillHistory(context) }
                    .getOrDefault(0)
                message = if (importedDays == 0) {
                    "No history found in Health Connect"
                } else {
                    "$importedDays day(s) imported"
                }
                busy = false
            }
        },
    )
    return state to actions
}

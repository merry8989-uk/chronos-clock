package com.osintgram.chronos.wellness

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.time.ZoneId

/**
 * Optional sync from Health Connect: steps, water and sleep already recorded by
 * another app on the phone. Everything here is guarded — when Health Connect is
 * missing, too old, or the user has not granted a permission, the feature
 * reports that state and the Wellness tab keeps working on its own numbers.
 */
object HealthConnectSource {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    enum class Status { Unavailable, UpdateRequired, Ready }

    /** What one read produced. Nulls mean "that record type had no data". */
    data class Snapshot(
        val steps: Int?,
        val waterMl: Int?,
        val sleepMinutes: Int?,
    )

    fun status(context: Context): Status = when (
        HealthConnectClient.getSdkStatus(context)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> Status.Ready
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UpdateRequired
        else -> Status.Unavailable
    }

    suspend fun granted(context: Context): Boolean = runCatching {
        if (status(context) != Status.Ready) return false
        val client = HealthConnectClient.getOrCreate(context)
        client.permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    /** Steps, water and sleep recorded today, as Health Connect sees them. */
    suspend fun readToday(context: Context): Snapshot? = withContext(Dispatchers.IO) {
        runCatching {
            if (status(context) != Status.Ready) return@runCatching null
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val start = Instant.now().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val end = Instant.now()
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
            DiagnosticsLogger.log("HealthConnect", "Read failed: ${it.message}")
        }.getOrNull()
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
    val lastRead: HealthConnectSource.Snapshot?,
    val message: String,
)

/**
 * Wires the permission launcher and the one-shot read together, so the screen
 * only has to say "connect" or "sync" and render the resulting numbers.
 */
@Composable
fun rememberHealthConnect(enabled: Boolean = true): Pair<HealthConnectState, (Boolean) -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = remember(context) { HealthConnectSource.status(context) }
    var granted by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<HealthConnectSource.Snapshot?>(null) }
    var message by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { result ->
        granted = result.containsAll(HealthConnectSource.permissions)
        message = if (granted) "Health Connect connected" else "Permission not granted"
        if (granted) {
            scope.launch {
                val read = HealthConnectSource.readToday(context)
                snapshot = read
                read?.let { applySnapshot(it) }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(status, enabled) {
        if (status != HealthConnectSource.Status.Ready || !enabled) return@LaunchedEffect
        granted = HealthConnectSource.granted(context)
        if (granted) {
            val read = HealthConnectSource.readToday(context)
            snapshot = read
            read?.let { applySnapshot(it) }
        }
    }

    val state = HealthConnectState(
        status = status,
        granted = granted,
        lastRead = snapshot,
        message = message,
    )
    return state to { wantsConnect: Boolean ->
        when {
            status != HealthConnectSource.Status.Ready -> {
                message = "Health Connect is not available on this device"
            }
            wantsConnect && !granted -> runCatching { launcher.launch(HealthConnectSource.permissions) }
                .onFailure { message = "Permission screen could not open" }
            else -> scope.launch {
                val read = HealthConnectSource.readToday(context)
                snapshot = read
                if (read == null) message = "Nothing to read yet" else applySnapshot(read)
            }
        }
    }
}

/**
 * Health Connect numbers are written into today's record only when they are
 * higher than what is already stored, so a sync never erases a manual entry.
 */
private fun applySnapshot(snapshot: HealthConnectSource.Snapshot) {
    val today = WellnessLogic.todayKey()
    val day = WellnessStore.day(today)
    snapshot.steps?.let { if (it > day.steps) WellnessStore.setSteps(it, today) }
    snapshot.waterMl?.let { if (it > day.waterMl) WellnessStore.addWater(it - day.waterMl, today) }
    snapshot.sleepMinutes?.let { if (it > day.sleepMinutes) WellnessStore.setSleepMinutes(it, today) }
}

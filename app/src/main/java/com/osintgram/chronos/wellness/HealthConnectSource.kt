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
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The phone's Health Connect: read steps, water and sleep, and write back the
 * water you log and the workouts you finish.
 *
 * Permissions are split into two groups on purpose:
 *  - **required** — the three record reads and the two record writes. "Connect"
 *    means these; the card reports connected only when all five are granted.
 *  - **optional** — background read (for the six-hourly sync) and history read
 *    (for the 84-day import). Some Health Connect builds do not know these
 *    strings, and asking for an unknown permission makes the whole request
 *    fail, which is exactly what "it never connects" looks like. So the full
 *    set is requested first and the request is retried with the required set
 *    alone if that launch is rejected.
 */
object HealthConnectSource {

    /**
     * Built lazily and behind runCatching: these calls touch the Health Connect
     * client's classes, and on a device whose client is older than the one this
     * app was compiled against they can fail while the object is being
     * initialised — which used to take the whole app down on first use.
     */
    val readPermissions: Set<String> by lazy {
        runCatching {
            setOf(
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(HydrationRecord::class),
                HealthPermission.getReadPermission(SleepSessionRecord::class),
            )
        }.onFailure {
            DiagnosticsLogger.log("HealthConnect", "Read permission names failed: ${it.message}")
        }.getOrDefault(emptySet())
    }

    val writePermissions: Set<String> by lazy {
        runCatching {
            setOf(
                HealthPermission.getWritePermission(HydrationRecord::class),
                HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            )
        }.onFailure {
            DiagnosticsLogger.log("HealthConnect", "Write permission names failed: ${it.message}")
        }.getOrDefault(emptySet())
    }

    /** What "connected" means: read and write, both. */
    val requiredPermissions: Set<String> by lazy { readPermissions + writePermissions }

    val backgroundPermission: String by lazy {
        runCatching { HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND }
            .getOrDefault("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")
    }

    val historyPermission: String by lazy {
        runCatching { HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY }
            .getOrDefault("android.permission.health.READ_HEALTH_DATA_HISTORY")
    }

    val optionalPermissions: Set<String> by lazy { setOf(backgroundPermission, historyPermission) }

    /** Everything asked for in the first attempt. */
    val permissions: Set<String> by lazy { requiredPermissions + optionalPermissions }

    /** False when this build cannot even name the permissions (old client). */
    fun canRequestPermissions(): Boolean = requiredPermissions.isNotEmpty()

    const val HISTORY_DAYS = 84

    enum class Status { Unavailable, UpdateRequired, Ready }

    data class Snapshot(
        val steps: Int?,
        val waterMl: Int?,
        val sleepMinutes: Int?,
    ) {
        val hasAny: Boolean get() = steps != null || waterMl != null || sleepMinutes != null
    }

    fun status(context: Context): Status = runCatching {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> Status.Ready
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UpdateRequired
            else -> Status.Unavailable
        }
    }.onFailure {
        DiagnosticsLogger.log("HealthConnect", "Status check failed: ${it.javaClass.simpleName}: ${it.message}")
    }.getOrDefault(Status.Unavailable)

    /** One guarded place where the client is created. */
    private fun healthClient(context: Context): HealthConnectClient? = runCatching {
        if (status(context) != Status.Ready) return null
        HealthConnectClient.getOrCreate(context)
    }.onFailure {
        DiagnosticsLogger.log("HealthConnect", "Client creation failed: ${it.javaClass.simpleName}: ${it.message}")
    }.getOrNull()

    suspend fun grantedPermissions(context: Context): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            val hc = healthClient(context) ?: return@runCatching emptySet()
            hc.permissionController.getGrantedPermissions()
        }.onFailure {
            DiagnosticsLogger.log("HealthConnect", "Permission read failed: ${it.message}")
        }.getOrDefault(emptySet())
    }

    /** Connected means read *and* write are granted. */
    suspend fun granted(context: Context): Boolean {
        val required = requiredPermissions
        if (required.isEmpty()) return false
        return grantedPermissions(context).containsAll(required)
    }

    suspend fun hasRead(context: Context): Boolean =
        grantedPermissions(context).containsAll(readPermissions)

    suspend fun hasWrite(context: Context): Boolean =
        grantedPermissions(context).containsAll(writePermissions)

    suspend fun hasBackground(context: Context): Boolean =
        grantedPermissions(context).contains(backgroundPermission)

    suspend fun hasHistory(context: Context): Boolean =
        grantedPermissions(context).contains(historyPermission)

    // ---- Reads -------------------------------------------------------------

    suspend fun readDay(context: Context, date: LocalDate): Snapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val hc = healthClient(context) ?: return@runCatching null
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
            val now = Instant.now()
            val end = if (endOfDay.isAfter(now)) now else endOfDay
            if (!end.isAfter(start)) return@runCatching null
            val result = hc.aggregate(
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

    // ---- Writes ------------------------------------------------------------

    /** Writes a glass of water into Health Connect. False when it was not written. */
    suspend fun writeHydration(
        context: Context,
        millilitres: Int,
        at: Long = System.currentTimeMillis(),
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (millilitres <= 0) return@runCatching false
            val hc = healthClient(context) ?: return@runCatching false
            if (!hasWrite(context)) return@runCatching false
            val instant = Instant.ofEpochMilli(at)
            val offset = ZoneId.systemDefault().rules.getOffset(instant)
            hc.insertRecords(
                listOf(
                    HydrationRecord(
                        startTime = instant,
                        startZoneOffset = offset,
                        endTime = instant,
                        endZoneOffset = offset,
                        volume = Volume.milliliters(millilitres.toDouble()),
                    ),
                ),
            )
            DiagnosticsLogger.log("HealthConnect", "Wrote ${millilitres}ml of water")
            true
        }.onFailure {
            DiagnosticsLogger.log("HealthConnect", "Water write failed: ${it.message}")
        }.getOrDefault(false)
    }

    /** Writes a finished workout as an exercise session. */
    suspend fun writeExercise(
        context: Context,
        title: String,
        startMillis: Long,
        endMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (endMillis <= startMillis) return@runCatching false
            val hc = healthClient(context) ?: return@runCatching false
            if (!hasWrite(context)) return@runCatching false
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(startMillis)
            val end = Instant.ofEpochMilli(endMillis)
            hc.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = start,
                        startZoneOffset = zone.rules.getOffset(start),
                        endTime = end,
                        endZoneOffset = zone.rules.getOffset(end),
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                        title = title,
                    ),
                ),
            )
            DiagnosticsLogger.log("HealthConnect", "Wrote exercise session: $title")
            true
        }.onFailure {
            DiagnosticsLogger.log("HealthConnect", "Exercise write failed: ${it.message}")
        }.getOrDefault(false)
    }

    /** Opens Health Connect's own settings screen. */
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
 * together. The permission request falls back to the required set when the
 * device's Health Connect rejects the optional background/history strings,
 * which is the difference between "connects" and "the button does nothing".
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
        granted = result.containsAll(HealthConnectSource.requiredPermissions)
        hasBackground = result.contains(HealthConnectSource.backgroundPermission)
        hasHistory = result.contains(HealthConnectSource.historyPermission)
        message = when {
            granted && hasBackground -> "Connected: read and write, background sync on"
            granted -> "Connected: read and write"
            else -> "Permissions not granted — read and write are both needed"
        }
        if (granted) {
            scope.launch {
                HealthConnectSource.readToday(context)?.let { WellnessSync.apply(context, it) }
                WellnessSync.scheduleIfEnabled(context)
            }
        }
    }

    fun requestPermissions() {
        if (status != HealthConnectSource.Status.Ready) {
            message = "Health Connect is not available on this device"
            return
        }
        if (!HealthConnectSource.canRequestPermissions()) {
            message = "This Health Connect build is too old for Chronos — update Health Connect"
            DiagnosticsLogger.log("HealthConnect", "Permission names unavailable; not launching")
            return
        }
        runCatching { launcher.launch(HealthConnectSource.permissions) }
            .onSuccess { return }
            .onFailure { first ->
                DiagnosticsLogger.log(
                    "HealthConnect",
                    "Full permission request rejected (${first.message}); retrying with the required set",
                )
                runCatching { launcher.launch(HealthConnectSource.requiredPermissions) }
                    .onSuccess {
                        message = "This Health Connect build has no background/history access; " +
                            "asked for read and write only"
                    }
                    .onFailure { second ->
                        message = "The Health Connect permission screen could not open: " +
                            (second.message ?: second.javaClass.simpleName)
                        DiagnosticsLogger.log("HealthConnect", "Permission launch failed: ${second.message}")
                    }
            }
    }

    LaunchedEffect(status, enabled) {
        if (status != HealthConnectSource.Status.Ready || !enabled) return@LaunchedEffect
        val grantedNow = HealthConnectSource.grantedPermissions(context)
        granted = grantedNow.containsAll(HealthConnectSource.requiredPermissions)
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
        connect = { requestPermissions() },
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
                message = if (importedDays == 0) "No history found in Health Connect"
                else "$importedDays day(s) imported"
                busy = false
            }
        },
    )
    return state to actions
}

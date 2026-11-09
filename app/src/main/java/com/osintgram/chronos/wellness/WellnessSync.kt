package com.osintgram.chronos.wellness

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The background half of the Health Connect sync.
 *
 * A six-hourly WorkManager job reads today's steps, water and sleep and folds
 * them into today's record. It needs the background read permission — without
 * it, Health Connect only answers while the app is on screen, so the worker
 * exits quietly instead of failing.
 */
object WellnessSync {

    const val WORK_NAME = "wellness-health-sync"
    private const val INTERVAL_HOURS = 6L

    /** Folds a reading into today's record. Values only ever go up. */
    suspend fun apply(context: Context, snapshot: HealthConnectSource.Snapshot) {
        val today = WellnessLogic.todayKey()
        withContext(Dispatchers.Main) {
            WellnessStore.updateDay(today) { day ->
                WellnessLogic.mergeHealthSnapshot(
                    day = day,
                    steps = snapshot.steps,
                    waterMl = snapshot.waterMl,
                    sleepMinutes = snapshot.sleepMinutes,
                )
            }
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WellnessSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            DiagnosticsLogger.log("WellnessSync", "Scheduled every ${INTERVAL_HOURS}h")
        }.onFailure {
            DiagnosticsLogger.log("WellnessSync", "Could not schedule sync: ${it.message}")
        }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
        DiagnosticsLogger.log("WellnessSync", "Cancelled")
    }

    /** Schedules when the user has the sync on, cancels when they turn it off. */
    fun scheduleIfEnabled(context: Context) {
        if (Settings.healthConnectSync.value) schedule(context) else cancel(context)
    }
}

/**
 * Reads today's Health Connect numbers in the background. Every reason it
 * cannot work is a quiet success rather than a retry loop — there is nothing to
 * retry when the user has not granted the permission.
 */
class WellnessSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!Settings.healthConnectSync.value) return Result.success()
        val context = applicationContext
        if (HealthConnectSource.status(context) != HealthConnectSource.Status.Ready) {
            return Result.success()
        }
        if (!HealthConnectSource.granted(context)) return Result.success()
        if (!HealthConnectSource.hasBackground(context)) {
            DiagnosticsLogger.log("WellnessSync", "Background read not granted, skipping")
            return Result.success()
        }
        val snapshot = HealthConnectSource.readToday(context)
            ?: return Result.retry()
        WellnessSync.apply(context, snapshot)
        DiagnosticsLogger.log(
            "WellnessSync",
            "Synced steps=${snapshot.steps} water=${snapshot.waterMl} sleep=${snapshot.sleepMinutes}",
        )
        return Result.success()
    }
}

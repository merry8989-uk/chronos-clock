package com.osintgram.chronos.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads what the phone already knows about app usage, through UsageStatsManager.
 *
 * This needs the **Usage access** special permission, which the user grants in
 * system settings — the app cannot ask for it with a normal dialog. Everything
 * here is guarded: without the permission the screen says so and shows nothing
 * rather than zeros that look like real data.
 */
object UsageSource {

    fun hasAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun openAccessSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.onFailure {
        DiagnosticsLogger.log("ScreenTime", "Usage access screen unavailable: ${it.message}")
    }.getOrDefault(false)

    /** Today's usage, per app, with launches and screen-on/unlock counts. */
    fun today(context: Context, notifications: Map<String, Int> = emptyMap()): DayScreenTime {
        if (!hasAccess(context)) return DayScreenTime()
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        return runCatching { read(context, start, end, notifications) }
            .onFailure {
                DiagnosticsLogger.log("ScreenTime", "Usage read failed: ${it.javaClass.simpleName}: ${it.message}")
            }
            .getOrDefault(DayScreenTime())
    }

    /** Usage for any window — the today call is this with today's bounds. */
    fun read(
        context: Context,
        startMillis: Long,
        endMillis: Long,
        notifications: Map<String, Int> = emptyMap(),
    ): DayScreenTime {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return DayScreenTime()

        // Time in the foreground per package.
        val totals = mutableMapOf<String, Long>()
        runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMillis, endMillis)
                .orEmpty()
                .forEach { stats ->
                    if (stats.totalTimeInForeground > 0) {
                        totals[stats.packageName] =
                            (totals[stats.packageName] ?: 0L) + stats.totalTimeInForeground
                    }
                }
        }.onFailure {
            DiagnosticsLogger.log("ScreenTime", "queryUsageStats failed: ${it.message}")
        }

        // Launches, screen-on and unlocks from the event stream.
        val launches = mutableMapOf<String, Int>()
        var screenOn = 0
        var unlocks = 0
        runCatching {
            val events = manager.queryEvents(startMillis, endMillis)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    resumedEventType() -> {
                        val pkg = event.packageName ?: continue
                        launches[pkg] = (launches[pkg] ?: 0) + 1
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn++
                    UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
                }
            }
        }.onFailure {
            DiagnosticsLogger.log("ScreenTime", "queryEvents failed: ${it.message}")
        }

        val packages = (totals.keys + launches.keys + notifications.keys)
            .filter { it.isNotBlank() && it != context.packageName }
        val apps = packages.map { pkg ->
            AppUsage(
                packageName = pkg,
                label = labelFor(context, pkg),
                foregroundMillis = totals[pkg] ?: 0L,
                launches = launches[pkg] ?: 0,
                notifications = notifications[pkg] ?: 0,
            )
        }

        return DayScreenTime(
            totalForegroundMillis = totals.filterKeys { it != context.packageName }.values.sum(),
            screenOnCount = screenOn,
            unlockCount = unlocks,
            apps = ScreenTimeLogic.sortByUsage(apps),
        )
    }

    /** The package currently in the foreground, used by the limit watcher. */
    fun foregroundPackage(context: Context, lookBackMillis: Long = 15_000L): String? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        return runCatching {
            val end = System.currentTimeMillis()
            val events = manager.queryEvents(end - lookBackMillis, end)
            val event = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == resumedEventType()) last = event.packageName
            }
            last
        }.getOrNull()
    }

    /** Usage time for one package today, for the limit check. */
    fun foregroundMillisToday(context: Context, packageName: String): Long =
        today(context).apps.firstOrNull { it.packageName == packageName }?.foregroundMillis ?: 0L

    fun labelFor(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName.substringAfterLast('.'))

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** API 29 renamed MOVE_TO_FOREGROUND to ACTIVITY_RESUMED. */
    private fun resumedEventType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    /** Kept for callers that want to know whether usage stats exist at all. */
    fun available(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITY_RECOGNITION) || hasAccess(context)
}

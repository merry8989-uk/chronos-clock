package com.osintgram.chronos.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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
        // The same boundary the limits reset on, so "today" means the same
        // thing to the numbers and to the blocker.
        val start = DayBoundary.startMillis()
        val end = System.currentTimeMillis()
        return runCatching { read(context, start, end, notifications) }
            .onFailure {
                DiagnosticsLogger.log("ScreenTime", "Usage read failed: ${it.javaClass.simpleName}: ${it.message}")
            }
            .getOrDefault(DayScreenTime())
    }

    /**
     * Today's foreground time in 24 hourly buckets, the shape the hourly chart
     * draws.
     *
     * The buckets come from the event stream rather than from the per-app
     * totals, because only the events know *when* an app was in front. An empty
     * list means "could not read" (or no usage access), which the screen shows
     * as a quiet note instead of a flat row of bars that would read as a day of
     * no use.
     */
    fun hourlyToday(context: Context): List<Long> {
        if (!hasAccess(context)) return emptyList()
        // The same boundary the limits reset on, so "today" means the same
        // thing to the numbers and to the blocker.
        val start = DayBoundary.startMillis()
        val end = System.currentTimeMillis()
        val manager = usageManager(context) ?: return emptyList()
        return runCatching {
            val segments = scanEvents(manager, start, end).segments
                .filter { it.packageName.isNotBlank() && it.packageName != context.packageName }
            ScreenTimeLogic.hourlyBuckets(segments, start)
        }.onFailure {
            DiagnosticsLogger.log("ScreenTime", "Hourly read failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrDefault(emptyList())
    }

    /**
     * The last [days] days of screen time, one total per day, oldest first.
     *
     * The trend row exists so a single heavy day can be read against the week
     * it sits in; the totals are the same per-app foreground figures the rest
     * of the tab uses, summed per day, so the row and the cards agree. Days the
     * phone was not used come back as zero and the average ignores them.
     */
    fun week(context: Context, days: Int = 7): WeekUsage {
        if (!hasAccess(context)) return WeekUsage()
        val manager = usageManager(context) ?: return WeekUsage()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val span = days.coerceIn(1, 31)
        val totals = (span - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(dayEnd, now)
            val millis = if (end > start) {
                foregroundBetween(manager, context.packageName, start, end)
            } else {
                0L
            }
            DayTotal(
                dayStartMillis = start,
                letter = ScreenTimeLogic.dayLetter(date),
                name = ScreenTimeLogic.dayName(date),
                foregroundMillis = millis,
                isToday = offset == 0,
            )
        }
        return ScreenTimeLogic.week(totals)
    }

    /** Usage for any window — the today call is this with today's bounds. */
    fun read(
        context: Context,
        startMillis: Long,
        endMillis: Long,
        notifications: Map<String, Int> = emptyMap(),
    ): DayScreenTime {
        val manager = usageManager(context) ?: return DayScreenTime()

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

        // Launches, screen-on, unlocks and the foreground stretches, in one pass.
        val scan = scanEvents(manager, startMillis, endMillis)

        // Our own package is the app the user is looking at; counting it would
        // make Screen time report the time spent reading Screen time.
        val segments = scan.segments.filter {
            it.packageName.isNotBlank() && it.packageName != context.packageName
        }
        val longest = ScreenTimeLogic.longestSession(segments)?.let { segment ->
            AppSession(
                packageName = segment.packageName,
                label = labelFor(context, segment.packageName),
                startMillis = segment.startMillis,
                durationMillis = segment.durationMillis,
            )
        }

        val packages = (totals.keys + scan.launches.keys + notifications.keys)
            .filter { it.isNotBlank() && it != context.packageName }
        val apps = packages.map { pkg ->
            AppUsage(
                packageName = pkg,
                label = labelFor(context, pkg),
                foregroundMillis = totals[pkg] ?: 0L,
                launches = scan.launches[pkg] ?: 0,
                notifications = notifications[pkg] ?: 0,
            )
        }

        return DayScreenTime(
            totalForegroundMillis = totals.filterKeys { it != context.packageName }.values.sum(),
            screenOnCount = scan.screenOnCount,
            unlockCount = scan.unlockCount,
            apps = ScreenTimeLogic.sortByUsage(apps),
            hourlyMillis = ScreenTimeLogic.hourlyBuckets(segments, startMillis),
            firstUnlockMillis = scan.firstUnlockMillis,
            lastUnlockMillis = scan.lastUnlockMillis,
            longestSession = longest,
            sessionsOverFiveMinutes = ScreenTimeLogic.sessionsLongerThan(segments),
        )
    }

    /** The package currently in the foreground, used by the limit watcher. */
    fun foregroundPackage(context: Context, lookBackMillis: Long = 15_000L): String? {
        if (!hasAccess(context)) return null
        val manager = usageManager(context) ?: return null
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

    private fun usageManager(context: Context): UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * The foreground time one package got in a window, from the per-app totals.
     *
     * Split out of [read] so the seven-day series can ask the same question of
     * six past windows without going through the whole day model each time.
     */
    private fun foregroundBetween(
        manager: UsageStatsManager,
        ownPackage: String,
        startMillis: Long,
        endMillis: Long,
    ): Long = runCatching {
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMillis, endMillis)
            .orEmpty()
            .filter { it.packageName != ownPackage && it.totalTimeInForeground > 0 }
            .sumOf { it.totalTimeInForeground }
    }.onFailure {
        DiagnosticsLogger.log("ScreenTime", "queryUsageStats failed: ${it.message}")
    }.getOrDefault(0L)

    /** What one pass over the event stream yields. */
    private class EventScan(
        val segments: List<UsageSegment>,
        val launches: Map<String, Int>,
        val screenOnCount: Int,
        val unlockCount: Int,
        val firstUnlockMillis: Long?,
        val lastUnlockMillis: Long?,
    )

    /**
     * Turns the event stream into foreground stretches, launch counts and
     * unlock times in a single pass.
     *
     * A resume opens a stretch for that package. It closes at the next resume
     * for any package, when the screen stops being interactive, or when the
     * keyguard comes up — the three ways a phone stops showing an app. Whatever
     * is still open at the end of the window closes at the window end, which
     * for today's read is "now", so the app on screen is counted as used up to
     * this moment rather than dropped. Reading it once matters: the day card
     * needs all four answers and queryEvents is the expensive call here.
     */
    private fun scanEvents(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long,
    ): EventScan {
        val segments = mutableListOf<UsageSegment>()
        val launches = mutableMapOf<String, Int>()
        var screenOn = 0
        var unlocks = 0
        var firstUnlock: Long? = null
        var lastUnlock: Long? = null
        var openPackage: String? = null
        var openStart = 0L

        runCatching {
            val events = manager.queryEvents(startMillis, endMillis)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val at = event.timeStamp
                val type = event.eventType
                val resumed = type == resumedEventType()
                val stopped = type == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    type == UsageEvents.Event.KEYGUARD_SHOWN
                val running = openPackage
                if (running != null && (resumed || stopped)) {
                    if (at > openStart) segments += UsageSegment(openStart, at, running)
                    openPackage = null
                }
                when {
                    resumed -> {
                        val pkg = event.packageName
                        if (!pkg.isNullOrBlank()) {
                            launches[pkg] = (launches[pkg] ?: 0) + 1
                            openPackage = pkg
                            openStart = at
                        }
                    }
                    type == UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn++
                    type == UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        unlocks++
                        if (firstUnlock == null) firstUnlock = at
                        lastUnlock = at
                    }
                }
            }
            openPackage?.let { pkg ->
                if (endMillis > openStart) segments += UsageSegment(openStart, endMillis, pkg)
            }
        }.onFailure {
            DiagnosticsLogger.log("ScreenTime", "queryEvents failed: ${it.message}")
        }

        return EventScan(
            segments = segments,
            launches = launches,
            screenOnCount = screenOn,
            unlockCount = unlocks,
            firstUnlockMillis = firstUnlock,
            lastUnlockMillis = lastUnlock,
        )
    }
}

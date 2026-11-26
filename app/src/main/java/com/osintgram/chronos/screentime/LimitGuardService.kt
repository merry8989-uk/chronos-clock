package com.osintgram.chronos.screentime

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings as SystemSettings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * Stops a limited app from being used.
 *
 * [LimitService] polls every four seconds and draws an overlay, which the user
 * can dismiss and carry on behind. This service is the part that actually
 * closes the door: Android tells it the instant a window comes to the front, so
 * a limited app is sent home and stopped before it can be used.
 *
 * How a block works, in order:
 * 1. A `TYPE_WINDOW_STATE_CHANGED` event names the package that just came to
 *    the front. Chronos itself, packages with no limit and packages still under
 *    their limit are ignored silently — this event fires constantly, so logging
 *    every one of them would bury the log.
 * 2. The package's limit comes from [ScreenTimeStore] and today's use from
 *    [UsageSource]. Both are compared with [ScreenTimeLogic.limitReached], the
 *    same rule the overlay uses, so the two cannot disagree about "used up".
 * 3. The user is sent to the home screen with a global action, so the app
 *    cannot stay in front, and `killBackgroundProcesses` stops its background
 *    work as well.
 * 4. One log line records the package, the minutes used and the minutes
 *    allowed, so a report of "it let me through" can be answered from the log.
 *
 * Every system call is wrapped: an accessibility service that throws is
 * disabled by Android, which would remove the protection without telling
 * anyone. A failure is logged and the service stays alive.
 *
 * Two honest limits. `killBackgroundProcesses` asks the system to kill; it is
 * not guaranteed to take effect immediately, and the app can be reopened — the
 * window event then blocks it again. And if the user switches the service off
 * in system settings, nothing here runs at all; the Special access screen
 * reports that state so it is never a silent gap.
 */
class LimitGuardService : AccessibilityService() {

    /**
     * When each package was last blocked, so a burst of window changes cannot
     * spin. A launcher that fires ten events in a second would otherwise send
     * ten home actions for one app.
     */
    private val lastBlockedAt = HashMap<String, Long>()

    /**
     * When each package's usage was last read. Reading a day of usage events is
     * the expensive part of a check, and a single app can raise several window
     * events while it settles, so the read is rate-limited per package as well.
     */
    private val lastCheckedAt = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsLogger.log(
            TAG,
            "Connected: watching ${ScreenTimeStore.allLimits().size} limited app(s), " +
                "strictLimits=${Settings.strictLimits.value}, " +
                "usageAccess=${UsageSource.hasAccess(this)}",
        )
        if (!Settings.strictLimits.value) {
            DiagnosticsLogger.log(
                TAG,
                "Strict limits are switched off in Settings, so this service will not block anything",
            )
        }
        if (!UsageSource.hasAccess(this)) {
            DiagnosticsLogger.log(
                TAG,
                "Usage access is missing, so today's use of an app cannot be read and no app " +
                    "will be blocked",
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val change = event ?: return
        if (change.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // The event stream is not a place to throw: a failure here disables the service.
        runCatching { handleWindowChange(change) }.onFailure {
            DiagnosticsLogger.log(
                TAG,
                "Could not check the window change (${it.javaClass.simpleName}: ${it.message}); " +
                    "this one event was skipped",
            )
        }
    }

    override fun onInterrupt() {
        // Nothing to interrupt: every check is a single synchronous pass.
    }

    override fun onDestroy() {
        DiagnosticsLogger.log(
            TAG,
            "Stopped: limits are no longer enforced by this service until it is switched " +
                "back on in Accessibility settings",
        )
        super.onDestroy()
    }

    // ---- One window change -------------------------------------------------

    private fun handleWindowChange(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty()) return

        // Chronos is never blocked: its own screens are how a limit gets lifted.
        if (pkg == packageName) return

        // The off switch. Honest design: when the user turns strong limits off,
        // this service does nothing at all, rather than half-doing it.
        if (!Settings.strictLimits.value) return

        // The limit that actually applies: the stored one plus today's bonus,
        // or nothing at all when the user turned this app off for today.
        val limitMinutes = LimitEnforcement.effectiveLimitMinutes(this, pkg) ?: return
        if (limitMinutes <= 0) return
        if (LimitEnforcement.pausedForToday(this, pkg)) return

        val now = System.currentTimeMillis()
        val lastCheck = lastCheckedAt[pkg] ?: 0L
        if (now - lastCheck < MIN_INTERVAL_MILLIS) return
        trim(lastCheckedAt, now)
        lastCheckedAt[pkg] = now

        val usedMillis = runCatching { UsageSource.foregroundMillisToday(this, pkg) }
            .onFailure {
                DiagnosticsLogger.log(
                    TAG,
                    "Could not read today's use of $pkg (${it.javaClass.simpleName}: " +
                        "${it.message}); treating it as 0 minutes, so this app is not blocked",
                )
            }
            .getOrDefault(0L)

        if (!ScreenTimeLogic.limitReached(usedMillis, limitMinutes)) return

        val lastBlock = lastBlockedAt[pkg] ?: 0L
        if (now - lastBlock < MIN_INTERVAL_MILLIS) return
        trim(lastBlockedAt, now)
        lastBlockedAt[pkg] = now

        block(pkg, usedMillis, limitMinutes)
    }

    /** Sends the user home, stops the app's background work and logs the block. */
    private fun block(pkg: String, usedMillis: Long, limitMinutes: Int) {
        val wentHome = runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
            .onFailure {
                DiagnosticsLogger.log(
                    TAG,
                    "Could not send the user home away from $pkg (${it.javaClass.simpleName}: " +
                        "${it.message}); the app may still be in front",
                )
            }
            .getOrDefault(false)
        if (!wentHome) {
            DiagnosticsLogger.log(
                TAG,
                "The system refused the home action for $pkg, so it may still be in front",
            )
        }

        runCatching {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: error("no ActivityManager on this device")
            manager.killBackgroundProcesses(pkg)
        }.onFailure {
            DiagnosticsLogger.log(
                TAG,
                "Could not stop the background processes of $pkg (${it.javaClass.simpleName}: " +
                    "${it.message}); its background work may continue",
            )
        }

        DiagnosticsLogger.log(
            TAG,
            "Blocked $pkg: ${usedMillis / 60_000L} of $limitMinutes min used today — sent home " +
                "and asked the system to stop its background processes",
        )
    }

    /**
     * Keeps the two maps from growing with every app ever seen. Anything older
     * than the rate-limit window is dead weight, so it is dropped when the map
     * passes [MAP_LIMIT] entries.
     */
    private fun trim(map: HashMap<String, Long>, now: Long) {
        if (map.size < MAP_LIMIT) return
        map.entries.removeAll { now - it.value > MIN_INTERVAL_MILLIS }
    }

    companion object {

        private const val TAG = "LimitGuard"

        /** One block, and one usage read, per package per second at most. */
        private const val MIN_INTERVAL_MILLIS = 1_000L

        /** Above this many remembered packages, stale entries are cleared out. */
        private const val MAP_LIMIT = 64

        /** The component the user has to switch on, for the message on screen. */
        fun component(context: Context): ComponentName =
            ComponentName(context.packageName, LimitGuardService::class.java.name)

        /**
         * True when the user has switched Chronos on under Accessibility.
         *
         * Asks the accessibility manager for the services that are actually
         * running and bound, then falls back to the secure setting that lists
         * the enabled ones, because some builds report an empty list until the
         * first event arrives.
         */
        fun isEnabled(context: Context): Boolean = runCatching {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? AccessibilityManager
            val running = manager?.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            ).orEmpty().any { info ->
                val service = info.resolveInfo?.serviceInfo ?: return@any false
                service.packageName == context.packageName &&
                    service.name == LimitGuardService::class.java.name
            }
            if (running) return@runCatching true
            listedInSecureSettings(context)
        }.onFailure {
            DiagnosticsLogger.log(
                TAG,
                "Could not read the accessibility state (${it.javaClass.simpleName}: " +
                    "${it.message}); reporting the service as off",
            )
        }.getOrDefault(false)

        /**
         * Reads the colon-separated `package/class` list Android keeps in
         * `Settings.Secure`, the same list the Accessibility screen ticks.
         */
        private fun listedInSecureSettings(context: Context): Boolean {
            val raw = SystemSettings.Secure.getString(
                context.contentResolver,
                SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            if (raw.isEmpty()) return false
            val wanted = component(context)
            return raw.split(':').any { entry ->
                val flat = entry.trim()
                if (flat.isEmpty()) return@any false
                val parsed = ComponentName.unflattenFromString(flat) ?: return@any false
                parsed.packageName == wanted.packageName && parsed.className == wanted.className
            }
        }
    }
}

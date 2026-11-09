package com.osintgram.chronos.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import java.io.File

/** One reading of the phone's state, written into the log and the export. */
data class DeviceSnapshot(
    val androidVersion: String,
    val sdk: Int,
    val model: String,
    val abis: String,
    val batteryPercent: Int,
    val charging: Boolean,
    val memoryAvailableMb: Long,
    val memoryTotalMb: Long,
    val lowMemory: Boolean,
    val network: String,
    val storageFreeMb: Long,
    val storageTotalMb: Long,
    val appVersion: String,
)

/**
 * Battery, memory, network, storage and the device identity.
 *
 * Logged on every launch and whenever something important happens, because the
 * first question about any bug report is "what phone, which Android, was it on
 * Wi-Fi, was the battery saver on" — and none of that was in the log before.
 */
object DeviceMetrics {

    fun snapshot(context: Context, appVersion: String): DeviceSnapshot {
        val battery = battery(context)
        val memory = memory(context)
        return DeviceSnapshot(
            androidVersion = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT,
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            abis = Build.SUPPORTED_ABIS.joinToString(","),
            batteryPercent = battery.first,
            charging = battery.second,
            memoryAvailableMb = memory.first,
            memoryTotalMb = memory.second,
            lowMemory = memory.third,
            network = network(context),
            storageFreeMb = storage(context).first,
            storageTotalMb = storage(context).second,
            appVersion = appVersion,
        )
    }

    /** A single line, the shape every log entry takes. */
    fun describe(snapshot: DeviceSnapshot): String = buildString {
        append("app=${snapshot.appVersion}")
        append(" android=${snapshot.androidVersion}(")
        append(snapshot.sdk)
        append(")")
        append(" model=${snapshot.model}")
        append(" abi=${snapshot.abis}")
        append(" battery=${snapshot.batteryPercent}%")
        append(" charging=${snapshot.charging}")
        append(" mem=${snapshot.memoryAvailableMb}/${snapshot.memoryTotalMb}MB")
        append(" lowMem=${snapshot.lowMemory}")
        append(" net=${snapshot.network}")
        append(" storage=${snapshot.storageFreeMb}/${snapshot.storageTotalMb}MB free")
    }

    fun log(context: Context, appVersion: String, reason: String) {
        runCatching {
            val snapshot = snapshot(context, appVersion)
            DiagnosticsLogger.log("Device", "$reason ${describe(snapshot)}")
        }.onFailure {
            DiagnosticsLogger.log("Device", "Snapshot failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    // ---- readers -----------------------------------------------------------

    private fun battery(context: Context): Pair<Int, Boolean> = runCatching {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return 0 to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        percent to charging
    }.getOrDefault(-1 to false)

    private fun memory(context: Context): Triple<Long, Long, Boolean> = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        Triple(info.availMem / (1024 * 1024), info.totalMem / (1024 * 1024), info.lowMemory)
    }.getOrDefault(Triple(-1L, -1L, false))

    private fun network(context: Context): String = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return "none"
        val caps = manager.getNetworkCapabilities(network) ?: return "none"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }.getOrDefault("unknown")

    private fun storage(context: Context): Pair<Long, Long> = runCatching {
        val folder: File = context.filesDir
        (folder.usableSpace / (1024 * 1024)) to (folder.totalSpace / (1024 * 1024))
    }.getOrDefault(0L to 0L)
}

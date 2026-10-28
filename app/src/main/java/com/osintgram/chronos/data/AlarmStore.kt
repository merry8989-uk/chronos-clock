package com.osintgram.chronos.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Alarms, persisted as a JSON array in SharedPreferences and exposed as an
 * observable list so every screen redraws the moment an alarm changes.
 */
object AlarmStore {

    private const val PREFS = "chronos_alarms"
    private const val KEY = "alarms"

    private var prefs: android.content.SharedPreferences? = null
    private var appContext: android.content.Context? = null
    private val alarms: SnapshotStateList<Alarm> = mutableStateListOf()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        alarms.clear()
        alarms.addAll(readAll())
        DiagnosticsLogger.log("AlarmStore", "Loaded ${alarms.size} alarm(s)")
    }

    fun all(): List<Alarm> = alarms.toList()

    fun byId(id: Long): Alarm? = alarms.firstOrNull { it.id == id }

    fun upsert(alarm: Alarm): Alarm {
        val existing = alarms.indexOfFirst { it.id == alarm.id }
        if (existing >= 0) {
            alarms[existing] = alarm
        } else {
            alarms.add(alarm)
        }
        sort()
        persist()
        return alarm
    }

    fun delete(id: Long) {
        alarms.removeAll { it.id == id }
        persist()
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val index = alarms.indexOfFirst { it.id == id }
        if (index >= 0) {
            alarms[index] = alarms[index].copy(enabled = enabled)
            persist()
        }
    }

    fun nextId(): Long = (alarms.maxOfOrNull { it.id } ?: 0L) + 1L

    fun replaceAll(list: List<Alarm>) {
        alarms.clear()
        alarms.addAll(list)
        sort()
        persist()
    }

    private fun sort() {
        val sorted = alarms.sortedWith(compareBy({ it.hour }, { it.minute }, { it.createdAt }))
        alarms.clear()
        alarms.addAll(sorted)
    }

    private fun persist() {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString(KEY, arr.toString())?.apply()
        // Keep the home-screen widgets in step with the list.
        appContext?.let { com.osintgram.chronos.widget.WidgetUpdater.refreshAll(it) }
    }

    private fun readAll(): List<Alarm> {
        val raw = prefs?.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { Alarm.fromJson(it) }
            }
        }.getOrElse {
            DiagnosticsLogger.log("AlarmStore", "Could not parse stored alarms: ${it.message}")
            emptyList()
        }
    }

    fun asJson(): JSONArray = JSONArray().also { arr -> alarms.forEach { arr.put(it.toJson()) } }

    fun importJson(arr: JSONArray) {
        val imported = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Alarm.fromJson(it) }
        }
        if (imported.isNotEmpty()) replaceAll(imported)
    }

    fun toJsonString(): String = JSONObject().put("alarms", asJson()).toString()
}

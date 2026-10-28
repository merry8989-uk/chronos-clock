package com.osintgram.chronos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Cities shown on the World Clock tab, persisted as a list of zone IDs. */
object WorldClockStore {

    private const val PREFS = "chronos_world"
    private const val KEY = "zones"

    private var prefs: SharedPreferences? = null
    private val zones: SnapshotStateList<String> = mutableStateListOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        zones.clear()
        zones.addAll(
            prefs?.getString(KEY, null)
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: listOf(java.util.TimeZone.getDefault().id)
        )
    }

    fun all(): List<String> = zones.toList()

    fun add(zoneId: String) {
        if (!zones.contains(zoneId)) {
            zones.add(zoneId)
            persist()
        }
    }

    fun remove(zoneId: String) {
        zones.remove(zoneId)
        persist()
    }

    fun move(from: Int, to: Int) {
        if (from in zones.indices && to in zones.indices) {
            val item = zones.removeAt(from)
            zones.add(to, item)
            persist()
        }
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY, zones.joinToString("|"))?.apply()
    }
}

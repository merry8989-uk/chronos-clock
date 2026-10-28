package com.osintgram.chronos.stopwatch

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stopwatch with laps. Elapsed time comes from the monotonic clock, and the
 * start instant is persisted, so the count keeps running correctly if the app
 * is backgrounded or its process is recreated.
 */
object StopwatchEngine {

    data class Lap(val index: Int, val totalMs: Long, val splitMs: Long)

    data class State(
        val running: Boolean = false,
        val elapsedMs: Long = 0L,
    )

    private const val PREFS = "chronos_stopwatch"
    private const val KEY_RUNNING = "running"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_ACCUMULATED = "accumulated"
    private const val KEY_LAPS = "laps"

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val laps: SnapshotStateList<Lap> = mutableStateListOf()
    private var prefs: SharedPreferences? = null
    private var startedAtElapsed: Long = 0L
    private var accumulatedMs: Long = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs ?: return
        accumulatedMs = p.getLong(KEY_ACCUMULATED, 0L)
        val wasRunning = p.getBoolean(KEY_RUNNING, false)
        if (wasRunning) {
            startedAtElapsed = p.getLong(KEY_STARTED_AT, SystemClock.elapsedRealtime())
            _state.value = State(running = true, elapsedMs = liveElapsed())
        } else {
            _state.value = State(running = false, elapsedMs = accumulatedMs)
        }
        laps.clear()
        laps.addAll(
            p.getString(KEY_LAPS, null)
                ?.split(";")
                ?.mapNotNull { entry ->
                    val parts = entry.split(",")
                    if (parts.size == 3) {
                        Lap(
                            index = parts[0].toIntOrNull() ?: return@mapNotNull null,
                            totalMs = parts[1].toLongOrNull() ?: return@mapNotNull null,
                            splitMs = parts[2].toLongOrNull() ?: return@mapNotNull null,
                        )
                    } else null
                }
                ?: emptyList()
        )
    }

    fun allLaps(): List<Lap> = laps.toList()

    fun start() {
        if (_state.value.running) return
        startedAtElapsed = SystemClock.elapsedRealtime()
        _state.value = State(running = true, elapsedMs = accumulatedMs)
        persist()
    }

    fun pause() {
        if (!_state.value.running) return
        accumulatedMs = liveElapsed()
        _state.value = State(running = false, elapsedMs = accumulatedMs)
        persist()
    }

    fun reset() {
        accumulatedMs = 0L
        startedAtElapsed = 0L
        laps.clear()
        _state.value = State(running = false, elapsedMs = 0L)
        persist()
    }

    fun lap() {
        val elapsed = liveElapsed()
        val previousTotal = laps.lastOrNull()?.totalMs ?: 0L
        laps.add(Lap(index = laps.size + 1, totalMs = elapsed, splitMs = elapsed - previousTotal))
        persist()
    }

    fun refresh() {
        _state.value = _state.value.copy(elapsedMs = liveElapsed())
    }

    private fun liveElapsed(): Long =
        if (_state.value.running) {
            accumulatedMs + (SystemClock.elapsedRealtime() - startedAtElapsed)
        } else {
            accumulatedMs
        }

    private fun persist() {
        prefs?.edit()
            ?.putBoolean(KEY_RUNNING, _state.value.running)
            ?.putLong(KEY_STARTED_AT, startedAtElapsed)
            ?.putLong(KEY_ACCUMULATED, accumulatedMs)
            ?.putString(KEY_LAPS, laps.joinToString(";") { "${it.index},${it.totalMs},${it.splitMs}" })
            ?.apply()
    }

    /** 00:00.00 style, plus hours once the run passes an hour. */
    fun format(ms: Long): String {
        val totalCs = ms / 10
        val cs = totalCs % 100
        val totalSeconds = totalCs / 100
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = totalSeconds / 3600
        return if (h > 0) String.format("%d:%02d:%02d.%02d", h, m, s, cs)
        else String.format("%02d:%02d.%02d", m, s, cs)
    }
}

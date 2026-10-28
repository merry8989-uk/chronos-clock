package com.osintgram.chronos.timer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single countdown timer. Time is derived from the monotonic clock, so it
 * stays accurate even if the device sleeps or the UI is recreated.
 */
object TimerEngine {

    data class State(
        val durationMs: Long = 15 * 60_000L,
        val remainingMs: Long = 15 * 60_000L,
        val running: Boolean = false,
        val finished: Boolean = false,
        val label: String = "Timer",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var endElapsed: Long = 0L
    private var prefs: SharedPreferences? = null

    /**
     * Restores the countdown after the process is recreated, so the home-screen
     * widget and the app agree on what is running.
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("chronos_timer", Context.MODE_PRIVATE)
        val p = prefs ?: return
        val duration = p.getLong("duration", 15 * 60_000L)
        val label = p.getString("label", "Timer") ?: "Timer"
        val running = p.getBoolean("running", false)
        val endWall = p.getLong("end_wall", 0L)
        val now = System.currentTimeMillis()

        if (running && endWall > now) {
            val remaining = endWall - now
            endElapsed = SystemClock.elapsedRealtime() + remaining
            _state.value = State(
                durationMs = duration,
                remainingMs = remaining,
                running = true,
                finished = false,
                label = label,
            )
        } else if (running) {
            _state.value = State(
                durationMs = duration,
                remainingMs = 0L,
                running = false,
                finished = true,
                label = label,
            )
        } else {
            _state.value = State(
                durationMs = duration,
                remainingMs = p.getLong("remaining", duration),
                running = false,
                finished = false,
                label = label,
            )
        }
    }

    private fun persist() {
        val state = _state.value
        prefs?.edit()
            ?.putLong("duration", state.durationMs)
            ?.putLong("remaining", state.remainingMs)
            ?.putBoolean("running", state.running)
            ?.putString("label", state.label)
            ?.putLong(
                "end_wall",
                if (state.running) System.currentTimeMillis() + state.remainingMs else 0L,
            )
            ?.apply()
    }

    fun setDuration(ms: Long) {
        if (_state.value.running) return
        val clamped = ms.coerceAtLeast(0L)
        _state.value = _state.value.copy(
            durationMs = clamped,
            remainingMs = clamped,
            finished = false,
        )
        persist()
    }

    /**
     * Adds (or removes, with a negative value) time. While idle this also moves
     * the configured duration, so Reset returns to the adjusted value.
     */
    fun extend(ms: Long) {
        val current = _state.value
        if (current.running) {
            val remaining = (endElapsed - SystemClock.elapsedRealtime() + ms).coerceAtLeast(1_000L)
            endElapsed = SystemClock.elapsedRealtime() + remaining
            _state.value = current.copy(
                durationMs = (current.durationMs + ms).coerceAtLeast(1_000L),
                remainingMs = remaining,
                finished = false,
            )
        } else {
            val next = (current.remainingMs + ms).coerceIn(0L, 99L * 3_600_000L)
            _state.value = current.copy(durationMs = next, remainingMs = next, finished = false)
        }
        persist()
    }

    fun setLabel(label: String) {
        _state.value = _state.value.copy(label = label.ifBlank { "Timer" })
        persist()
    }

    fun start(context: Context) {
        val current = _state.value
        if (current.running || current.remainingMs <= 0L) return
        endElapsed = SystemClock.elapsedRealtime() + current.remainingMs
        _state.value = current.copy(running = true, finished = false)
        persist()
        ContextCompat.startForegroundService(
            context,
            Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_START },
        )
    }

    fun pause(context: Context) {
        val current = _state.value
        if (!current.running) return
        val remaining = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _state.value = current.copy(running = false, remainingMs = remaining)
        persist()
        context.stopService(Intent(context, TimerService::class.java))
    }

    fun reset(context: Context) {
        context.stopService(Intent(context, TimerService::class.java))
        _state.value = _state.value.copy(
            running = false,
            finished = false,
            remainingMs = _state.value.durationMs,
        )
        persist()
    }

    /** Called by [TimerService] on every tick. */
    fun tick() {
        val current = _state.value
        if (!current.running) return
        val remaining = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val updated = current.copy(
            remainingMs = remaining,
            running = remaining > 0L,
            finished = remaining == 0L,
        )
        _state.value = updated
        if (!updated.running) persist()
    }

    fun remainingMillis(): Long =
        if (_state.value.running) (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        else _state.value.remainingMs

    fun format(ms: Long, showHours: Boolean = true): String {
        val totalSeconds = (ms + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (showHours || h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}

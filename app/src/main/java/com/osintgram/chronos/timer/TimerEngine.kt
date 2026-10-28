package com.osintgram.chronos.timer

import android.content.Context
import android.content.Intent
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

    fun setDuration(ms: Long) {
        if (_state.value.running) return
        val clamped = ms.coerceAtLeast(0L)
        _state.value = _state.value.copy(
            durationMs = clamped,
            remainingMs = clamped,
            finished = false,
        )
    }

    /** Adds time to a running countdown (used by the "+1 minute" button). */
    fun extend(ms: Long) {
        val current = _state.value
        if (current.running) {
            endElapsed += ms
            _state.value = current.copy(
                durationMs = current.durationMs + ms,
                remainingMs = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L),
            )
        } else {
            val next = current.remainingMs + ms
            _state.value = current.copy(durationMs = next, remainingMs = next, finished = false)
        }
    }

    fun setLabel(label: String) {
        _state.value = _state.value.copy(label = label.ifBlank { "Timer" })
    }

    fun start(context: Context) {
        val current = _state.value
        if (current.running || current.remainingMs <= 0L) return
        endElapsed = SystemClock.elapsedRealtime() + current.remainingMs
        _state.value = current.copy(running = true, finished = false)
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
        context.stopService(Intent(context, TimerService::class.java))
    }

    fun reset(context: Context) {
        context.stopService(Intent(context, TimerService::class.java))
        _state.value = _state.value.copy(
            running = false,
            finished = false,
            remainingMs = _state.value.durationMs,
        )
    }

    /** Called by [TimerService] on every tick. */
    fun tick() {
        val current = _state.value
        if (!current.running) return
        val remaining = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _state.value = current.copy(
            remainingMs = remaining,
            running = remaining > 0L,
            finished = remaining == 0L,
        )
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

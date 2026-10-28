package com.osintgram.chronos.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One detected snore inside a sleep session. */
data class SnoreEvent(
    val atMillis: Long,
    val peakLevel: Int,
    val clipPath: String?,
)

/**
 * One recorded night. Everything here is measured — audio level from the
 * microphone and movement from the accelerometer. Sleep *stages* (REM, deep)
 * cannot be measured by a phone, so this app does not invent them.
 */
data class SleepSession(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val snores: List<SnoreEvent> = emptyList(),
    val noise: List<Int> = emptyList(),
    val movement: List<Int> = emptyList(),
    val intervalMs: Long = SAMPLE_INTERVAL_MS,
    val screenOffEvents: List<Long> = emptyList(),
    val screenOnEvents: List<Long> = emptyList(),
    val unlocks: Int = 0,
    val screenOffMillis: Long = 0L,
    val screenOnMillis: Long = 0L,
) {
    val durationMs: Long get() = (endMillis - startMillis).coerceAtLeast(0)

    /** Total snore time, counted as one interval per detected event. */
    val snoreMs: Long get() = snores.size * 2_000L

    /** How many times the screen came on during the night. */
    val wakeUps: Int get() = screenOnEvents.size

    /** First moment the screen went off — the best available sleep-onset marker. */
    fun sleepOnsetMillis(): Long? = screenOffEvents.firstOrNull()

    fun date(): LocalDate =
        Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toJson(): JSONObject {
        val snoreArray = JSONArray()
        snores.forEach { s ->
            snoreArray.put(
                JSONObject()
                    .put("at", s.atMillis)
                    .put("level", s.peakLevel)
                    .put("clip", s.clipPath ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("id", id)
            .put("start", startMillis)
            .put("end", endMillis)
            .put("intervalMs", intervalMs)
            .put("snores", snoreArray)
            .put("noise", JSONArray(noise))
            .put("movement", JSONArray(movement))
            .put("screenOff", JSONArray(screenOffEvents))
            .put("screenOn", JSONArray(screenOnEvents))
            .put("unlocks", unlocks)
            .put("screenOffMs", screenOffMillis)
            .put("screenOnMs", screenOnMillis)
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 30_000L

        fun fromJson(o: JSONObject): SleepSession {
            val snores = mutableListOf<SnoreEvent>()
            o.optJSONArray("snores")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { s ->
                        snores.add(
                            SnoreEvent(
                                atMillis = s.optLong("at"),
                                peakLevel = s.optInt("level"),
                                clipPath = if (s.isNull("clip")) null
                                else s.optString("clip").takeIf { it.isNotEmpty() },
                            ),
                        )
                    }
                }
            }
            fun ints(key: String): List<Int> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.optInt(it) }
            }
            fun longs(key: String): List<Long> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.optLong(it) }
            }
            return SleepSession(
                id = o.optLong("id"),
                startMillis = o.optLong("start"),
                endMillis = o.optLong("end"),
                snores = snores,
                noise = ints("noise"),
                movement = ints("movement"),
                intervalMs = o.optLong("intervalMs", SAMPLE_INTERVAL_MS),
                screenOffEvents = longs("screenOff"),
                screenOnEvents = longs("screenOn"),
                unlocks = o.optInt("unlocks", 0),
                screenOffMillis = o.optLong("screenOffMs", 0L),
                screenOnMillis = o.optLong("screenOnMs", 0L),
            )
        }
    }
}

/** Persisted sleep sessions, newest first. */
object SleepStore {

    private const val PREFS = "chronos_sleep"
    private const val KEY = "sessions"
    private const val MAX_SESSIONS = 60

    private var prefs: SharedPreferences? = null
    private val sessions = mutableListOf<SleepSession>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sessions.clear()
        sessions.addAll(read())
    }

    fun all(): List<SleepSession> = sessions.toList()

    fun byId(id: Long): SleepSession? = sessions.firstOrNull { it.id == id }

    fun forDate(date: LocalDate): SleepSession? = sessions.firstOrNull { it.date() == date }

    fun latest(): SleepSession? = sessions.firstOrNull()

    fun save(session: SleepSession) {
        sessions.removeAll { it.id == session.id }
        sessions.add(0, session)
        while (sessions.size > MAX_SESSIONS) {
            sessions.removeAt(sessions.lastIndex)
        }
        persist()
    }

    fun delete(id: Long) {
        sessions.removeAll { it.id == id }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        sessions.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString(KEY, arr.toString())?.apply()
    }

    private fun read(): List<SleepSession> {
        val raw = prefs?.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { SleepSession.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }
}

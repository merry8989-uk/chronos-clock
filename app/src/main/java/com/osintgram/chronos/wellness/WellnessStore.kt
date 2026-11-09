package com.osintgram.chronos.wellness

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.osintgram.chronos.diagnostics.DiagnosticsLogger
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Workouts, meditation sessions and daily health numbers, persisted as JSON in
 * SharedPreferences and exposed as observable lists — the same pattern
 * [com.osintgram.chronos.data.AlarmStore] uses, so every screen redraws the
 * moment a record changes and nothing leaves the device.
 */
object WellnessStore {

    private const val PREFS = "chronos_wellness"
    private const val KEY_WORKOUTS = "workouts"
    private const val KEY_MEDITATIONS = "meditations"
    private const val KEY_DAYS = "days"
    private const val KEY_STEP_DATE = "step_baseline_date"
    private const val KEY_STEP_VALUE = "step_baseline_value"

    private var prefs: SharedPreferences? = null

    private val workouts: SnapshotStateList<WorkoutSession> = mutableStateListOf()
    private val meditations: SnapshotStateList<MeditationSession> = mutableStateListOf()
    private val days: SnapshotStateList<DayLog> = mutableStateListOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        workouts.clear()
        meditations.clear()
        days.clear()
        workouts.addAll(WellnessCodec.workoutsFromJson(readArray(KEY_WORKOUTS)))
        meditations.addAll(WellnessCodec.meditationsFromJson(readArray(KEY_MEDITATIONS)))
        days.addAll(WellnessCodec.daysFromJson(readArray(KEY_DAYS)))
        DiagnosticsLogger.log(
            "WellnessStore",
            "Loaded ${workouts.size} workout(s), ${meditations.size} session(s), ${days.size} day(s)",
        )
    }

    // ---- Reads -------------------------------------------------------------

    fun allWorkouts(): List<WorkoutSession> = workouts.toList()

    fun allMeditations(): List<MeditationSession> = meditations.toList()

    fun allDays(): List<DayLog> = days.toList()

    fun workout(id: Long): WorkoutSession? = workouts.firstOrNull { it.id == id }

    /** The session that is running right now, if there is one. */
    fun activeWorkout(): WorkoutSession? = workouts.firstOrNull { !it.isFinished }

    fun day(date: String = WellnessLogic.todayKey()): DayLog =
        days.firstOrNull { it.date == date } ?: DayLog(date = date)

    /** Days oldest-first inside the window, missing days filled with zeros. */
    fun recentDays(count: Int = 7, today: LocalDate = LocalDate.now()): List<DayLog> =
        WellnessLogic.recentDays(today, count).map { day(WellnessLogic.todayKey(it)) }

    fun weekTotals(count: Int = 7, today: LocalDate = LocalDate.now()): WeekTotals {
        val window = WellnessLogic.recentDays(today, count)
        val startDate = window.first()
        val start = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli() - 1
        return WellnessLogic.weekTotals(
            days = recentDays(count, today),
            workouts = workouts.toList(),
            meditations = meditations.toList(),
            windowStart = start,
            windowEnd = end,
        )
    }

    /** Consecutive days with something logged, including finished workouts. */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        val finishedDates = workouts
            .filter { it.isFinished }
            .mapNotNull { WellnessLogic.parseDate(dateKeyOf(it.startedAt)) }
            .map { WellnessLogic.todayKey(it) }
            .toSet()
        val active = days
            .filter { WellnessLogic.isActiveDay(it, finishedDates) }
            .map { it.date }
            .toSet() + finishedDates
        return WellnessLogic.streak(active, today)
    }

    fun workoutDates(): Set<String> = workouts
        .filter { it.isFinished }
        .mapNotNull { WellnessLogic.parseDate(dateKeyOf(it.startedAt)) }
        .map { WellnessLogic.todayKey(it) }
        .toSet()

    // ---- Workouts ----------------------------------------------------------

    fun startWorkout(planId: String, title: String, startedAt: Long = System.currentTimeMillis()): WorkoutSession {
        val session = WorkoutSession(
            id = nextId(),
            planId = planId,
            title = title,
            startedAt = startedAt,
            endedAt = null,
        )
        workouts.add(0, session)
        persistWorkouts()
        DiagnosticsLogger.log("WellnessStore", "Workout started: $title")
        return session
    }

    fun addEntry(workoutId: Long, entry: WorkoutEntry) {
        update(workoutId) { it.copy(entries = it.entries + entry) }
    }

    fun removeEntry(workoutId: Long, index: Int) {
        update(workoutId) { session ->
            if (index !in session.entries.indices) session
            else session.copy(entries = session.entries.filterIndexed { i, _ -> i != index })
        }
    }

    fun finishWorkout(workoutId: Long, endedAt: Long = System.currentTimeMillis()) {
        update(workoutId) { it.copy(endedAt = endedAt) }
        DiagnosticsLogger.log("WellnessStore", "Workout finished: $workoutId")
    }

    fun discardWorkout(workoutId: Long) {
        workouts.removeAll { it.id == workoutId }
        persistWorkouts()
    }

    private fun update(workoutId: Long, block: (WorkoutSession) -> WorkoutSession) {
        val index = workouts.indexOfFirst { it.id == workoutId }
        if (index < 0) return
        workouts[index] = block(workouts[index])
        persistWorkouts()
    }

    // ---- Meditation --------------------------------------------------------

    fun logMeditation(minutes: Int, patternId: String, completedAt: Long = System.currentTimeMillis()) {
        if (minutes <= 0) return
        meditations.add(0, MeditationSession(nextId(), minutes, patternId, completedAt))
        persistMeditations()
        DiagnosticsLogger.log("WellnessStore", "Meditation logged: $minutes min ($patternId)")
    }

    fun deleteMeditation(id: Long) {
        meditations.removeAll { it.id == id }
        persistMeditations()
    }

    // ---- Daily health numbers ---------------------------------------------

    /** Writes [block]'s result into today's (or [date]'s) record. */
    fun updateDay(date: String = WellnessLogic.todayKey(), block: (DayLog) -> DayLog) {
        val index = days.indexOfFirst { it.date == date }
        val current = if (index >= 0) days[index] else DayLog(date = date)
        val next = block(current)
        if (index >= 0) days[index] = next else days.add(next)
        persistDays()
    }

    fun setSteps(steps: Int, date: String = WellnessLogic.todayKey()) =
        updateDay(date) { it.copy(steps = steps.coerceAtLeast(0)) }

    fun addWater(ml: Int, date: String = WellnessLogic.todayKey()) =
        updateDay(date) { it.copy(waterMl = (it.waterMl + ml).coerceAtLeast(0)) }

    fun setSleepMinutes(minutes: Int, date: String = WellnessLogic.todayKey()) =
        updateDay(date) { it.copy(sleepMinutes = minutes.coerceAtLeast(0)) }

    /** Stores the latest heart-rate reading. Zero is ignored, never stored. */
    fun setHeartRate(bpm: Int, measuredAt: Long = System.currentTimeMillis(), date: String = WellnessLogic.todayKey()) {
        if (bpm <= 0) return
        updateDay(date) { it.copy(heartRateBpm = bpm, heartRateAt = measuredAt) }
    }

    fun toggleHabit(habitId: String, date: String = WellnessLogic.todayKey()) =
        updateDay(date) { day ->
            val next = if (habitId in day.habits) day.habits - habitId else day.habits + habitId
            day.copy(habits = next)
        }

    // ---- Step baseline -----------------------------------------------------

    /** The stored baseline, or null when today has none yet. */
    fun stepBaseline(today: String = WellnessLogic.todayKey()): Long? {
        val stored = prefs?.getString(KEY_STEP_DATE, null) ?: return null
        if (stored != today) return null
        val value = prefs?.getLong(KEY_STEP_VALUE, -1L) ?: -1L
        return if (value < 0L) null else value
    }

    fun setStepBaseline(cumulative: Long, today: String = WellnessLogic.todayKey()) {
        prefs?.edit()?.putString(KEY_STEP_DATE, today)?.putLong(KEY_STEP_VALUE, cumulative)?.apply()
    }

    // ---- Backup ------------------------------------------------------------

    fun asJson(): JSONObject = WellnessCodec.toJson(
        workouts = workouts.toList(),
        meditations = meditations.toList(),
        days = days.toList(),
    )

    /** Replaces everything with the records in [json]. Returns what was loaded. */
    fun importJson(json: JSONObject?): String? {
        if (json == null) return null
        val restoredWorkouts = WellnessCodec.workoutsFromJson(json.optJSONArray("workouts"))
        val restoredMeditations = WellnessCodec.meditationsFromJson(json.optJSONArray("meditations"))
        val restoredDays = WellnessCodec.daysFromJson(json.optJSONArray("days"))
        workouts.clear()
        workouts.addAll(restoredWorkouts)
        meditations.clear()
        meditations.addAll(restoredMeditations)
        days.clear()
        days.addAll(restoredDays)
        persistWorkouts()
        persistMeditations()
        persistDays()
        return "${restoredWorkouts.size} workout(s), ${restoredMeditations.size} meditation(s), " +
            "${restoredDays.size} day(s)"
    }

    /** Everything the wellness feature holds, removed. Used by factory reset. */
    fun clearAll() {
        workouts.clear()
        meditations.clear()
        days.clear()
        prefs?.edit()?.clear()?.apply()
    }

    // ---- Internals ---------------------------------------------------------

    private fun dateKeyOf(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(WellnessLogic.ISO_DATE)

    private fun nextId(): Long = System.currentTimeMillis()

    private fun readArray(key: String): JSONArray? =
        runCatching { prefs?.getString(key, null)?.let { JSONArray(it) } }.getOrNull()

    private fun persistWorkouts() =
        prefs?.edit()?.putString(KEY_WORKOUTS, WellnessCodec.workoutsToJson(workouts.toList()).toString())?.apply()

    private fun persistMeditations() =
        prefs?.edit()?.putString(KEY_MEDITATIONS, WellnessCodec.meditationsToJson(meditations.toList()).toString())?.apply()

    private fun persistDays() =
        prefs?.edit()?.putString(KEY_DAYS, WellnessCodec.daysToJson(days.toList()).toString())?.apply()
}

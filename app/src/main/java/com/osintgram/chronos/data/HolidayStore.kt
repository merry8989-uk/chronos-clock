package com.osintgram.chronos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.time.LocalDate
import java.time.MonthDay

/**
 * Holidays that an alarm can skip over.
 *
 * Fixed-date Indian national holidays are built in (Republic Day, Independence
 * Day, Gandhi Jayanti). Everything else — the lunar-calendar festivals whose
 * dates move every year — is added by the user from Settings, so the app never
 * claims a date it cannot stand behind.
 */
object HolidayStore {

    private const val PREFS = "chronos_holidays"
    private const val KEY_CUSTOM = "custom_dates"

    /** Built-in fixed-date Indian national holidays. */
    val builtIn: List<Pair<String, MonthDay>> = listOf(
        "Republic Day" to MonthDay.of(1, 26),
        "Independence Day" to MonthDay.of(8, 15),
        "Gandhi Jayanti" to MonthDay.of(10, 2),
    )

    private var prefs: SharedPreferences? = null
    private val custom: SnapshotStateList<String> = mutableStateListOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        custom.clear()
        custom.addAll(prefs?.getStringSet(KEY_CUSTOM, emptySet())?.sorted() ?: emptyList())
    }

    /** User-added dates, as ISO strings (yyyy-MM-dd). */
    fun customDates(): List<String> = custom.toList()

    fun addCustom(date: LocalDate) {
        val iso = date.toString()
        if (!custom.contains(iso)) {
            custom.add(iso)
            custom.sort()
            persist()
        }
    }

    fun removeCustom(iso: String) {
        custom.remove(iso)
        persist()
    }

    /** Every holiday date that falls in [year]. */
    fun datesFor(year: Int): Set<LocalDate> {
        val out = mutableSetOf<LocalDate>()
        builtIn.forEach { (_, md) -> out.add(md.atYear(year)) }
        custom.forEach { iso ->
            runCatching { LocalDate.parse(iso) }.getOrNull()?.let { d ->
                if (d.year == year) out.add(d)
            }
        }
        return out
    }

    /** Holiday dates spanning this year and next, for scheduling. */
    fun schedulingDates(): Set<LocalDate> {
        val thisYear = LocalDate.now().year
        return datesFor(thisYear) + datesFor(thisYear + 1)
    }

    fun labelFor(date: LocalDate): String? {
        builtIn.firstOrNull { it.second == MonthDay.from(date) }?.let { return it.first }
        return if (custom.contains(date.toString())) "Custom holiday" else null
    }

    private fun persist() {
        prefs?.edit()?.putStringSet(KEY_CUSTOM, custom.toSet())?.apply()
    }
}

package com.shiftly.planner.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/**
 * The user's whole schedule: their shift vocabulary, their rotation, and any one-off changes.
 *
 * [overrides] is what makes the app usable in the real world — rotas get swapped, people take
 * leave, and a pure repeating pattern that can't be corrected is useless after week two.
 */
@Serializable
data class Schedule(
    val shiftTypes: List<ShiftType> = ShiftType.defaults,
    val pattern: ShiftPattern? = null,
    /** epochDay -> [ShiftType.id]. Takes precedence over [pattern]. */
    val overrides: Map<Long, String> = emptyMap(),
) {
    private val typesById: Map<String, ShiftType> by lazy { shiftTypes.associateBy { it.id } }

    fun typeOrNull(id: String): ShiftType? = typesById[id]

    /**
     * The shift on [date], override first, then pattern. Null when the user hasn't set up a
     * pattern yet, or when the stored id refers to a type that has since been deleted.
     */
    fun shiftOn(date: LocalDate): ShiftType? {
        val id = overrides[date.toEpochDay()] ?: pattern?.shiftTypeIdOn(date) ?: return null
        return typesById[id]
    }

    fun isOverridden(date: LocalDate): Boolean = overrides.containsKey(date.toEpochDay())

    /**
     * Adds any built-in shift type the stored schedule predates.
     *
     * [shiftTypes] is persisted, so a schedule saved before a built-in existed keeps the older,
     * shorter list. A preset referring to the newer type would then resolve to no shift at all and
     * the calendar would come up blank. Applied on load; user-created and user-edited types are
     * untouched because only ids missing entirely are added.
     */
    fun withBuiltinTypes(): Schedule {
        val missing = ShiftType.defaults.filterNot { builtin ->
            shiftTypes.any { it.id == builtin.id }
        }
        return if (missing.isEmpty()) this else copy(shiftTypes = shiftTypes + missing)
    }

    fun withOverride(date: LocalDate, shiftTypeId: String): Schedule =
        copy(overrides = overrides + (date.toEpochDay() to shiftTypeId))

    fun withoutOverride(date: LocalDate): Schedule =
        copy(overrides = overrides - date.toEpochDay())

    /** Resolved shifts for every day of [month], in calendar order. */
    fun shiftsInMonth(month: YearMonth): List<DayShift> =
        (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            DayShift(date, shiftOn(date), isOverridden(date))
        }

    /** Totals for the month summary bar: how many shifts, and how many hours. */
    fun monthSummary(month: YearMonth): MonthSummary {
        val days = shiftsInMonth(month)
        val working = days.filter { it.shift?.isWorking == true }
        val minutes = working.sumOf { it.shift?.durationMinutes ?: 0 }
        return MonthSummary(
            workingDays = working.size,
            offDays = days.size - working.size,
            totalMinutes = minutes,
        )
    }

    /** The next date on or after [from] whose shift is a working one. Used by the widget. */
    fun nextWorkingDay(from: LocalDate, searchLimitDays: Int = 366): DayShift? {
        var date = from
        repeat(searchLimitDays) {
            val shift = shiftOn(date)
            if (shift?.isWorking == true) return DayShift(date, shift, isOverridden(date))
            date = date.plusDays(1)
        }
        return null
    }
}

data class DayShift(
    val date: LocalDate,
    val shift: ShiftType?,
    val isOverridden: Boolean = false,
)

data class MonthSummary(
    val workingDays: Int,
    val offDays: Int,
    val totalMinutes: Int,
) {
    val totalHoursText: String
        get() {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
        }
}

package com.shiftly.planner.domain

import kotlinx.serialization.Serializable

/**
 * A kind of shift a user can be assigned to on a given day.
 *
 * [id] is stable and referenced by [ShiftPattern.cycle] and by overrides, so it must never change
 * once a type has been used. User-created types get a random UUID; built-ins use fixed ids so that
 * presets can reference them.
 */
@Serializable
data class ShiftType(
    val id: String,
    val name: String,
    /** 1-2 characters, drawn in the calendar cell. */
    val abbreviation: String,
    /** ARGB packed into a Long so it survives serialization without a Compose dependency. */
    val colorArgb: Long,
    /** Minutes from midnight, or null for non-working types. */
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val isWorking: Boolean = true,
) {
    /**
     * Shift length in minutes, handling shifts that cross midnight (a night shift ending at 07:00
     * is 12 hours after a 19:00 start, not negative twelve).
     */
    val durationMinutes: Int?
        get() {
            val s = startMinute ?: return null
            val e = endMinute ?: return null
            return if (e > s) e - s else e + MINUTES_PER_DAY - s
        }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        const val ID_OFF = "builtin_off"
        const val ID_DAY = "builtin_day"
        const val ID_NIGHT = "builtin_night"
        const val ID_EVENING = "builtin_evening"

        val OFF = ShiftType(
            id = ID_OFF,
            name = "Off",
            abbreviation = "",
            colorArgb = 0xFFE8EAED,
            isWorking = false,
        )

        val DAY = ShiftType(
            id = ID_DAY,
            name = "Day",
            abbreviation = "D",
            colorArgb = 0xFF1E88E5,
            startMinute = 7 * 60,
            endMinute = 19 * 60,
        )

        val NIGHT = ShiftType(
            id = ID_NIGHT,
            name = "Night",
            abbreviation = "N",
            colorArgb = 0xFF5E35B1,
            startMinute = 19 * 60,
            endMinute = 7 * 60,
        )

        val EVENING = ShiftType(
            id = ID_EVENING,
            name = "Evening",
            abbreviation = "E",
            colorArgb = 0xFFF4511E,
            startMinute = 15 * 60,
            endMinute = 23 * 60,
        )

        /** Seeded into a new install; the user can edit or add to these. */
        val defaults: List<ShiftType> = listOf(OFF, DAY, NIGHT, EVENING)
    }
}

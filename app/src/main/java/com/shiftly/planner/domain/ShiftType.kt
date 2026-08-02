package com.shiftly.planner.domain

import kotlinx.serialization.Serializable

/**
 * One continuous block of work inside a day.
 *
 * Most shifts are a single block, but watch systems are not: four hours on and eight off means two
 * separate four-hour blocks in the same twenty-four hours. Splitting the *type* rather than the
 * pattern is what keeps the one idea intact — a rotation is still a cycle of shift-type ids
 * anchored to a date, and a day still resolves to exactly one type.
 */
@Serializable
data class ShiftSegment(
    /** Minutes from midnight. */
    val startMinute: Int,
    val endMinute: Int,
) {
    /**
     * Wrap-aware, so a block running 22:00 to 02:00 is four hours rather than minus twenty, and a
     * block that ends when it starts has run right round the clock.
     */
    val lengthMinutes: Int
        get() = if (endMinute > startMinute) {
            endMinute - startMinute
        } else {
            endMinute + ShiftType.MINUTES_PER_DAY - startMinute
        }
}

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
    /**
     * Minutes from midnight, or null for non-working types.
     *
     * Kept alongside [segments] rather than replaced by it so that a schedule stored before splits
     * existed still decodes, and so the common case — one block — stays a pair of fields rather
     * than a list of one.
     */
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    /** Set instead of [startMinute]/[endMinute] when the day is split into several blocks. */
    val segments: List<ShiftSegment> = emptyList(),
    val isWorking: Boolean = true,
) {
    /**
     * Every block worked, however the type was written.
     *
     * The rest of the app goes through this rather than the raw fields, so a split day and a plain
     * one are the same shape to the calendar, the widget, the totals and the calendar export.
     */
    val blocks: List<ShiftSegment>
        get() = when {
            segments.isNotEmpty() -> segments
            startMinute != null && endMinute != null ->
                listOf(ShiftSegment(startMinute, endMinute))
            else -> emptyList()
        }

    /** Total time worked in a day, summed across every block. Null when nothing is worked. */
    val durationMinutes: Int?
        get() = blocks.takeIf { it.isNotEmpty() }?.sumOf { it.lengthMinutes }

    /** True when the day is broken into more than one block. */
    val isSplit: Boolean get() = blocks.size > 1

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        const val ID_OFF = "builtin_off"
        const val ID_DAY = "builtin_day"
        const val ID_NIGHT = "builtin_night"
        const val ID_EVENING = "builtin_evening"
        const val ID_FULL_DAY = "builtin_full_day"

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

        /**
         * A full 24-hour tour, as worked by fire and ambulance crews.
         *
         * Starts and ends at 08:00, which [durationMinutes] reads as a whole day rather than as
         * zero — a shift that ends when it started has run right round the clock.
         */
        val FULL_DAY = ShiftType(
            id = ID_FULL_DAY,
            name = "24 hours",
            abbreviation = "24",
            colorArgb = 0xFF00897B,
            startMinute = 8 * 60,
            endMinute = 8 * 60,
        )

        /** Seeded into a new install; the user can edit or add to these. */
        val defaults: List<ShiftType> = listOf(OFF, DAY, NIGHT, EVENING, FULL_DAY)
    }
}

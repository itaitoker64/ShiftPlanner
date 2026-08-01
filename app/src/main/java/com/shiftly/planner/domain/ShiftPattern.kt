package com.shiftly.planner.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * A repeating rota: a fixed-length [cycle] of shift-type ids anchored to a real calendar date.
 *
 * Every rotation this app supports — presets and user-built alike — is expressible this way, which
 * is why there is exactly one pattern type rather than a class per rotation. The cycle repeats
 * forever in both directions from [anchorEpochDay], so past months resolve as readily as future
 * ones.
 */
@Serializable
data class ShiftPattern(
    val id: String,
    val name: String,
    /** One entry per day of the cycle; each is a [ShiftType.id]. Must be non-empty. */
    val cycle: List<String>,
    /** The real date on which the user sits at `cycle[0]`. */
    val anchorEpochDay: Long,
) {
    init {
        require(cycle.isNotEmpty()) { "A shift pattern must have at least one day in its cycle" }
    }

    val cycleLength: Int get() = cycle.size

    val anchorDate: LocalDate get() = LocalDate.ofEpochDay(anchorEpochDay)

    /**
     * The shift-type id this pattern assigns to [date], ignoring any user override.
     *
     * Uses floor-mod rather than `%` so dates before the anchor wrap correctly instead of
     * producing a negative index.
     */
    fun shiftTypeIdOn(date: LocalDate): String {
        val offset = date.toEpochDay() - anchorEpochDay
        val index = Math.floorMod(offset, cycle.size.toLong()).toInt()
        return cycle[index]
    }

    /** Position within the cycle on [date], 0-based. Shown in the day detail sheet. */
    fun cycleDayOn(date: LocalDate): Int =
        Math.floorMod(date.toEpochDay() - anchorEpochDay, cycle.size.toLong()).toInt()

    /** Re-anchors the pattern so that [date] lands on cycle position [cycleIndex]. */
    fun anchoredSoThat(date: LocalDate, cycleIndex: Int): ShiftPattern {
        require(cycleIndex in cycle.indices) { "cycleIndex out of bounds" }
        return copy(anchorEpochDay = date.toEpochDay() - cycleIndex)
    }
}

package com.shiftly.planner.domain

import com.shiftly.planner.domain.ShiftType.Companion.ID_DAY
import com.shiftly.planner.domain.ShiftType.Companion.ID_NIGHT
import com.shiftly.planner.domain.ShiftType.Companion.ID_OFF

/**
 * A ready-made rotation the user can pick during setup instead of tapping out their own cycle.
 *
 * These cover the rotations actually worked in healthcare, manufacturing, emergency services and
 * aviation. Anything not listed here is reachable through the custom cycle builder, so this list
 * is a convenience, not a constraint.
 */
data class ShiftPreset(
    val key: String,
    val name: String,
    val description: String,
    val cycle: List<String>,
) {
    val cycleLength: Int get() = cycle.size
    val workingDaysPerCycle: Int get() = cycle.count { it != ID_OFF }
}

/** Builds a cycle from run-length pairs, e.g. `runs(ID_DAY to 4, ID_OFF to 4)`. */
private fun runs(vararg segments: Pair<String, Int>): List<String> =
    segments.flatMap { (id, count) -> List(count) { id } }

object Presets {

    val all: List<ShiftPreset> = listOf(
        ShiftPreset(
            key = "4on4off_days",
            name = "4 on, 4 off",
            description = "Four 12-hour days, then four days off. 8-day cycle.",
            cycle = runs(ID_DAY to 4, ID_OFF to 4),
        ),
        ShiftPreset(
            key = "4on4off_dn",
            name = "4 on, 4 off — days & nights",
            description = "Four days on, four off, four nights on, four off. 16-day cycle.",
            cycle = runs(ID_DAY to 4, ID_OFF to 4, ID_NIGHT to 4, ID_OFF to 4),
        ),
        ShiftPreset(
            key = "panama_days",
            name = "Panama (2-2-3)",
            description = "2 on, 2 off, 3 on, 2 off, 2 on, 3 off. 14-day cycle.",
            cycle = runs(
                ID_DAY to 2, ID_OFF to 2, ID_DAY to 3,
                ID_OFF to 2, ID_DAY to 2, ID_OFF to 3,
            ),
        ),
        ShiftPreset(
            key = "panama_dn",
            name = "Panama (2-2-3) — days & nights",
            description = "A full 2-2-3 rotation on days, then the same on nights. 28-day cycle.",
            cycle = runs(
                ID_DAY to 2, ID_OFF to 2, ID_DAY to 3,
                ID_OFF to 2, ID_DAY to 2, ID_OFF to 3,
                ID_NIGHT to 2, ID_OFF to 2, ID_NIGHT to 3,
                ID_OFF to 2, ID_NIGHT to 2, ID_OFF to 3,
            ),
        ),
        ShiftPreset(
            key = "dupont",
            name = "DuPont",
            description = "The classic 28-day 12-hour rotation, ending in a full week off.",
            cycle = runs(
                ID_NIGHT to 4, ID_OFF to 3,
                ID_DAY to 3, ID_OFF to 1,
                ID_NIGHT to 3, ID_OFF to 3,
                ID_DAY to 4, ID_OFF to 7,
            ),
        ),
        ShiftPreset(
            key = "3on3off",
            name = "3 on, 3 off",
            description = "Three 12-hour days on, three off. 6-day cycle.",
            cycle = runs(ID_DAY to 3, ID_OFF to 3),
        ),
        ShiftPreset(
            key = "7on7off",
            name = "7 on, 7 off",
            description = "A full week on, a full week off. 14-day cycle.",
            cycle = runs(ID_DAY to 7, ID_OFF to 7),
        ),
        ShiftPreset(
            key = "5on2off",
            name = "5 on, 2 off",
            description = "A standard working week. Anchor it to a Monday. 7-day cycle.",
            cycle = runs(ID_DAY to 5, ID_OFF to 2),
        ),
        ShiftPreset(
            key = "6on2off",
            name = "6 on, 2 off",
            description = "Six on, two off — drifts through the week. 8-day cycle.",
            cycle = runs(ID_DAY to 6, ID_OFF to 2),
        ),
        ShiftPreset(
            key = "week_days_nights",
            name = "Week of days, week of nights",
            description = "Five days on, two off, five nights on, two off. 14-day cycle.",
            cycle = runs(ID_DAY to 5, ID_OFF to 2, ID_NIGHT to 5, ID_OFF to 2),
        ),
    )

    fun byKey(key: String): ShiftPreset? = all.firstOrNull { it.key == key }
}

package com.shiftly.planner.domain

import com.shiftly.planner.domain.ShiftType.Companion.ID_DAY
import com.shiftly.planner.domain.ShiftType.Companion.ID_NIGHT
import com.shiftly.planner.domain.ShiftType.Companion.ID_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ShiftPatternTest {

    private val anchor = LocalDate.of(2026, 1, 1)

    private fun pattern(cycle: List<String>) = ShiftPattern(
        id = "test",
        name = "Test",
        cycle = cycle,
        anchorEpochDay = anchor.toEpochDay(),
    )

    @Test
    fun `resolves the anchor date to the first day of the cycle`() {
        val p = pattern(listOf(ID_DAY, ID_OFF))
        assertEquals(ID_DAY, p.shiftTypeIdOn(anchor))
        assertEquals(0, p.cycleDayOn(anchor))
    }

    @Test
    fun `repeats forward across cycle boundaries`() {
        val p = pattern(Presets.byKey("4on4off_days")!!.cycle)
        // Days 0-3 work, 4-7 off, then it starts over.
        assertEquals(ID_DAY, p.shiftTypeIdOn(anchor.plusDays(3)))
        assertEquals(ID_OFF, p.shiftTypeIdOn(anchor.plusDays(4)))
        assertEquals(ID_DAY, p.shiftTypeIdOn(anchor.plusDays(8)))
        assertEquals(ID_DAY, p.shiftTypeIdOn(anchor.plusDays(800)))
    }

    @Test
    fun `resolves dates before the anchor without wrapping negative`() {
        val p = pattern(listOf(ID_DAY, ID_OFF, ID_NIGHT))
        // One day before the anchor is the last day of the previous cycle.
        assertEquals(ID_NIGHT, p.shiftTypeIdOn(anchor.minusDays(1)))
        assertEquals(ID_OFF, p.shiftTypeIdOn(anchor.minusDays(2)))
        assertEquals(ID_DAY, p.shiftTypeIdOn(anchor.minusDays(3)))
        assertEquals(2, p.cycleDayOn(anchor.minusDays(1)))
    }

    @Test
    fun `re-anchoring places the given date at the requested cycle position`() {
        val p = pattern(Presets.byKey("panama_days")!!.cycle)
        val target = LocalDate.of(2026, 3, 17)
        val moved = p.anchoredSoThat(target, cycleIndex = 5)
        assertEquals(5, moved.cycleDayOn(target))
        assertEquals(p.cycle, moved.cycle)
    }

    @Test
    fun `every preset has a non-empty cycle and at least one working day`() {
        Presets.all.forEach { preset ->
            assertTrue(preset.name, preset.cycleLength > 0)
            assertTrue(preset.name, preset.workingDaysPerCycle > 0)
            assertTrue(preset.name, preset.cycle.any { it == ID_OFF })
        }
    }

    @Test
    fun `preset cycle lengths match their documented descriptions`() {
        assertEquals(8, Presets.byKey("4on4off_days")!!.cycleLength)
        assertEquals(16, Presets.byKey("4on4off_dn")!!.cycleLength)
        assertEquals(14, Presets.byKey("panama_days")!!.cycleLength)
        assertEquals(28, Presets.byKey("panama_dn")!!.cycleLength)
        assertEquals(28, Presets.byKey("dupont")!!.cycleLength)
        assertEquals(14, Presets.byKey("week_days_nights")!!.cycleLength)
    }

    @Test
    fun `panama works seven of every fourteen days`() {
        assertEquals(7, Presets.byKey("panama_days")!!.workingDaysPerCycle)
    }

    @Test
    fun `dupont works fourteen of every twenty-eight days`() {
        assertEquals(14, Presets.byKey("dupont")!!.workingDaysPerCycle)
    }
}

class ScheduleTest {

    private val anchor = LocalDate.of(2026, 1, 1)

    private fun scheduleWith(cycle: List<String>) = Schedule(
        pattern = ShiftPattern("test", "Test", cycle, anchor.toEpochDay()),
    )

    @Test
    fun `returns null shift when no pattern has been set up`() {
        assertNull(Schedule().shiftOn(anchor))
    }

    @Test
    fun `an override wins over the pattern`() {
        val schedule = scheduleWith(listOf(ID_DAY, ID_OFF))
            .withOverride(anchor, ID_NIGHT)

        assertEquals(ShiftType.NIGHT, schedule.shiftOn(anchor))
        assertTrue(schedule.isOverridden(anchor))
        // Neighbouring days are untouched.
        assertEquals(ShiftType.OFF, schedule.shiftOn(anchor.plusDays(1)))
        assertFalse(schedule.isOverridden(anchor.plusDays(1)))
    }

    @Test
    fun `clearing an override falls back to the pattern`() {
        val schedule = scheduleWith(listOf(ID_DAY, ID_OFF))
            .withOverride(anchor, ID_NIGHT)
            .withoutOverride(anchor)

        assertEquals(ShiftType.DAY, schedule.shiftOn(anchor))
        assertFalse(schedule.isOverridden(anchor))
    }

    @Test
    fun `shift resolves to null when its type has been deleted`() {
        val schedule = scheduleWith(listOf("ghost_type"))
        assertNull(schedule.shiftOn(anchor))
    }

    @Test
    fun `month view returns one entry per calendar day`() {
        val schedule = scheduleWith(Presets.byKey("panama_days")!!.cycle)
        val february = YearMonth.of(2026, 2)

        val days = schedule.shiftsInMonth(february)

        assertEquals(28, days.size)
        assertEquals(february.atDay(1), days.first().date)
        assertEquals(february.atEndOfMonth(), days.last().date)
    }

    @Test
    fun `month summary counts working days and hours`() {
        // 4 on / 4 off from Jan 1 2026: 12-hour days.
        val schedule = scheduleWith(Presets.byKey("4on4off_days")!!.cycle)
        val january = YearMonth.of(2026, 1)

        val summary = schedule.monthSummary(january)

        assertEquals(31, summary.workingDays + summary.offDays)
        assertEquals(summary.workingDays * 12 * 60, summary.totalMinutes)
        assertEquals("${summary.workingDays * 12}h", summary.totalHoursText)
    }

    @Test
    fun `overrides are reflected in the month summary`() {
        val schedule = scheduleWith(Presets.byKey("4on4off_days")!!.cycle)
        val january = YearMonth.of(2026, 1)
        val before = schedule.monthSummary(january)

        // Turn the first working day into a day off.
        val after = schedule.withOverride(anchor, ID_OFF).monthSummary(january)

        assertEquals(before.workingDays - 1, after.workingDays)
        assertEquals(before.totalMinutes - 12 * 60, after.totalMinutes)
    }

    @Test
    fun `next working day finds today when today is a shift`() {
        val schedule = scheduleWith(Presets.byKey("4on4off_days")!!.cycle)
        val next = schedule.nextWorkingDay(anchor)
        assertEquals(anchor, next?.date)
    }

    @Test
    fun `next working day skips the off block`() {
        val schedule = scheduleWith(Presets.byKey("4on4off_days")!!.cycle)
        // Jan 5 is the first of four days off; the next shift is Jan 9.
        val next = schedule.nextWorkingDay(anchor.plusDays(4))
        assertEquals(anchor.plusDays(8), next?.date)
    }

    @Test
    fun `next working day returns null for an all-off schedule`() {
        val schedule = scheduleWith(listOf(ID_OFF))
        assertNull(schedule.nextWorkingDay(anchor))
    }
}

class ShiftTypeTest {

    @Test
    fun `day shift duration is a plain subtraction`() {
        assertEquals(12 * 60, ShiftType.DAY.durationMinutes)
    }

    @Test
    fun `night shift duration handles crossing midnight`() {
        // 19:00 -> 07:00 is twelve hours, not minus twelve.
        assertEquals(12 * 60, ShiftType.NIGHT.durationMinutes)
    }

    @Test
    fun `non-working types have no duration`() {
        assertNull(ShiftType.OFF.durationMinutes)
    }
}

package com.shiftly.planner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiftly.planner.R
import com.shiftly.planner.calendar.CalendarSync
import com.shiftly.planner.data.ScheduleRepository
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftPattern
import com.shiftly.planner.domain.ShiftPreset
import com.shiftly.planner.domain.ShiftType
import com.shiftly.planner.widget.ShiftWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ScheduleRepository(app)

    val schedule: StateFlow<Schedule> = repository.schedule.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Schedule(),
    )

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    /**
     * False until the first-run walkthrough has been finished or skipped.
     *
     * Kept out of [Schedule] deliberately: it says something about this install, not about the
     * rota, and a corrupt schedule blob must not also lose the fact it has been seen.
     *
     * Exposed as a plain Flow rather than shared into a StateFlow. Each stateIn holds a collector
     * open on viewModelScope — which is Dispatchers.Main — for five seconds past its last
     * subscriber, and pending main-thread work is exactly what a Compose test waits on.
     */
    val onboardingComplete: Flow<Boolean> = repository.onboardingComplete

    fun markOnboardingComplete() {
        viewModelScope.launch { repository.setOnboardingComplete() }
    }

    /** Plain Flow for the same reason as [onboardingComplete]. */
    val syncedCalendarId: Flow<Long?> = repository.syncedCalendarId

    private val _calendarStatus = MutableStateFlow<CalendarStatus>(CalendarStatus.Idle)
    val calendarStatus: StateFlow<CalendarStatus> = _calendarStatus.asStateFlow()

    /**
     * Writes a rolling window of shifts into [calendarId], replacing anything written before.
     *
     * On the IO dispatcher because it is a content-provider round trip per shift — a year of a
     * 4-on-4-off rota is around 180 inserts, which is not a main-thread amount of work.
     */
    fun syncToCalendar(calendarId: Long, months: Long) {
        viewModelScope.launch {
            _calendarStatus.value = CalendarStatus.Working
            val current = schedule.value
            val previous = repository.exportedEventIds.first()
            val written = withContext(Dispatchers.IO) {
                CalendarSync.sync(
                    context = getApplication(),
                    schedule = current,
                    calendarId = calendarId,
                    previouslyWritten = previous,
                    months = months,
                )
            }
            repository.setCalendarSync(calendarId, written)
            _calendarStatus.value = CalendarStatus.Synced(written.size)
        }
    }

    /** Takes back every event this app wrote, and forgets the calendar. */
    fun removeFromCalendar() {
        viewModelScope.launch {
            _calendarStatus.value = CalendarStatus.Working
            val previous = repository.exportedEventIds.first()
            withContext(Dispatchers.IO) {
                CalendarSync.removeAll(getApplication(), previous)
            }
            repository.setCalendarSync(null, emptySet())
            _calendarStatus.value = CalendarStatus.Removed
        }
    }

    fun clearCalendarStatus() {
        _calendarStatus.value = CalendarStatus.Idle
    }

    fun showMonth(month: YearMonth) {
        _visibleMonth.value = month
    }

    fun showToday() {
        _visibleMonth.value = YearMonth.now()
        _selectedDate.value = LocalDate.now()
    }

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    /**
     * Applies a preset, anchoring it so that [startDate] is day one of the cycle.
     *
     * Overrides are deliberately cleared: they were corrections against the *old* rotation and
     * would silently corrupt the new one.
     */
    fun applyPreset(
        preset: ShiftPreset,
        startDate: LocalDate,
        /** What the picker showed, so a schedule set up in Hebrew is not labelled in English. */
        name: String = preset.name,
    ) = mutate { current ->
        current.copy(
            pattern = ShiftPattern(
                id = UUID.randomUUID().toString(),
                name = name,
                cycle = preset.cycle,
                anchorEpochDay = startDate.toEpochDay(),
            ),
            overrides = emptyMap(),
        )
    }

    /** Applies a cycle the user tapped out themselves. */
    fun applyCustomPattern(name: String, cycle: List<String>, startDate: LocalDate) =
        mutate { current ->
            val fallback = getApplication<Application>().getString(R.string.default_rotation_name)
            current.copy(
                pattern = ShiftPattern(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { fallback },
                    cycle = cycle,
                    anchorEpochDay = startDate.toEpochDay(),
                ),
                overrides = emptyMap(),
            )
        }

    /** Shifts the whole rotation without rebuilding it — for "I'm actually a day ahead". */
    fun nudgeAnchor(days: Long) = mutate { current ->
        val pattern = current.pattern ?: return@mutate current
        current.copy(pattern = pattern.copy(anchorEpochDay = pattern.anchorEpochDay + days))
    }

    fun setOverride(date: LocalDate, shiftTypeId: String) = mutate { current ->
        // Writing the value the pattern already produces would be a no-op override; drop it
        // instead so the day keeps following the rotation if the anchor later moves.
        if (current.pattern?.shiftTypeIdOn(date) == shiftTypeId) {
            current.withoutOverride(date)
        } else {
            current.withOverride(date, shiftTypeId)
        }
    }

    /** Adds a shift type, or saves an edit to an existing one. */
    fun saveShiftType(type: ShiftType) = mutate { it.withShiftType(type) }

    /** No-op while the type is still on the rotation — see [Schedule.withoutShiftType]. */
    fun deleteShiftType(typeId: String) = mutate { it.withoutShiftType(typeId) }

    fun clearOverride(date: LocalDate) = mutate { it.withoutOverride(date) }

    fun clearAllOverrides() = mutate { it.copy(overrides = emptyMap()) }

    private fun mutate(transform: (Schedule) -> Schedule) {
        viewModelScope.launch {
            repository.update(transform)
            // The widget renders from the same store, so it must be told to redraw.
            ShiftWidgetUpdater.requestUpdate(getApplication())
        }
    }
}

/** Where a calendar sync has got to, for the screen that started it. */
sealed interface CalendarStatus {
    data object Idle : CalendarStatus
    data object Working : CalendarStatus
    data class Synced(val eventCount: Int) : CalendarStatus
    data object Removed : CalendarStatus
}

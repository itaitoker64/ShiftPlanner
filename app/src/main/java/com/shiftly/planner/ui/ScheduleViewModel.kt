package com.shiftly.planner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiftly.planner.data.ScheduleRepository
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftPattern
import com.shiftly.planner.domain.ShiftPreset
import com.shiftly.planner.widget.ShiftWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    fun applyPreset(preset: ShiftPreset, startDate: LocalDate) = mutate { current ->
        current.copy(
            pattern = ShiftPattern(
                id = UUID.randomUUID().toString(),
                name = preset.name,
                cycle = preset.cycle,
                anchorEpochDay = startDate.toEpochDay(),
            ),
            overrides = emptyMap(),
        )
    }

    /** Applies a cycle the user tapped out themselves. */
    fun applyCustomPattern(name: String, cycle: List<String>, startDate: LocalDate) =
        mutate { current ->
            current.copy(
                pattern = ShiftPattern(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "My rotation" },
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

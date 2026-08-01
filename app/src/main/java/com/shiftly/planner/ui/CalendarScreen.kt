package com.shiftly.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.domain.DayShift
import com.shiftly.planner.domain.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: ScheduleViewModel,
    onEditPattern: () -> Unit,
    bannerAd: @Composable () -> Unit = {},
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = month.atDay(1).format(MONTH_TITLE),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.showMonth(month.minusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showMonth(month.plusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                    }
                    TextButton(onClick = viewModel::showToday) { Text("Today") }
                    IconButton(onClick = onEditPattern) {
                        Icon(Icons.Default.Edit, "Edit rotation")
                    }
                },
            )
        },
        bottomBar = { bannerAd() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
        ) {
            MonthSummaryBar(schedule, month)
            Spacer(Modifier.size(8.dp))
            WeekdayHeader()
            MonthGrid(
                schedule = schedule,
                month = month,
                selected = selected,
                onSelect = viewModel::selectDate,
            )
            Spacer(Modifier.size(8.dp))
            PatternFooter(schedule, onEditPattern)
        }
    }

    selected?.let { date ->
        DayDetailSheet(
            date = date,
            schedule = schedule,
            onDismiss = { viewModel.selectDate(null) },
            onPick = { typeId -> viewModel.setOverride(date, typeId) },
            onResetToPattern = { viewModel.clearOverride(date) },
        )
    }
}

@Composable
private fun MonthSummaryBar(schedule: Schedule, month: YearMonth) {
    if (schedule.pattern == null) return
    val summary = schedule.monthSummary(month)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryStat("${summary.workingDays}", "shifts")
            SummaryStat("${summary.offDays}", "days off")
            SummaryStat(summary.totalHoursText, "hours")
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Respects the device locale's first day of week — Monday in most of the world, Sunday in the US. */
private fun weekdayOrder(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0..6).map { first.plus(it.toLong()) }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        weekdayOrder().forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    schedule: Schedule,
    month: YearMonth,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val order = weekdayOrder()
    val firstOfMonth = month.atDay(1)
    // How many blank cells before day 1, given the locale's week start.
    val leadingBlanks = order.indexOf(firstOfMonth.dayOfWeek)
    val cells = leadingBlanks + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()

    Column(Modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val dayOfMonth = index - leadingBlanks + 1
                    Box(Modifier.weight(1f)) {
                        if (dayOfMonth in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayOfMonth)
                            DayCell(
                                day = DayShift(date, schedule.shiftOn(date), schedule.isOverridden(date)),
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = { onSelect(date) },
                            )
                        } else {
                            Spacer(Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: DayShift,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shift = day.shift
    val fill = shift?.let { Color(it.colorArgb.toInt()) } ?: Color.Transparent
    val working = shift?.isWorking == true

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (working) fill else fill.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${day.date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (working) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            if (shift != null && shift.abbreviation.isNotEmpty()) {
                Text(
                    text = shift.abbreviation,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (working) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // A small dot marks days the user has hand-edited away from the rotation.
        if (day.isOverridden) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
        }

        if (isToday) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (working) Color.White else MaterialTheme.colorScheme.primary
                    ),
            )
        }
    }
}

@Composable
private fun PatternFooter(schedule: Schedule, onEditPattern: () -> Unit) {
    val pattern = schedule.pattern
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditPattern)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pattern?.let { "${it.name} · ${it.cycleLength}-day cycle" }
                ?: "No rotation set up yet",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (pattern == null) "Set up" else "Change",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

package com.shiftly.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.domain.DayShift
import com.shiftly.planner.domain.MonthSummary
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * Readable text for an arbitrary cell colour.
 *
 * Shift colours are user-editable, so "white on working days" only holds for the built-ins. Picking
 * from the colour's own brightness keeps a pale custom colour legible instead of white-on-white.
 */
internal fun textOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF10151B) else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: ScheduleViewModel,
    onEditPattern: () -> Unit,
    onShowGuide: () -> Unit = {},
    /** Taken as a parameter so a test can render a month around a fixed "today". */
    today: LocalDate = LocalDate.now(),
    bannerAd: @Composable () -> Unit = {},
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()

    // Resolving a month walks every day through the pattern and the override map. Doing it once per
    // (schedule, month) rather than once per cell per recomposition is what keeps swiping months
    // and opening the day sheet from re-deriving the whole grid.
    val days = remember(schedule, month) { schedule.shiftsInMonth(month) }
    val summary = remember(schedule, days) { schedule.summaryOf(days) }
    val legend = remember(days) {
        days.mapNotNull { it.shift }.distinctBy { it.id }
    }

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
                    IconButton(onClick = onShowGuide) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, "How this app works")
                    }
                    IconButton(onClick = onEditPattern) {
                        Icon(Icons.Default.Edit, "Edit rotation")
                    }
                },
            )
        },
        bottomBar = { bannerAd() },
    ) { padding ->
        // Scrollable because the tall months (six grid rows) plus the summary, legend and footer
        // can outgrow a short screen. On a normal phone nothing scrolls; on a small one the
        // rotation footer stays reachable instead of being clipped off the bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            if (schedule.pattern != null) {
                MonthSummaryBar(summary)
                Spacer(Modifier.size(12.dp))
            }
            WeekdayHeader()
            Spacer(Modifier.size(4.dp))
            MonthGrid(
                days = days,
                month = month,
                today = today,
                selected = selected,
                onSelect = viewModel::selectDate,
            )
            if (legend.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                Legend(legend)
            }
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
private fun MonthSummaryBar(summary: MonthSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryStat("${summary.workingDays}", "shifts", Modifier.weight(1f))
        SummaryStat("${summary.offDays}", "days off", Modifier.weight(1f))
        SummaryStat(summary.totalHoursText, "hours", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Respects the device locale's first day of week — Monday in most of the world, Sunday in the US. */
private fun weekdayOrder(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0..6).map { first.plus(it.toLong()) }
}

@Composable
private fun WeekdayHeader() {
    val order = remember { weekdayOrder() }
    val labels = remember(order) {
        order.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<DayShift>,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val order = remember { weekdayOrder() }
    // How many blank cells before day 1, given the locale's week start.
    val leadingBlanks = remember(order, month) { order.indexOf(month.atDay(1).dayOfWeek) }
    val rows = remember(leadingBlanks, month) { (leadingBlanks + month.lengthOfMonth() + 6) / 7 }

    Column(Modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column - leadingBlanks
                    Box(Modifier.weight(1f)) {
                        val day = days.getOrNull(index)
                        if (day != null) {
                            DayCell(
                                day = day,
                                isToday = day.date == today,
                                isSelected = day.date == selected,
                                onClick = { onSelect(day.date) },
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

/**
 * One square in the grid.
 *
 * Internal rather than private so the guide can show the real thing in its legend — a hand-drawn
 * copy would drift out of step with the calendar the first time either changes.
 */
@Composable
internal fun DayCell(
    day: DayShift,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shift = day.shift
    val working = shift?.isWorking == true
    val base = shift?.let { Color(it.colorArgb.toInt()) } ?: Color.Transparent
    val fill = if (working) base else base.copy(alpha = 0.45f)

    val surface = MaterialTheme.colorScheme.surface
    val content = if (shift == null) {
        MaterialTheme.colorScheme.onSurface
    } else {
        textOn(fill.compositeOver(surface))
    }

    // Today is a ring; the selected day is a thicker one. Painting a second translucent background
    // over the fill — the previous approach — muddied the shift colour rather than framing it.
    val ring = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val ringWidth = if (isSelected) 2.5.dp else 2.dp

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(ringWidth, ring, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${day.date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = content,
            )
            if (shift != null && shift.abbreviation.isNotEmpty()) {
                Text(
                    text = shift.abbreviation,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = content.copy(alpha = 0.85f),
                )
            }
        }

        // A small dot marks days the user has hand-edited away from the rotation.
        if (day.isOverridden) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

/** Which colour means which shift, for the shifts actually on screen this month. */
@Composable
private fun Legend(types: List<ShiftType>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        types.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(type.colorArgb.toInt())),
                )
                Spacer(Modifier.size(5.dp))
                Text(
                    text = type.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatternFooter(schedule: Schedule, onEditPattern: () -> Unit) {
    val pattern = schedule.pattern
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditPattern),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = pattern?.let { "${it.name} · ${it.cycleLength}-day cycle" }
                    ?: "No rotation set up yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (pattern == null) "Set up" else "Change",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

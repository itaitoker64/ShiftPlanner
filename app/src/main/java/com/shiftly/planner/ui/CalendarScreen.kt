package com.shiftly.planner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.R
import com.shiftly.planner.domain.DayShift
import com.shiftly.planner.domain.MonthSummary
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftType
import com.shiftly.planner.text.DateSkeleton
import com.shiftly.planner.text.autoIsolate
import com.shiftly.planner.text.displayAbbreviation
import com.shiftly.planner.text.displayName
import com.shiftly.planner.text.rememberDateFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

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
    onShowSettings: () -> Unit = {},
    /** Taken as a parameter so a test can render a month around a fixed "today". */
    today: LocalDate = LocalDate.now(),
    bannerAd: @Composable () -> Unit = {},
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()

    // Empty means normal tapping. Once a long press has put something in here, tapping toggles
    // days instead of opening one, which is the only way a run of days can be changed at once.
    var picked by remember { mutableStateOf(emptySet<LocalDate>()) }

    // Resolving a month walks every day through the pattern and the override map. Doing it once per
    // (schedule, month) rather than once per cell per recomposition is what keeps swiping months
    // and opening the day sheet from re-deriving the whole grid.
    val days = remember(schedule, month) { schedule.shiftsInMonth(month) }
    val summary = remember(schedule, days) { schedule.summaryOf(days) }
    val legend = remember(days) {
        days.mapNotNull { it.shift }.distinctBy { it.id }
    }

    val monthTitle = rememberDateFormatter(DateSkeleton.MONTH_AND_YEAR, "MMMM yyyy")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = month.atDay(1).format(monthTitle),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.showMonth(month.minusMonths(1)) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.cd_previous_month),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showMonth(month.plusMonths(1)) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(R.string.cd_next_month),
                        )
                    }
                    TextButton(onClick = viewModel::showToday) {
                        Text(stringResource(R.string.action_today))
                    }
                    IconButton(onClick = onShowGuide) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            stringResource(R.string.cd_guide),
                        )
                    }
                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.cd_settings))
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (picked.isNotEmpty()) {
                    BulkEditBar(
                        count = picked.size,
                        shiftTypes = schedule.shiftTypes,
                        onPick = { typeId ->
                            viewModel.setOverrides(picked, typeId)
                            picked = emptySet()
                        },
                        onResetToRotation = {
                            viewModel.clearOverrides(picked)
                            picked = emptySet()
                        },
                        onCancel = { picked = emptySet() },
                    )
                }
                bannerAd()
            }
        },
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
                picked = picked,
                onSelect = { date ->
                    if (picked.isEmpty()) {
                        viewModel.selectDate(date)
                    } else {
                        picked = if (date in picked) picked - date else picked + date
                    }
                },
                onLongSelect = { date -> picked = picked + date },
                onPreviousMonth = { viewModel.showMonth(month.minusMonths(1)) },
                onNextMonth = { viewModel.showMonth(month.plusMonths(1)) },
            )
            if (legend.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                Legend(legend)
                Spacer(Modifier.size(6.dp))
                Text(
                    // A long press is invisible unless something says so.
                    text = stringResource(R.string.bulk_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            PatternFooter(schedule, onEditPattern)
        }
    }

    selected?.takeIf { picked.isEmpty() }?.let { date ->
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
    // Formatted here rather than through MonthSummary.totalHoursText: the domain has no way to
    // reach a string resource, so "8h 30m" would stay English on a Hebrew phone.
    val hours = summary.totalMinutes / 60
    val minutes = summary.totalMinutes % 60
    val hoursText = if (minutes == 0) {
        stringResource(R.string.hours_only, hours)
    } else {
        stringResource(R.string.hours_and_minutes, hours, minutes)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryStat(
            value = "${summary.workingDays}",
            label = stringResource(R.string.summary_shifts),
            modifier = Modifier.weight(1f),
        )
        SummaryStat(
            value = "${summary.offDays}",
            label = stringResource(R.string.summary_days_off),
            modifier = Modifier.weight(1f),
        )
        SummaryStat(
            value = hoursText,
            label = stringResource(R.string.summary_hours),
            modifier = Modifier.weight(1f),
        )
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

/**
 * Respects the locale's first day of week — Monday in most of the world, Sunday in Israel and the
 * US. Takes the locale rather than reading `Locale.getDefault()`, which below API 33 is still the
 * phone's language even when the app has been switched to another one.
 */
private fun weekdayOrder(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0..6).map { first.plus(it.toLong()) }
}

/** The order the grid runs in, rebuilt if the app's language changes under it. */
@Composable
private fun rememberWeekdayOrder(): List<DayOfWeek> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { weekdayOrder(locale) }
}

@Composable
private fun WeekdayHeader() {
    val locale = LocalConfiguration.current.locales[0]
    val order = rememberWeekdayOrder()
    val labels = remember(order, locale) {
        order.map { it.getDisplayName(TextStyle.NARROW, locale) }
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
    picked: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onLongSelect: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val order = rememberWeekdayOrder()
    // How many blank cells before day 1, given the locale's week start.
    val leadingBlanks = remember(order, month) { order.indexOf(month.atDay(1).dayOfWeek) }
    val rows = remember(leadingBlanks, month) { (leadingBlanks + month.lengthOfMonth() + 6) / 7 }
    val layoutDirection = LocalLayoutDirection.current

    Column(
        Modifier
            .fillMaxWidth()
            // Dragging the grid sideways moves a month, which is how every calendar on a phone
            // behaves. Mirrored under a right-to-left layout, where "forward" is the other way.
            .pointerInput(month, layoutDirection) {
                var travelled = 0f
                val threshold = 56.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(travelled) >= threshold) {
                            val forwards = if (layoutDirection == LayoutDirection.Rtl) {
                                travelled > 0
                            } else {
                                travelled < 0
                            }
                            if (forwards) onNextMonth() else onPreviousMonth()
                        }
                        travelled = 0f
                    },
                    onDragCancel = { travelled = 0f },
                    onHorizontalDrag = { _, delta -> travelled += delta },
                )
            }
    ) {
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
                                isPicked = day.date in picked,
                                onClick = { onSelect(day.date) },
                                onLongClick = { onLongSelect(day.date) },
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
 * What to do with the days a long press has gathered up.
 *
 * Sits in the bottom bar rather than a dialog so the calendar stays visible: the whole point is
 * seeing which days are about to change while choosing what to change them to.
 */
@Composable
private fun BulkEditBar(
    count: Int,
    shiftTypes: List<ShiftType>,
    onPick: (String) -> Unit,
    onResetToRotation: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.bulk_selected, count),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.bulk_cancel)) }
            }

            Text(
                text = stringResource(R.string.bulk_set_to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                shiftTypes.forEach { type ->
                    ShiftChoice(
                        type = type,
                        isSelected = false,
                        onClick = { onPick(type.id) },
                    )
                }
            }

            TextButton(onClick = onResetToRotation, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bulk_reset))
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DayCell(
    day: DayShift,
    isToday: Boolean,
    isSelected: Boolean,
    isPicked: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
        isPicked -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.onSurface
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val ringWidth = when {
        isPicked -> 3.5.dp
        isSelected -> 2.5.dp
        else -> 2.dp
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(ringWidth, ring, RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = content,
            )
            // The non-composable overload: a safe call on a @Composable one would be a conditional
            // composable invocation, which is more machinery than reading a string deserves.
            val abbreviation = shift?.displayAbbreviation(LocalContext.current).orEmpty()
            if (abbreviation.isNotEmpty()) {
                Text(
                    text = abbreviation,
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
    val context = LocalContext.current
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
                    text = type.displayName(context),
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
                // The name is whatever the user typed, so it carries its own direction into a
                // sentence that has one already. Isolated, an English name inside Hebrew stops
                // dragging the separator around it to the wrong end of the line.
                text = pattern
                    ?.let {
                        stringResource(
                            R.string.rotation_summary,
                            autoIsolate(it.name),
                            it.cycleLength,
                        )
                    }
                    ?: stringResource(R.string.rotation_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    if (pattern == null) R.string.action_set_up else R.string.action_change
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

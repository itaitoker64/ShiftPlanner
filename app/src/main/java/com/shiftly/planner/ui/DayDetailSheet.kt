package com.shiftly.planner.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shiftly.planner.R
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftType
import com.shiftly.planner.text.displayAbbreviation
import com.shiftly.planner.text.displayName
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DATE_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Takes a Context rather than being @Composable: it returns early for non-working shifts, and an
// early return out of a composable is more trouble than formatting a string is worth.
private fun ShiftType.timeRangeText(context: Context): String? {
    val spans = blocks.takeIf { it.isNotEmpty() } ?: return null
    val ranges = spans.joinToString(" · ") { block ->
        val from = LocalTime.of(block.startMinute / 60, block.startMinute % 60).format(TIME)
        val to = LocalTime.of(block.endMinute / 60, block.endMinute % 60).format(TIME)
        context.getString(R.string.time_range, from, to)
    }
    val hours = durationMinutes?.div(60) ?: return ranges
    return context.getString(R.string.time_range_total, ranges, hours)
}

/**
 * Tap a day to see what it is and change it.
 *
 * Changing a day writes an override rather than editing the rotation, so a one-off swap never
 * silently reshapes every future week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: LocalDate,
    schedule: Schedule,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onResetToPattern: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val current = schedule.shiftOn(date)
    val patternShift = schedule.pattern?.shiftTypeIdOn(date)?.let { schedule.typeOrNull(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = date.format(DATE_TITLE),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.size(4.dp))

            val name = current?.displayName(context) ?: stringResource(R.string.day_no_shift)
            val times = current?.timeRangeText(context)
            Text(
                text = times?.let { stringResource(R.string.day_shift_with_time, name, it) } ?: name,
                style = MaterialTheme.typography.bodyMedium,
            )

            schedule.pattern?.let { pattern ->
                Text(
                    text = stringResource(
                        R.string.day_cycle_position,
                        pattern.cycleDayOn(date) + 1,
                        pattern.cycleLength,
                        pattern.name,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(20.dp))

            Text(
                text = stringResource(R.string.day_change_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.day_change_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))

            // A scrolling Row rather than a LazyRow: a handful of choices does not justify the
            // lazy machinery, and scrolling still saves the labels from clipping on a narrow phone.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                schedule.shiftTypes.forEach { type ->
                    ShiftChoice(
                        type = type,
                        isSelected = type.id == current?.id,
                        onClick = { onPick(type.id) },
                    )
                }
            }

            if (schedule.isOverridden(date)) {
                Spacer(Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = patternShift
                            ?.let {
                                stringResource(R.string.day_rotation_says, it.displayName(context))
                            }
                            ?: stringResource(R.string.day_edited),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onResetToPattern) {
                        Text(stringResource(R.string.action_reset))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShiftChoice(type: ShiftType, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        val fill = Color(type.colorArgb.toInt())
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = type.displayAbbreviation().ifEmpty { "–" },
                color = textOn(fill),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = type.displayName(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(width = 20.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
        )
    }
}

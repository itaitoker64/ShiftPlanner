package com.shiftly.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DATE_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun ShiftType.timeRangeText(): String? {
    val start = startMinute ?: return null
    val end = endMinute ?: return null
    val from = LocalTime.of(start / 60, start % 60).format(TIME)
    val to = LocalTime.of(end / 60, end % 60).format(TIME)
    val hours = durationMinutes?.let { " · ${it / 60}h" } ?: ""
    return "$from – $to$hours"
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

            Text(
                text = buildString {
                    append(current?.name ?: "No shift")
                    current?.timeRangeText()?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            schedule.pattern?.let { pattern ->
                Text(
                    text = "Day ${pattern.cycleDayOn(date) + 1} of ${pattern.cycleLength} · ${pattern.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(20.dp))

            Text("Change this day", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(schedule.shiftTypes, key = { it.id }) { type ->
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
                        text = patternShift?.let { "Rotation says: ${it.name}" }
                            ?: "Edited away from the rotation",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onResetToPattern) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun ShiftChoice(type: ShiftType, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(type.colorArgb.toInt())),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = type.abbreviation.ifEmpty { "–" },
                color = if (type.isWorking) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = type.name,
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

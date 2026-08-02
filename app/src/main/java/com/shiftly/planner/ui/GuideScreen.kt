package com.shiftly.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shiftly.planner.R
import com.shiftly.planner.domain.DayShift
import com.shiftly.planner.domain.ShiftType
import java.time.LocalDate

/**
 * What everything in the app means, in one scrollable page.
 *
 * The calendar is dense with meaning that has nowhere else to be explained — a colour per shift, a
 * letter, a ring around today, a dot on days you changed by hand. Rather than describe those in
 * words, this page renders the real [DayCell] the calendar draws and labels it, so the legend
 * cannot drift out of step with the thing it explains.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onDone: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.guide_intro),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                GuideSection(stringResource(R.string.guide_repeats_title)) {
                    Paragraph(
                        stringResource(R.string.guide_repeats_1)
                    )
                    Paragraph(
                        stringResource(R.string.guide_repeats_2)
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_reading_title)) {
                    Paragraph(
                        stringResource(R.string.guide_reading_1)
                    )
                    Spacer(Modifier.size(12.dp))
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.DAY),
                        title = stringResource(R.string.guide_legend_working),
                        detail = stringResource(R.string.guide_legend_working_detail),
                    )
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.OFF),
                        title = stringResource(R.string.guide_legend_off),
                        detail = stringResource(R.string.guide_legend_off_detail),
                    )
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.NIGHT),
                        title = stringResource(R.string.guide_legend_today),
                        isToday = true,
                        detail = stringResource(R.string.guide_legend_today_detail),
                    )
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.EVENING, isOverridden = true),
                        title = stringResource(R.string.guide_legend_edited),
                        detail = stringResource(R.string.guide_legend_edited_detail),
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_totals_title)) {
                    Paragraph(
                        stringResource(R.string.guide_totals_1)
                    )
                    Paragraph(
                        stringResource(R.string.guide_totals_2)
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_one_day_title)) {
                    Paragraph(
                        stringResource(R.string.guide_one_day_1)
                    )
                    Paragraph(
                        stringResource(R.string.guide_one_day_2)
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_rotation_title)) {
                    Paragraph(
                        stringResource(R.string.guide_rotation_1)
                    )
                    Paragraph(
                        stringResource(R.string.guide_rotation_2)
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_offset_title)) {
                    Paragraph(
                        stringResource(R.string.guide_offset_1)
                    )
                    Paragraph(
                        stringResource(R.string.guide_offset_2)
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_widget_title)) {
                    Paragraph(
                        stringResource(R.string.guide_widget_1)
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_reminder_title)) {
                    Paragraph(
                        stringResource(R.string.guide_reminder_1)
                    )
                }
            }

            item {
                GuideSection(stringResource(R.string.guide_privacy_title)) {
                    Paragraph(
                        stringResource(R.string.guide_privacy_1)
                    )
                }
            }
        }
    }
}

private val EXAMPLE_DATE: LocalDate = LocalDate.of(2026, 1, 14)

@Composable
private fun GuideSection(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** A real calendar square beside what it means. */
@Composable
private fun LegendRow(
    day: DayShift,
    title: String,
    detail: String,
    isToday: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp)) {
            DayCell(day = day, isToday = isToday, isSelected = false)
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

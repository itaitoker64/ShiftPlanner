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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                title = { Text("How Shiftly works") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                    text = "Tell Shiftly your rotation once. It fills in every month, " +
                        "forwards and back, for as long as you work it.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                GuideSection("Your rotation repeats — that's the whole idea") {
                    Paragraph(
                        "Most shift work runs on a cycle: four on and four off, two-two-three, a " +
                            "week of days then a week of nights. Whatever yours is, it repeats."
                    )
                    Paragraph(
                        "So Shiftly only needs two things from you: what one full cycle looks " +
                            "like, and which real date you are on day 1 of it. From those it works " +
                            "out every other day by itself. There is nothing to fill in each week."
                    )
                }
            }

            item {
                GuideSection("Reading the calendar") {
                    Paragraph(
                        "Every day is a coloured square. The colour and the letter tell you which " +
                            "shift it is."
                    )
                    Spacer(Modifier.size(12.dp))
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.DAY),
                        title = "A working day",
                        detail = "Filled in the colour of that shift, with its letter underneath. " +
                            "D for day, N for night, E for evening, 24 for a full tour.",
                    )
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.OFF),
                        title = "A day off",
                        detail = "Pale and unlettered, so a run of days off is obvious at a glance.",
                    )
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.NIGHT),
                        title = "Today",
                        isToday = true,
                        detail = "Ringed, with the date in bold. The Today button in the top bar " +
                            "brings you back to it from any month.",
                    )
                    LegendRow(
                        day = DayShift(EXAMPLE_DATE, ShiftType.EVENING, isOverridden = true),
                        title = "A day you changed yourself",
                        detail = "The red dot means this day no longer follows the rotation " +
                            "because you set it by hand.",
                    )
                }
            }

            item {
                GuideSection("The three numbers at the top") {
                    Paragraph(
                        "Shifts is how many working days fall in the month you are looking at. " +
                            "Days off is the rest of the month. Hours adds up the length of every " +
                            "shift in it — useful for checking a payslip."
                    )
                    Paragraph(
                        "They follow whichever month is on screen, so scroll forward to see what " +
                            "next month holds."
                    )
                }
            }

            item {
                GuideSection("Changing one single day") {
                    Paragraph(
                        "Tap any day and pick a different shift. Swapped a shift with someone, " +
                            "took a day's leave, got called in — this is for that."
                    )
                    Paragraph(
                        "Only that one day changes. Your rotation is left alone, so the rest of " +
                            "the year does not move. The day gets a red dot to show it was you, " +
                            "not the rotation. Tap it again and press Reset to put it back."
                    )
                }
            }

            item {
                GuideSection("Changing your whole rotation") {
                    Paragraph(
                        "The bar at the bottom of the calendar shows the rotation you are on. Tap " +
                            "it, or the pencil in the top bar, to pick a different one or build " +
                            "your own."
                    )
                    Paragraph(
                        "Changing your rotation clears the single days you had changed by hand. " +
                            "Those were corrections to the old rotation and would be wrong " +
                            "against the new one."
                    )
                }
            }

            item {
                GuideSection("Everything is off by a day or two") {
                    Paragraph(
                        "This is the usual thing to go wrong, and it is not worth rebuilding your " +
                            "rotation over. It means the start date you gave is out — the shape " +
                            "of your cycle is right, it is just sitting on the wrong dates."
                    )
                    Paragraph(
                        "Open your rotation from the bar at the bottom of the calendar. At the " +
                            "top you will find buttons to move the whole rotation a day earlier " +
                            "or a day later. Nudge it until the calendar matches the shifts you " +
                            "actually worked this week. Days you changed by hand stay put."
                    )
                }
            }

            item {
                GuideSection("On your homescreen") {
                    Paragraph(
                        "Shiftly comes with a widget showing the shift you are on today and when " +
                            "you are next in. Long-press an empty part of your homescreen, choose " +
                            "Widgets, and find Shiftly in the list."
                    )
                }
            }

            item {
                GuideSection("The evening before") {
                    Paragraph(
                        "If you allow notifications, Shiftly reminds you the evening before any " +
                            "working day. Nothing on days off."
                    )
                }
            }

            item {
                GuideSection("Your schedule stays on your phone") {
                    Paragraph(
                        "There is no account and no sign-in, and your rotation is not sent " +
                            "anywhere. It is stored on this phone only, which also means " +
                            "uninstalling the app deletes it."
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

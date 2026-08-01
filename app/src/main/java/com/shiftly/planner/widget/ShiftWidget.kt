package com.shiftly.planner.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.shiftly.planner.MainActivity
import com.shiftly.planner.data.ScheduleRepository
import com.shiftly.planner.domain.Schedule
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val NEXT_SHIFT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Homescreen widget: what you're on today, and when you're next in.
 *
 * The whole point of a rota app is answering that question without opening anything, so the widget
 * is a first-class surface rather than an extra.
 */
class ShiftWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val schedule = ScheduleRepository(context).schedule.first()
        provideContent {
            GlanceTheme {
                WidgetContent(schedule)
            }
        }
    }
}

@Composable
private fun WidgetContent(schedule: Schedule) {
    val today = LocalDate.now()
    val todayShift = schedule.shiftOn(today)
    val working = todayShift?.isWorking == true

    val background = when {
        todayShift == null -> Color(0xFF9E9E9E)
        working -> Color(todayShift.colorArgb.toInt())
        else -> Color(0xFF43A047)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(background, background))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        val white = ColorProvider(Color.White, Color.White)

        Text(
            text = "Today",
            style = TextStyle(color = white, fontSize = 12.sp),
        )

        Text(
            text = todayShift?.name ?: "No rotation",
            style = TextStyle(color = white, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )

        if (working) {
            // Already in today — the useful second line is the hours, not the next shift.
            todayShift?.let { shift ->
                val start = shift.startMinute
                val end = shift.endMinute
                if (start != null && end != null) {
                    Text(
                        text = "%02d:%02d – %02d:%02d".format(
                            start / 60, start % 60, end / 60, end % 60,
                        ),
                        style = TextStyle(color = white, fontSize = 13.sp),
                    )
                }
            }
        } else {
            val next = schedule.nextWorkingDay(today.plusDays(1))
            Text(
                text = next?.let {
                    val days = it.date.toEpochDay() - today.toEpochDay()
                    when (days) {
                        1L -> "Back in tomorrow"
                        else -> "Back in ${it.date.format(NEXT_SHIFT_DATE)} · ${days}d"
                    }
                } ?: "Nothing scheduled",
                style = TextStyle(color = white, fontSize = 13.sp),
            )
        }
    }
}

class ShiftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShiftWidget()
}

/** Called after every schedule mutation so the homescreen never shows a stale shift. */
object ShiftWidgetUpdater {
    suspend fun requestUpdate(context: Context) {
        runCatching { ShiftWidget().updateAll(context) }
    }
}

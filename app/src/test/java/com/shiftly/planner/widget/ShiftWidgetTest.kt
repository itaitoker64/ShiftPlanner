package com.shiftly.planner.widget

import android.app.Application
import android.content.Context
import androidx.glance.testing.unit.hasText
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import com.shiftly.planner.domain.Presets
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftPattern
import com.shiftly.planner.domain.ShiftType.Companion.ID_NIGHT
import com.shiftly.planner.domain.ShiftType.Companion.ID_OFF
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The homescreen widget.
 *
 * This is the surface the app is really for — "am I in today, and when am I next in?" answered
 * without opening anything. It had no coverage at all, which for the headline feature is the worst
 * place to have none.
 *
 * Glance composes to RemoteViews rather than to a View tree, so these use Glance's own unit-test
 * harness rather than the Compose one used for the screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShiftWidgetTest {

    private val today = LocalDate.of(2026, 6, 15)

    // Glance does not provide LocalContext to its test harness, so the widget takes one.
    private val context: Context = ApplicationProvider.getApplicationContext<Application>()

    /** A 4-on-4-off rotation positioned so that [today] sits at [cycleIndex]. */
    private fun rotationAt(cycleIndex: Int) = Schedule(
        pattern = ShiftPattern(
            id = "test",
            name = "4 on, 4 off",
            cycle = Presets.byKey("4on4off_days")!!.cycle,
            anchorEpochDay = today.toEpochDay() - cycleIndex,
        ),
    )

    @Test
    fun `shows the shift and its hours when in today`() = runGlanceAppWidgetUnitTest {
        provideComposable { WidgetContent(context, rotationAt(cycleIndex = 0), today) }

        onNode(hasText("Today")).assertExists()
        onNode(hasText("Day")).assertExists()
        // In today, the useful second line is the hours, not a countdown.
        onNode(hasText("07:00 – 19:00")).assertExists()
    }

    @Test
    fun `counts down to the next shift when off today`() = runGlanceAppWidgetUnitTest {
        // Cycle index 5 is the second of four days off, so the next shift is three days out.
        provideComposable { WidgetContent(context, rotationAt(cycleIndex = 5), today) }

        onNode(hasText("Off")).assertExists()
        onNode(hasText("Back in Thu 18 Jun · 3d")).assertExists()
    }

    @Test
    fun `says tomorrow rather than counting one day`() = runGlanceAppWidgetUnitTest {
        // Cycle index 7 is the last day off; the rotation starts again tomorrow.
        provideComposable { WidgetContent(context, rotationAt(cycleIndex = 7), today) }

        onNode(hasText("Back in tomorrow")).assertExists()
    }

    @Test
    fun `an override is reflected on the widget`() = runGlanceAppWidgetUnitTest {
        // Today is a Day shift in the rotation, swapped to a Night.
        val schedule = rotationAt(cycleIndex = 0).withOverride(today, ID_NIGHT)
        provideComposable { WidgetContent(context, schedule, today) }

        onNode(hasText("Night")).assertExists()
        onNode(hasText("19:00 – 07:00")).assertExists()
    }

    @Test
    fun `prompts when no rotation has been set up`() = runGlanceAppWidgetUnitTest {
        provideComposable { WidgetContent(context, Schedule(), today) }

        onNode(hasText("No rotation")).assertExists()
    }

    @Test
    fun `says nothing is scheduled when the rotation never works`() = runGlanceAppWidgetUnitTest {
        val schedule = Schedule(
            pattern = ShiftPattern("test", "All off", listOf(ID_OFF), today.toEpochDay()),
        )
        provideComposable { WidgetContent(context, schedule, today) }

        onNode(hasText("Nothing scheduled")).assertExists()
    }
}

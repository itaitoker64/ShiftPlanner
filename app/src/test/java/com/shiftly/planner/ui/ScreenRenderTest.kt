package com.shiftly.planner.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.shiftly.planner.domain.Presets
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftPattern
import com.shiftly.planner.domain.ShiftType.Companion.ID_NIGHT
import com.shiftly.planner.ui.theme.ShiftlyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Composes the screens on the JVM.
 *
 * These do not prove the app looks right — only a device can show that. What they do prove is that
 * each screen gets through composition without throwing, which is the failure mode that matters for
 * code that has never been rendered. A missing resource, a bad `Icons.` reference or an
 * out-of-bounds cycle index all surface here rather than as a crash on someone's phone.
 */
// Deliberately the stock Application rather than ShiftlyApplication. ShiftlyApplication.onCreate
// arms the reminder through WorkManager.getInstance(), which on a device is already initialised by
// the androidx.startup content provider — providers are installed before Application.onCreate.
// Robolectric creates them afterwards, so the real Application throws here for a reason that does
// not exist on a phone. These tests are about the screens, not about startup.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun viewModel() = ScheduleViewModel(
        ApplicationProvider.getApplicationContext<Application>()
    )

    private val anchor = LocalDate.of(2026, 1, 1)

    /**
     * The vertically scrolling list on whichever screen is under test — the preset picker, or the
     * guide. Matching on "has a scroll action" is not enough: both screens hold horizontally
     * scrolling strips too, so only the vertical axis identifies the list itself.
     */
    private val verticalList = SemanticsMatcher.keyIsDefined(
        SemanticsProperties.VerticalScrollAxisRange
    )

    private fun scheduleWith(presetKey: String) = Schedule(
        pattern = ShiftPattern(
            id = "test",
            name = Presets.byKey(presetKey)!!.name,
            cycle = Presets.byKey(presetKey)!!.cycle,
            anchorEpochDay = anchor.toEpochDay(),
        ),
    )

    @Test
    fun `setup screen composes and offers both ways to build a rotation`() {
        compose.setContent {
            ShiftlyTheme { SetupScreen(viewModel = viewModel(), onDone = {}) }
        }

        compose.onNodeWithText("Your rotation").assertIsDisplayed()
        compose.onNodeWithText("Common rotations").assertIsDisplayed()
        compose.onNodeWithText("Build my own").assertIsDisplayed()
    }

    @Test
    fun `every preset is reachable by scrolling the picker`() {
        compose.setContent {
            ShiftlyTheme { SetupScreen(viewModel = viewModel(), onDone = {}) }
        }

        // The picker is a LazyColumn, so off-screen presets are not composed until scrolled to.
        // Walking the whole list is what proves none of them is unreachable.
        Presets.all.forEach { preset ->
            compose.onNode(verticalList).performScrollToNode(hasText(preset.name))
            compose.onNodeWithText(preset.name).assertExists()
        }
    }

    @Test
    fun `choosing a preset reveals the confirm button`() {
        compose.setContent {
            ShiftlyTheme { SetupScreen(viewModel = viewModel(), onDone = {}) }
        }

        val first = Presets.all.first()
        compose.onNodeWithText(first.name).performClick()
        compose.waitForIdle()

        compose.onNode(verticalList).performScrollToNode(hasText("Use this rotation"))
        compose.onNodeWithText("Use this rotation").assertExists()
    }

    @Test
    fun `switching to the custom builder composes it`() {
        compose.setContent {
            ShiftlyTheme { SetupScreen(viewModel = viewModel(), onDone = {}) }
        }

        compose.onNodeWithText("Build my own").performClick()
        compose.waitForIdle()
        // Reaching here without an exception means the builder composed.
        compose.onNodeWithText("Build my own").assertIsDisplayed()
    }

    @Test
    fun `calendar screen composes with no rotation set up`() {
        compose.setContent {
            ShiftlyTheme { CalendarScreen(viewModel = viewModel(), onEditPattern = {}) }
        }

        compose.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun `calendar screen offers a way into the guide`() {
        // Passing "today" rather than letting the cell read the clock keeps the render fixed.
        compose.setContent {
            ShiftlyTheme {
                CalendarScreen(
                    viewModel = viewModel(),
                    onEditPattern = {},
                    today = anchor,
                )
            }
        }

        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithContentDescription("How this app works").assertExists()
    }

    @Test
    fun `setup screen offers a way into the guide`() {
        // Asserted on the top-bar icon rather than the first-run intro's link: the intro only
        // shows while no rotation is stored, and these tests share one DataStore file, so which
        // of the two states this screen composes in is not this test's to decide.
        compose.setContent {
            ShiftlyTheme { SetupScreen(viewModel = viewModel(), onDone = {}) }
        }

        compose.onNodeWithContentDescription("How this app works").assertExists()
    }

    @Test
    fun `guide screen composes and explains the calendar`() {
        compose.setContent { ShiftlyTheme { GuideScreen(onDone = {}) } }

        // Only the top bar is asserted here: the sections live in a LazyColumn, so which of them
        // happen to be composed depends on the screen height. The next test walks all of them.
        compose.onNodeWithText("How Shiftly works").assertIsDisplayed()
    }

    @Test
    fun `every guide section is reachable by scrolling`() {
        compose.setContent { ShiftlyTheme { GuideScreen(onDone = {}) } }

        // The guide is a LazyColumn: the later sections are not composed until scrolled to, and an
        // unreachable section is the same as a missing one.
        listOf(
            "Your rotation repeats — that's the whole idea",
            "Reading the calendar",
            "The three numbers at the top",
            "Changing one single day",
            "Changing your whole rotation",
            "Everything is off by a day or two",
            "On your homescreen",
            "The evening before",
            "Your schedule stays on your phone",
        ).forEach { heading ->
            compose.onNode(verticalList).performScrollToNode(hasText(heading))
            compose.onNodeWithText(heading).assertExists()
        }
    }

    @Test
    fun `day detail sheet shows the shift and its position in the cycle`() {
        val schedule = scheduleWith("4on4off_days")

        compose.setContent {
            ShiftlyTheme {
                DayDetailSheet(
                    date = anchor,
                    schedule = schedule,
                    onDismiss = {},
                    onPick = {},
                    onResetToPattern = {},
                )
            }
        }

        // Jan 1 2026 is the anchor, so it is day one of the eight-day cycle and a Day shift.
        compose.onNodeWithText("Day 1 of 8 · 4 on, 4 off").assertIsDisplayed()
        compose.onNodeWithText("Change this day").assertIsDisplayed()
    }

    @Test
    fun `day detail sheet offers a reset once the day is overridden`() {
        val schedule = scheduleWith("4on4off_days").withOverride(anchor, ID_NIGHT)

        compose.setContent {
            ShiftlyTheme {
                DayDetailSheet(
                    date = anchor,
                    schedule = schedule,
                    onDismiss = {},
                    onPick = {},
                    onResetToPattern = {},
                )
            }
        }

        // The sheet opens partially expanded, so the reset row exists below the fold.
        compose.onNodeWithText("Rotation says: Day").assertExists()
        compose.onNodeWithText("Reset").assertExists()
    }
}

package com.shiftly.planner.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
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

        // Scrolled to first, the way a person reaches it: the picker sits below the rotation's
        // nudge card and the links off this screen, so on a short screen even the first preset
        // starts below the fold.
        val first = Presets.all.first()
        compose.onNode(verticalList).performScrollToNode(hasText(first.name))
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
    fun `the first-run walkthrough composes and can be skipped`() {
        compose.setContent {
            ShiftlyTheme {
                OnboardingScreen(
                    viewModel = viewModel(),
                    onFinished = {},
                    today = anchor,
                )
            }
        }

        // Step one, with the way out for anyone who would rather do it themselves.
        compose.onNodeWithText("Let's build your schedule").assertIsDisplayed()
        compose.onNodeWithText("Skip and set it up myself").assertExists()
    }

    @Test
    fun `the walkthrough will not advance past the welcome without a rotation`() {
        compose.setContent {
            ShiftlyTheme {
                OnboardingScreen(viewModel = viewModel(), onFinished = {}, today = anchor)
            }
        }

        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        // Now on the rotation step, where Next stays disabled until one is picked: every later
        // step is about a rotation that does not exist yet.
        compose.onNodeWithText("Which rotation do you work?").assertExists()
        compose.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun `picking a rotation reaches the preview of the next fortnight`() {
        compose.setContent {
            ShiftlyTheme {
                OnboardingScreen(viewModel = viewModel(), onFinished = {}, today = anchor)
            }
        }

        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(Presets.all.first().name).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("When does your cycle start?").assertExists()
        compose.onNodeWithText("The next two weeks").assertExists()
    }

    @Test
    fun `shift times screen composes and lists the built-in shifts`() {
        compose.setContent {
            ShiftlyTheme { ShiftTypesScreen(viewModel = viewModel(), onDone = {}) }
        }

        compose.onNodeWithText("Shift times").assertIsDisplayed()
        // The hours the app shipped with, which this screen exists to let people change.
        compose.onNodeWithText("07:00 – 19:00 · 12h").assertExists()
    }

    @Test
    fun `calendar sync screen composes and asks for permission first`() {
        compose.setContent {
            ShiftlyTheme { CalendarSyncScreen(viewModel = viewModel(), onDone = {}) }
        }

        compose.onNodeWithText("Add to your calendar").assertIsDisplayed()
    }

    @Test
    fun `settings screen composes and offers a language`() {
        compose.setContent { ShiftlyTheme { SettingsScreen(onDone = {}) } }

        compose.onNodeWithText("Settings").assertIsDisplayed()
        // Named in their own scripts on purpose: someone stuck in a language they cannot read has
        // to be able to find their way out.
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("עברית").assertExists()
    }

    @Test
    fun `holding a day starts a selection the bar can act on`() {
        compose.setContent {
            ShiftlyTheme {
                CalendarScreen(viewModel = viewModel(), onEditPattern = {}, today = anchor)
            }
        }

        // Nothing selected: no bar.
        compose.onNodeWithText("Put them back on the rotation").assertDoesNotExist()

        // Targeted by "can be long-pressed" rather than by the date: the month summary shows
        // counts that collide with day numbers, and a day number is not a unique piece of text on
        // this screen. Nothing else on the calendar takes a long press, so this is the day grid.
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .onFirst()
            .performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("Selected: 1").assertExists()
        compose.onNodeWithText("Put them back on the rotation").assertExists()
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
            "Changing a run of days at once",
            "Moving through the months",
            "Shifts that are split across the day",
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

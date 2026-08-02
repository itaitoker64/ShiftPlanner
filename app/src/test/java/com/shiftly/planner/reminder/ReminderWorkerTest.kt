package com.shiftly.planner.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.shiftly.planner.data.ScheduleRepository
import com.shiftly.planner.domain.Presets
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftPattern
import com.shiftly.planner.domain.ShiftType.Companion.ID_OFF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The evening-before reminder.
 *
 * The contract worth protecting is the *silence*: a notification that fires every night, including
 * on days off, gets the channel muted within a week, and a muted channel is not recoverable. That
 * is behaviour no one would notice breaking until the reviews arrived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderWorkerTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Anchors a 4-on-4-off rotation so that tomorrow lands on [cycleIndex]. */
    private fun seedRotation(cycleIndex: Int) = runBlocking {
        val cycle = Presets.byKey("4on4off_days")!!.cycle
        val tomorrow = LocalDate.now().plusDays(1)
        ScheduleRepository(context).save(
            Schedule(
                pattern = ShiftPattern(
                    id = "test",
                    name = "4 on, 4 off",
                    cycle = cycle,
                    anchorEpochDay = tomorrow.toEpochDay() - cycleIndex,
                ),
            )
        )
    }

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork()
    }

    private fun postedNotifications() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun `notifies when tomorrow is a working day`() {
        // Cycle index 0 of 4-on-4-off is the first Day shift.
        seedRotation(cycleIndex = 0)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        val posted = postedNotifications()
        assertEquals(1, posted.size)
        val text = shadowOf(posted.single()).contentText.toString()
        assertTrue(text, text.startsWith("Day"))
        // The hours are the point of the notification, not decoration.
        assertTrue(text, text.contains("07:00 – 19:00"))
    }

    @Test
    fun `stays silent when tomorrow is a day off`() {
        // Cycle index 4 of 4-on-4-off is the first of the four days off.
        seedRotation(cycleIndex = 4)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `stays silent when no rotation has been set up`() {
        runBlocking { ScheduleRepository(context).save(Schedule()) }

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `stays silent when an override turns tomorrow into a day off`() {
        seedRotation(cycleIndex = 0)
        runBlocking {
            ScheduleRepository(context).update { it.withOverride(LocalDate.now().plusDays(1), ID_OFF) }
        }

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `stays silent without notification permission`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        seedRotation(cycleIndex = 0)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(postedNotifications().isEmpty())
    }
}

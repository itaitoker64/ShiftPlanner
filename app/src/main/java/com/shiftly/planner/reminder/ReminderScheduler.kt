package com.shiftly.planner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Arranges the evening-before shift reminder.
 *
 * A daily periodic worker is used rather than an exact alarm: shift reminders are a convenience,
 * not an alarm clock, and exact alarms need a special permission that Play scrutinises.
 */
object ReminderScheduler {

    private const val WORK_NAME = "shift_reminder_check"

    /** Local time the reminder fires the evening before a shift. */
    val REMINDER_TIME: LocalTime = LocalTime.of(19, 0)

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofHours(24))
            .setInitialDelay(delayUntilNextReminderTime())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // KEEP, so a cold start doesn't reset the timer every time the app opens.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun delayUntilNextReminderTime(): Duration {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), REMINDER_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}

/** WorkManager schedules do not survive a reboot on all OEM builds; re-arm on boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.ensureScheduled(context)
        }
    }
}

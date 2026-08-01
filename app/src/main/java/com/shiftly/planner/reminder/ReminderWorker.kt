package com.shiftly.planner.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shiftly.planner.MainActivity
import com.shiftly.planner.R
import com.shiftly.planner.data.ScheduleRepository
import com.shiftly.planner.domain.ShiftType
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Runs each evening. If tomorrow is a working day, posts "you're in tomorrow" with the hours.
 *
 * Silent on off-days by design — a notification that fires every single night gets the app muted
 * within a week, and a muted notification channel is not recoverable.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (!hasNotificationPermission(context)) return Result.success()

        val schedule = ScheduleRepository(context).schedule.first()
        val tomorrow = LocalDate.now().plusDays(1)
        val shift = schedule.shiftOn(tomorrow)

        if (shift == null || !shift.isWorking) return Result.success()

        notify(context, shift)
        return Result.success()
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, shift: ShiftType) {
        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            }
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val hours = shift.startMinute?.let { start ->
            shift.endMinute?.let { end ->
                " · %02d:%02d – %02d:%02d".format(start / 60, start % 60, end / 60, end % 60)
            }
        }.orEmpty()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText("${shift.name}$hours")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "shift_reminders"
        const val NOTIFICATION_ID = 1001
    }
}

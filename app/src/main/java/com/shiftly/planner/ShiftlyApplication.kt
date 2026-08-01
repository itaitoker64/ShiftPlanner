package com.shiftly.planner

import android.app.Application
import com.shiftly.planner.reminder.ReminderScheduler

class ShiftlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Re-arm the daily reminder check on every cold start. The work is unique and periodic,
        // so repeated calls are cheap and it self-heals if the OS dropped the schedule.
        ReminderScheduler.ensureScheduled(this)
    }
}

package com.shiftly.planner.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.shiftly.planner.R
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.text.displayName
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** A calendar on the phone that Shiftly is allowed to write into. */
data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
)

/**
 * Writes shifts into a calendar on the phone.
 *
 * A rotation runs forever, so there is nothing to "export" once and be done with — this writes a
 * rolling window and keeps the ids of everything it wrote. Re-syncing deletes those and writes the
 * window again, which is what keeps the calendar honest after the rota changes or a day is
 * corrected by hand.
 *
 * Only events this app created are ever deleted, because only those ids are recorded. Nothing else
 * in the user's calendar is touched, and a shift removed from the rota disappears rather than
 * lingering as a stale event.
 */
object CalendarSync {

    /**
     * Calendars the user could reasonably sync into.
     *
     * Filtered to those the provider says we can add events to; a read-only subscribed calendar
     * (holidays, a shared team rota) would accept the insert and silently drop it.
     */
    fun writableCalendars(context: Context): List<DeviceCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DeviceCalendar(
                            id = cursor.getLong(0),
                            displayName = cursor.getString(1).orEmpty(),
                            accountName = cursor.getString(2).orEmpty(),
                        )
                    )
                }
            }
        }.orEmpty()
    }

    /**
     * Deletes the events listed in [previouslyWritten] and writes [schedule]'s working days from
     * [from] for [months], returning the ids of what it wrote.
     *
     * Deleting first is what makes this re-runnable: without it a second sync would double every
     * shift already in the calendar.
     */
    fun sync(
        context: Context,
        schedule: Schedule,
        calendarId: Long,
        previouslyWritten: Set<Long>,
        from: LocalDate = LocalDate.now(),
        months: Long = 12,
    ): Set<Long> {
        removeAll(context, previouslyWritten)

        val zone = ZoneId.systemDefault()
        val timeZoneId = TimeZone.getDefault().id
        val resolver = context.contentResolver
        val until = from.plusMonths(months)
        val written = mutableSetOf<Long>()

        var date = from
        while (date.isBefore(until)) {
            val shift = schedule.shiftOn(date)
            if (shift != null && shift.isWorking) {
                val title = shift.displayName(context)
                val description = context.getString(R.string.calendar_event_description)
                val blocks = shift.blocks

                if (blocks.isEmpty()) {
                    // A working shift with no hours set: all-day, which the provider requires to
                    // be midnight UTC rather than midnight local.
                    val midnightUtc = date.atStartOfDay(ZoneId.of("UTC"))
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, title)
                        put(CalendarContract.Events.DESCRIPTION, description)
                        put(CalendarContract.Events.HAS_ALARM, 0)
                        put(CalendarContract.Events.ALL_DAY, 1)
                        put(CalendarContract.Events.DTSTART, midnightUtc.toInstant().toEpochMilli())
                        put(
                            CalendarContract.Events.DTEND,
                            midnightUtc.plusDays(1).toInstant().toEpochMilli(),
                        )
                        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                    }
                    insert(resolver, values)?.let(written::add)
                } else {
                    // One event per block, so a split day — four on, eight off, four on — shows as
                    // the two separate stretches it actually is rather than one long smear.
                    blocks.forEach { block ->
                        val startsAt = date.atStartOfDay(zone).plusMinutes(block.startMinute.toLong())
                        val values = ContentValues().apply {
                            put(CalendarContract.Events.CALENDAR_ID, calendarId)
                            put(CalendarContract.Events.TITLE, title)
                            put(CalendarContract.Events.DESCRIPTION, description)
                            put(CalendarContract.Events.HAS_ALARM, 0)
                            // Built by adding minutes to a zoned midnight rather than to an
                            // instant, so a block the night the clocks change still starts at the
                            // time on the rota.
                            put(CalendarContract.Events.DTSTART, startsAt.toInstant().toEpochMilli())
                            put(
                                CalendarContract.Events.DTEND,
                                startsAt.plusMinutes(block.lengthMinutes.toLong())
                                    .toInstant()
                                    .toEpochMilli(),
                            )
                            put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
                        }
                        insert(resolver, values)?.let(written::add)
                    }
                }
            }
            date = date.plusDays(1)
        }

        return written
    }

    private fun insert(
        resolver: android.content.ContentResolver,
        values: ContentValues,
    ): Long? = runCatching { resolver.insert(CalendarContract.Events.CONTENT_URI, values) }
        .getOrNull()
        ?.let(ContentUris::parseId)

    /** Deletes every event this app wrote, leaving the rest of the calendar alone. */
    fun removeAll(context: Context, eventIds: Set<Long>) {
        eventIds.forEach { id ->
            runCatching {
                context.contentResolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                    null,
                    null,
                )
            }
        }
    }
}

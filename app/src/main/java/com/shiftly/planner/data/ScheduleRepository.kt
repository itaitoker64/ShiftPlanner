package com.shiftly.planner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shiftly.planner.domain.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "schedule")

/**
 * Single source of truth for the user's schedule.
 *
 * The whole schedule is small — one pattern, a handful of shift types, and a sparse override map —
 * so it is stored as a single JSON blob rather than in a relational database. This keeps the app
 * fully offline with no migration burden, and lets the widget read the same state cheaply.
 */
class ScheduleRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val schedule: Flow<Schedule> = context.scheduleDataStore.data.map { prefs ->
        prefs[KEY_SCHEDULE]?.let { stored ->
            // A corrupt or forward-version blob must not brick the app; fall back to empty.
            runCatching { json.decodeFromString<Schedule>(stored) }.getOrNull()
        }?.withBuiltinTypes() ?: Schedule()
    }

    /**
     * Whether the first-run walkthrough has been finished or skipped.
     *
     * A separate key rather than a field on [Schedule]: it describes this install, not the rota,
     * so a corrupt schedule blob — which decodes to an empty [Schedule] — does not also lose the
     * fact that the walkthrough has already been seen.
     *
     * Absent means a fresh install, so the walkthrough runs. The view model holds *true* until the
     * first read arrives, so a launch with a schedule already stored does not flash the
     * walkthrough on its way to the calendar.
     */
    val onboardingComplete: Flow<Boolean> =
        context.scheduleDataStore.data.map { prefs -> prefs[KEY_ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete() {
        context.scheduleDataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETE] = true }
    }

    /**
     * The calendar the user last synced into, and the ids of the events written there.
     *
     * The ids are the whole reason a re-sync is safe: they are the only events this app will ever
     * delete, so nothing else in the user's calendar can be touched by a rewrite.
     */
    val syncedCalendarId: Flow<Long?> =
        context.scheduleDataStore.data.map { prefs -> prefs[KEY_CALENDAR_ID] }

    val exportedEventIds: Flow<Set<Long>> = context.scheduleDataStore.data.map { prefs ->
        prefs[KEY_EXPORTED_EVENT_IDS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setCalendarSync(calendarId: Long?, eventIds: Set<Long>) {
        context.scheduleDataStore.edit { prefs ->
            if (calendarId == null) prefs.remove(KEY_CALENDAR_ID) else prefs[KEY_CALENDAR_ID] = calendarId
            prefs[KEY_EXPORTED_EVENT_IDS] = eventIds.map(Long::toString).toSet()
        }
    }

    suspend fun save(schedule: Schedule) {
        context.scheduleDataStore.edit { prefs ->
            prefs[KEY_SCHEDULE] = json.encodeToString(schedule)
        }
    }

    /** Read-modify-write; used by every mutation in the UI layer. */
    suspend fun update(transform: (Schedule) -> Schedule) {
        context.scheduleDataStore.edit { prefs ->
            val current = prefs[KEY_SCHEDULE]
                ?.let { runCatching { json.decodeFromString<Schedule>(it) }.getOrNull() }
                ?.withBuiltinTypes()
                ?: Schedule()
            prefs[KEY_SCHEDULE] = json.encodeToString(transform(current))
        }
    }

    private companion object {
        val KEY_SCHEDULE = stringPreferencesKey("schedule_json")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_CALENDAR_ID = longPreferencesKey("calendar_id")
        val KEY_EXPORTED_EVENT_IDS = stringSetPreferencesKey("exported_event_ids")
    }
}

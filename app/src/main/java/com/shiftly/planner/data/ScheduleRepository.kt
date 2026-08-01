package com.shiftly.planner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        } ?: Schedule()
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
                ?: Schedule()
            prefs[KEY_SCHEDULE] = json.encodeToString(transform(current))
        }
    }

    private companion object {
        val KEY_SCHEDULE = stringPreferencesKey("schedule_json")
    }
}

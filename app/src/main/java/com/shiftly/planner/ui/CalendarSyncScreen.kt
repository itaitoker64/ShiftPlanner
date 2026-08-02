package com.shiftly.planner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.R
import com.shiftly.planner.calendar.CalendarSync
import com.shiftly.planner.calendar.DeviceCalendar

private val HORIZONS = listOf(3L, 6L, 12L)

private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

/**
 * Puts the rota into a calendar on the phone.
 *
 * A rotation has no end, so this writes a window rather than everything, and remembers what it
 * wrote. Syncing again replaces that window — which is the only way the calendar stays right after
 * the rota changes, and the only way this can be run twice without doubling every shift.
 *
 * Reading the calendar list needs the permission too, not just writing: without it there is nothing
 * to offer the user to choose between.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncScreen(
    viewModel: ScheduleViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val status by viewModel.calendarStatus.collectAsStateWithLifecycle()
    val syncedCalendarId by viewModel.syncedCalendarId.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(
            CALENDAR_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var calendars by remember { mutableStateOf(emptyList<DeviceCalendar>()) }
    var chosen by remember { mutableStateOf<Long?>(null) }
    var months by remember { mutableStateOf(12L) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }

    // Re-read whenever permission arrives; the list is empty until it does.
    LaunchedEffect(granted) {
        if (granted) {
            calendars = runCatching { CalendarSync.writableCalendars(context) }.getOrDefault(emptyList())
            chosen = chosen ?: syncedCalendarId ?: calendars.firstOrNull()?.id
        }
    }

    LaunchedEffect(Unit) { viewModel.clearCalendarStatus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.calendar_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (schedule.pattern == null) {
                item {
                    Text(
                        text = stringResource(R.string.calendar_needs_rotation),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                return@LazyColumn
            }

            if (!granted) {
                item {
                    Text(
                        text = stringResource(R.string.calendar_permission_why),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = { launcher.launch(CALENDAR_PERMISSIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.calendar_allow)) }
                }
                return@LazyColumn
            }

            if (calendars.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.calendar_none_writable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@LazyColumn
            }

            item {
                Text(
                    text = stringResource(R.string.calendar_choose),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            items(calendars, key = { it.id }) { calendar ->
                CalendarRow(
                    calendar = calendar,
                    selected = calendar.id == chosen,
                    onClick = { chosen = calendar.id },
                )
            }

            item {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.calendar_how_far),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HORIZONS.forEach { option ->
                        FilterChip(
                            selected = option == months,
                            onClick = { months = option },
                            label = {
                                Text(stringResource(R.string.calendar_months, option.toInt()))
                            },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { chosen?.let { viewModel.syncToCalendar(it, months) } },
                    enabled = chosen != null && status !is CalendarStatus.Working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (syncedCalendarId == null) R.string.calendar_add
                            else R.string.calendar_update
                        )
                    )
                }

                when (val current = status) {
                    is CalendarStatus.Working -> {
                        Spacer(Modifier.size(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(R.string.calendar_working))
                        }
                    }

                    is CalendarStatus.Synced -> {
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.calendar_done, current.eventCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is CalendarStatus.Removed -> {
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.calendar_removed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    CalendarStatus.Idle -> Unit
                }

                if (syncedCalendarId != null) {
                    TextButton(
                        onClick = viewModel::removeFromCalendar,
                        enabled = status !is CalendarStatus.Working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.calendar_remove)) }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.calendar_rerun_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalendarRow(calendar: DeviceCalendar, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (calendar.accountName.isNotBlank() &&
                    calendar.accountName != calendar.displayName
                ) {
                    Text(
                        text = calendar.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

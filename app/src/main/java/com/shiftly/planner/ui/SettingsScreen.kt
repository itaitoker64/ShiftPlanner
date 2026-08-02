package com.shiftly.planner.ui

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.shiftly.planner.R
import com.shiftly.planner.text.AppLanguage

/**
 * Everything that is a setting rather than a schedule.
 *
 * The language picker is the reason this screen exists: someone wanting the app in Hebrew on an
 * English phone had no way to say so, because Android's own per-app language screen only exists
 * from API 33 and is not somewhere people look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    onShowRotation: () -> Unit = {},
    onShowShiftTimes: () -> Unit = {},
    onShowCalendarSync: () -> Unit = {},
    onShowGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguage.stored(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            item {
                LanguageChoice(
                    label = stringResource(R.string.settings_language_system),
                    selected = language == AppLanguage.SYSTEM,
                    onClick = {
                        language = AppLanguage.SYSTEM
                        AppLanguage.set(context, AppLanguage.SYSTEM)
                        (context as? Activity)?.recreate()
                    },
                )
            }
            item {
                LanguageChoice(
                    // Deliberately untranslated: someone stuck in a language they cannot read has
                    // to be able to find their way out by recognising the name of their own.
                    label = "English",
                    selected = language == AppLanguage.ENGLISH,
                    onClick = {
                        language = AppLanguage.ENGLISH
                        AppLanguage.set(context, AppLanguage.ENGLISH)
                        (context as? Activity)?.recreate()
                    },
                )
            }
            item {
                LanguageChoice(
                    label = "עברית",
                    selected = language == AppLanguage.HEBREW,
                    onClick = {
                        language = AppLanguage.HEBREW
                        AppLanguage.set(context, AppLanguage.HEBREW)
                        (context as? Activity)?.recreate()
                    },
                )
            }

            item {
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.settings_your_schedule),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            item {
                SettingsRow(
                    title = stringResource(R.string.setup_title),
                    subtitle = stringResource(R.string.settings_rotation_help),
                    onClick = onShowRotation,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.setup_shift_times_row),
                    subtitle = stringResource(R.string.setup_shift_times_row_help),
                    onClick = onShowShiftTimes,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.setup_calendar_row),
                    subtitle = stringResource(R.string.setup_calendar_row_help),
                    onClick = onShowCalendarSync,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.guide_link),
                    subtitle = stringResource(R.string.settings_guide_help),
                    onClick = onShowGuide,
                )
            }
        }
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onClick: () -> Unit) {
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

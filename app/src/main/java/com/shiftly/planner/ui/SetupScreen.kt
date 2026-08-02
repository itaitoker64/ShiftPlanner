package com.shiftly.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.R
import com.shiftly.planner.domain.Presets
import com.shiftly.planner.domain.ShiftPreset
import com.shiftly.planner.domain.ShiftType
import com.shiftly.planner.text.displayAbbreviation
import com.shiftly.planner.text.displayDescription
import com.shiftly.planner.text.displayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val START_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: ScheduleViewModel,
    onDone: () -> Unit,
    onShowGuide: () -> Unit = {},
    onShowShiftTimes: () -> Unit = {},
    onShowCalendarSync: () -> Unit = {},
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    var useCustom by remember { mutableStateOf(false) }
    val pattern = schedule.pattern

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = {
                    if (pattern != null) {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onShowGuide) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            stringResource(R.string.cd_guide),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // The bulky part of the header — the nudge card and the links off this screen — scrolls
        // inside the list. Stacked above it in a fixed Column, as it first was, it took its height
        // off the top and left the list whatever remained; on a short screen that is close to
        // nothing, and a lazy list with no viewport cannot be scrolled to anything.
        val header: LazyListScope.() -> Unit = {
            item {
                if (pattern == null) {
                    FirstRunIntro(onShowGuide)
                } else {
                    NudgeCard(
                        patternName = pattern.name,
                        onNudge = viewModel::nudgeAnchor,
                    )
                    Spacer(Modifier.size(8.dp))
                    LinkRow(
                        title = stringResource(R.string.setup_shift_times_row),
                        subtitle = stringResource(R.string.setup_shift_times_row_help),
                        onClick = onShowShiftTimes,
                    )
                    Spacer(Modifier.size(8.dp))
                    LinkRow(
                        title = stringResource(R.string.setup_calendar_row),
                        subtitle = stringResource(R.string.setup_calendar_row_help),
                        onClick = onShowCalendarSync,
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // The two chips stay pinned. They switch between the two things this screen does, and
            // navigation that scrolls out of reach is navigation you cannot find. They are also
            // short enough that pinning them cannot squeeze the list.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !useCustom,
                    onClick = { useCustom = false },
                    label = { Text(stringResource(R.string.tab_presets)) },
                )
                FilterChip(
                    selected = useCustom,
                    onClick = { useCustom = true },
                    label = { Text(stringResource(R.string.tab_custom)) },
                )
            }

            Spacer(Modifier.size(8.dp))

            Box(Modifier.weight(1f)) {
                if (useCustom) {
                    CustomCycleBuilder(
                        shiftTypes = schedule.shiftTypes,
                        header = header,
                        onSave = { name, cycle, start ->
                            viewModel.applyCustomPattern(name, cycle, start)
                            onDone()
                        },
                    )
                } else {
                    PresetPicker(
                        shiftTypes = schedule.shiftTypes,
                        header = header,
                        onSave = { preset, start, name ->
                            viewModel.applyPreset(preset, start, name)
                            onDone()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Shown only on a fresh install, where the app opens straight onto this screen.
 *
 * Landing on thirteen unexplained preset cards is the point people bounce off, so this says what
 * the app is for and what the one decision on this screen actually is.
 */
@Composable
private fun FirstRunIntro(onShowGuide: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.intro_headline),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.intro_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        TextButton(onClick = onShowGuide, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(R.string.guide_link))
        }
    }
}

/** A tappable row leading off this screen — shift hours, calendar sync. */
@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
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

/**
 * Moves the whole rotation a day at a time.
 *
 * Getting the start date wrong by a day or two is the most common way to end up with a calendar
 * that is the right shape but on the wrong dates, and rebuilding the rotation to fix it loses the
 * days you corrected by hand. Nudging keeps them.
 */
@Composable
private fun NudgeCard(patternName: String, onNudge: (Long) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.nudge_title, patternName),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.nudge_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onNudge(-1) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nudge_earlier))
                }
                OutlinedButton(onClick = { onNudge(1) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nudge_later))
                }
            }
        }
    }
}

@Composable
private fun PresetPicker(
    shiftTypes: List<ShiftType>,
    header: LazyListScope.() -> Unit,
    onSave: (ShiftPreset, LocalDate, String) -> Unit,
) {
    var selected by remember { mutableStateOf<ShiftPreset?>(null) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header()

        items(Presets.all, key = { it.key }) { preset ->
            PresetCard(
                preset = preset,
                shiftTypes = shiftTypes,
                isSelected = preset.key == selected?.key,
                onClick = { selected = if (selected?.key == preset.key) null else preset },
            )
        }

        selected?.let { preset ->
            item {
                // Stored as well as shown: the pattern keeps a plain name, so what gets saved is
                // whatever this reader was shown when they chose it.
                val presetName = preset.displayName()

                Spacer(Modifier.size(4.dp))
                StartDateSection(
                    startDate = startDate,
                    onDateChange = { startDate = it },
                    helper = stringResource(R.string.preset_start_helper, presetName),
                )
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { onSave(preset, startDate, presetName) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_use_rotation)) }
            }
        }
    }
}

@Composable
internal fun PresetCard(
    preset: ShiftPreset,
    shiftTypes: List<ShiftType>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = preset.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(preset.displayDescription(), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(10.dp))
            CyclePreview(preset.cycle, shiftTypes)
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(
                    R.string.preset_shifts_per_cycle,
                    preset.workingDaysPerCycle,
                    preset.cycleLength,
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * A horizontal strip of coloured squares — the fastest way to recognise your own rotation.
 *
 * Internal so the first-run walkthrough shows the same strip; two drawings of one cycle would
 * drift.
 */
@Composable
internal fun CyclePreview(cycle: List<String>, shiftTypes: List<ShiftType>) {
    val context = LocalContext.current
    val byId = remember(shiftTypes) { shiftTypes.associateBy { it.id } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        items(cycle.size) { index ->
            val type = byId[cycle[index]]
            val fill = type?.let { Color(it.colorArgb.toInt()) } ?: Color.Gray
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                if (type?.isWorking == true) {
                    Text(
                        text = type.displayAbbreviation(context),
                        style = MaterialTheme.typography.labelSmall,
                        color = textOn(fill),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomCycleBuilder(
    shiftTypes: List<ShiftType>,
    header: LazyListScope.() -> Unit,
    onSave: (String, List<String>, LocalDate) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    val cycle = remember { mutableListOf<String>().toMutableStateList() }
    val byId = remember(shiftTypes) { shiftTypes.associateBy { it.id } }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.custom_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = stringResource(R.string.custom_help),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.size(8.dp))
            // A handful of shift types — a scrolling Row costs less than the lazy machinery, while
            // still not clipping if someone adds enough custom types to overflow the width.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                shiftTypes.forEach { type ->
                    val fill = Color(type.colorArgb.toInt())
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .clickable { cycle.add(type.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = type.displayAbbreviation().ifEmpty { "–" },
                            color = textOn(fill),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = if (cycle.isEmpty()) {
                    stringResource(R.string.custom_cycle_empty)
                } else {
                    stringResource(R.string.custom_cycle_length, cycle.size)
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (cycle.isNotEmpty()) {
            item {
                // Tap a day in the built cycle to remove it.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(cycle) { index, id ->
                        val type = byId[id]
                        val fill = type?.let { Color(it.colorArgb.toInt()) } ?: Color.Gray
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(fill)
                                .clickable { cycle.removeAt(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = type?.displayAbbreviation(context).orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = textOn(fill),
                            )
                        }
                    }
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.custom_tap_to_remove),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            item {
                StartDateSection(
                    startDate = startDate,
                    onDateChange = { startDate = it },
                    helper = stringResource(R.string.custom_start_helper),
                )
            }

            item {
                Button(
                    onClick = { onSave(name, cycle.toList(), startDate) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_save_rotation)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateSection(
    startDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    helper: String,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Text(helper, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cycle_starts, startDate.format(START_DATE)))
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.toEpochDay() * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // The picker works in UTC; convert without letting the local zone shift the day.
                        onDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

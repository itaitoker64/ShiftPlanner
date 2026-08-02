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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.domain.Presets
import com.shiftly.planner.domain.ShiftPreset
import com.shiftly.planner.domain.ShiftType
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
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    var useCustom by remember { mutableStateOf(false) }
    val pattern = schedule.pattern

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your rotation") },
                navigationIcon = {
                    if (pattern != null) {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onShowGuide) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, "How this app works")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (pattern == null) {
                FirstRunIntro(onShowGuide)
            } else {
                NudgeCard(
                    patternName = pattern.name,
                    onNudge = viewModel::nudgeAnchor,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !useCustom,
                    onClick = { useCustom = false },
                    label = { Text("Common rotations") },
                )
                FilterChip(
                    selected = useCustom,
                    onClick = { useCustom = true },
                    label = { Text("Build my own") },
                )
            }

            Spacer(Modifier.size(8.dp))

            if (useCustom) {
                CustomCycleBuilder(
                    shiftTypes = schedule.shiftTypes,
                    onSave = { name, cycle, start ->
                        viewModel.applyCustomPattern(name, cycle, start)
                        onDone()
                    },
                )
            } else {
                PresetPicker(
                    shiftTypes = schedule.shiftTypes,
                    onSave = { preset, start ->
                        viewModel.applyPreset(preset, start)
                        onDone()
                    },
                )
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
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Text(
            text = "Pick the rotation you work, and Shiftly fills in your calendar for every " +
                "month ahead.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "Choose one below, or build your own if it isn't listed. You can change it " +
                "later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        TextButton(onClick = onShowGuide, contentPadding = PaddingValues(0.dp)) {
            Text("How this app works")
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "You're on $patternName",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Right shifts, wrong days? Move the whole rotation without rebuilding it. " +
                    "Days you changed by hand stay where they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onNudge(-1) }, modifier = Modifier.weight(1f)) {
                    Text("A day earlier")
                }
                OutlinedButton(onClick = { onNudge(1) }, modifier = Modifier.weight(1f)) {
                    Text("A day later")
                }
            }
        }
    }
}

@Composable
private fun PresetPicker(
    shiftTypes: List<ShiftType>,
    onSave: (ShiftPreset, LocalDate) -> Unit,
) {
    var selected by remember { mutableStateOf<ShiftPreset?>(null) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                Spacer(Modifier.size(4.dp))
                StartDateSection(
                    startDate = startDate,
                    onDateChange = { startDate = it },
                    helper = "Pick the first day of your next \"${preset.name}\" cycle — the first " +
                        "day back on after a block off.",
                )
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { onSave(preset, startDate) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Use this rotation") }
            }
        }
    }
}

@Composable
private fun PresetCard(
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
            Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(preset.description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(10.dp))
            CyclePreview(preset.cycle, shiftTypes)
            Spacer(Modifier.size(6.dp))
            Text(
                text = "${preset.workingDaysPerCycle} shifts every ${preset.cycleLength} days",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** A horizontal strip of coloured squares — the fastest way to recognise your own rotation. */
@Composable
private fun CyclePreview(cycle: List<String>, shiftTypes: List<ShiftType>) {
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
                        text = type.abbreviation,
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
    onSave: (String, List<String>, LocalDate) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    val cycle = remember { mutableListOf<String>().toMutableStateList() }
    val byId = remember(shiftTypes) { shiftTypes.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "Tap a shift for each day of your cycle, in order. Add days until the " +
                    "pattern repeats.",
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
                            text = type.abbreviation.ifEmpty { "–" },
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
                    "Your cycle is empty"
                } else {
                    "Your cycle · ${cycle.size} days"
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
                                text = type?.abbreviation.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = textOn(fill),
                            )
                        }
                    }
                }
                Spacer(Modifier.size(4.dp))
                Text("Tap a day to remove it", style = MaterialTheme.typography.labelSmall)
            }

            item {
                StartDateSection(
                    startDate = startDate,
                    onDateChange = { startDate = it },
                    helper = "Pick the date you'll be on day 1 of this cycle.",
                )
            }

            item {
                Button(
                    onClick = { onSave(name, cycle.toList(), startDate) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save rotation") }
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
            Text("Cycle starts: ${startDate.format(START_DATE)}")
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

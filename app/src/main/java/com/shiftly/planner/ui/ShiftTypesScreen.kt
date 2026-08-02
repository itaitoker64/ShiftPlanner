package com.shiftly.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
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
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftSegment
import com.shiftly.planner.domain.ShiftType
import com.shiftly.planner.text.displayAbbreviation
import com.shiftly.planner.text.displayName
import java.util.UUID

/** Colours offered when creating or recolouring a shift. */
private val PALETTE = listOf(
    0xFF1E88E5, 0xFF5E35B1, 0xFFF4511E, 0xFF00897B, 0xFF43A047,
    0xFFD81B60, 0xFF6D4C41, 0xFF546E7A, 0xFFF9A825, 0xFFE8EAED,
)

private const val DEFAULT_START_MINUTE = 9 * 60
private const val DEFAULT_END_MINUTE = 17 * 60

internal fun minuteOfDayText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/**
 * Where the hours of a shift are set.
 *
 * The app shipped with a day shift running 07:00–19:00 and no way to argue with it, which is wrong
 * for most of the people it is for. Every field of a shift is editable here, and new shifts can be
 * added for anything the built-ins do not cover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftTypesScreen(
    viewModel: ScheduleViewModel,
    onDone: () -> Unit,
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ShiftType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shift_times_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = newShiftType() }) {
                Icon(Icons.Default.Add, stringResource(R.string.shift_add))
            }
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
                    text = stringResource(R.string.shift_times_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(schedule.shiftTypes, key = { it.id }) { type ->
                ShiftTypeRow(type = type, onClick = { editing = type })
            }
        }
    }

    editing?.let { type ->
        ShiftTypeEditor(
            type = type,
            // A type on the rotation cannot be removed without blanking the days it covers, and a
            // built-in comes straight back on the next load. Both are decided in the domain; this
            // just declines to offer the button.
            canDelete = !schedule.isShiftTypeInUse(type.id) && !isBuiltin(type.id) &&
                schedule.typeOrNull(type.id) != null,
            onSave = {
                viewModel.saveShiftType(it)
                editing = null
            },
            onDelete = {
                viewModel.deleteShiftType(type.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun isBuiltin(id: String): Boolean = ShiftType.defaults.any { it.id == id }

private fun newShiftType() = ShiftType(
    id = "custom_${UUID.randomUUID()}",
    name = "",
    abbreviation = "",
    colorArgb = PALETTE.first(),
    startMinute = DEFAULT_START_MINUTE,
    endMinute = DEFAULT_END_MINUTE,
    isWorking = true,
)

@Composable
internal fun ShiftTypeRow(type: ShiftType, onClick: () -> Unit) {
    val context = LocalContext.current
    val fill = Color(type.colorArgb.toInt())

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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = type.displayAbbreviation(context),
                    color = textOn(fill),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = type.displayName(context),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val blocks = type.blocks
                Text(
                    text = when {
                        !type.isWorking || blocks.isEmpty() ->
                            stringResource(R.string.shift_not_worked)

                        blocks.size == 1 -> stringResource(
                            R.string.shift_hours_summary,
                            minuteOfDayText(blocks.single().startMinute),
                            minuteOfDayText(blocks.single().endMinute),
                            (type.durationMinutes ?: 0) / 60,
                        )

                        // A split day: say how many blocks rather than listing them all in a row
                        // that has no space for them.
                        else -> stringResource(
                            R.string.shift_hours_split_summary,
                            blocks.size,
                            (type.durationMinutes ?: 0) / 60,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShiftTypeEditor(
    type: ShiftType,
    canDelete: Boolean,
    onSave: (ShiftType) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Seeded from the built-in's translated name so a Hebrew reader edits Hebrew, not "Evening".
    var name by remember(type.id) { mutableStateOf(type.displayName(context)) }
    var abbreviation by remember(type.id) { mutableStateOf(type.displayAbbreviation(context)) }
    var isWorking by remember(type.id) { mutableStateOf(type.isWorking) }
    var colorArgb by remember(type.id) { mutableStateOf(type.colorArgb) }
    var picking by remember(type.id) { mutableStateOf<TimeField?>(null) }
    // One entry for a plain shift, several for a watch pattern like four on and eight off.
    val blocks = remember(type.id) {
        type.blocks
            .ifEmpty { listOf(ShiftSegment(DEFAULT_START_MINUTE, DEFAULT_END_MINUTE)) }
            .toMutableStateList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shift_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(10.dp))
            OutlinedTextField(
                value = abbreviation,
                // Two characters is what fits inside a calendar square.
                onValueChange = { abbreviation = it.take(2) },
                label = { Text(stringResource(R.string.shift_letter_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shift_is_working),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.shift_is_working_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = isWorking, onCheckedChange = { isWorking = it })
            }

            if (isWorking) {
                Spacer(Modifier.size(14.dp))

                blocks.forEachIndexed { index, block ->
                    if (blocks.size > 1) {
                        Text(
                            text = stringResource(R.string.shift_block_number, index + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(4.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { picking = TimeField(index, isStart = true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(
                                    R.string.shift_starts_at,
                                    minuteOfDayText(block.startMinute),
                                )
                            )
                        }
                        OutlinedButton(
                            onClick = { picking = TimeField(index, isStart = false) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(
                                    R.string.shift_ends_at,
                                    minuteOfDayText(block.endMinute),
                                )
                            )
                        }
                        // The last block cannot be removed; a working shift with no hours at all
                        // would count as zero everywhere and read as a bug.
                        if (blocks.size > 1) {
                            IconButton(onClick = { blocks.removeAt(index) }) {
                                Icon(
                                    Icons.Default.Close,
                                    stringResource(R.string.shift_remove_block),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                }

                TextButton(
                    onClick = {
                        blocks.add(ShiftSegment(DEFAULT_START_MINUTE, DEFAULT_END_MINUTE))
                    },
                ) { Text(stringResource(R.string.shift_add_block)) }

                Spacer(Modifier.size(4.dp))
                Text(
                    // Read back from the same arithmetic the calendar totals use, so a block over
                    // midnight is visibly four hours rather than minus twenty.
                    text = stringResource(
                        R.string.shift_lasts,
                        blocks.sumOf { it.lengthMinutes } / 60,
                        blocks.sumOf { it.lengthMinutes } % 60,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.shift_colour),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.size(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PALETTE.forEach { swatch ->
                    val selected = swatch == colorArgb
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(swatch.toInt()))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    Color.Transparent
                                },
                                shape = CircleShape,
                            )
                            .clickable { colorArgb = swatch },
                    )
                }
            }

            Spacer(Modifier.size(20.dp))
            Button(
                onClick = {
                    onSave(
                        type.copy(
                            name = name.ifBlank { type.name },
                            abbreviation = abbreviation.trim(),
                            colorArgb = colorArgb,
                            isWorking = isWorking,
                            // Clearing the times on a non-working shift is what makes it read as
                            // "off" everywhere else: durationMinutes goes null and stops counting.
                            // One block stays in the plain pair of fields so an ordinary shift
                            // never grows a list it does not need; several move to segments.
                            startMinute = blocks.firstOrNull()
                                ?.startMinute
                                ?.takeIf { isWorking && blocks.size == 1 },
                            endMinute = blocks.firstOrNull()
                                ?.endMinute
                                ?.takeIf { isWorking && blocks.size == 1 },
                            segments = if (isWorking && blocks.size > 1) blocks.toList()
                            else emptyList(),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.shift_save)) }

            if (canDelete) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shift_delete))
                }
            }
        }
    }

    picking?.let { field ->
        val block = blocks.getOrNull(field.index) ?: return@let
        ShiftTimePickerDialog(
            initialMinute = if (field.isStart) block.startMinute else block.endMinute,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                blocks[field.index] = if (field.isStart) {
                    block.copy(startMinute = minute)
                } else {
                    block.copy(endMinute = minute)
                }
                picking = null
            },
        )
    }
}

/** Which end of which block the time picker is currently editing. */
private data class TimeField(val index: Int, val isStart: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftTimePickerDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        // Shift hours are written 07:00-19:00 everywhere else in the app; matching that beats
        // matching the phone's clock format here.
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}

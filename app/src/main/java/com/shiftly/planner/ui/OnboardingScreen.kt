package com.shiftly.planner.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftly.planner.R
import com.shiftly.planner.domain.DayShift
import com.shiftly.planner.domain.Presets
import com.shiftly.planner.domain.Schedule
import com.shiftly.planner.domain.ShiftPattern
import com.shiftly.planner.domain.ShiftPreset
import com.shiftly.planner.domain.ShiftType
import com.shiftly.planner.text.DateSkeleton
import com.shiftly.planner.text.autoIsolate
import com.shiftly.planner.text.displayName
import com.shiftly.planner.text.rememberDateFormatter
import java.time.LocalDate


/** How many days of the rotation the confirmation step draws. */
private const val PREVIEW_DAYS = 14

private enum class Step { Welcome, Rotation, StartDate, Hours, Done }

/**
 * The walkthrough a fresh install opens on, once.
 *
 * The previous first run dropped you onto a list of thirteen rotations with a date picker and no
 * way to tell whether you had got it right. The step that earns this screen is [Step.StartDate]:
 * it draws the fortnight your answers actually produce, so a rotation sitting a day out is
 * something you can see and fix here rather than discover next week.
 *
 * Nothing is written until Finish, so backing out of the walkthrough leaves no half-built rota
 * behind. Shift hours are the exception — those are global, and editing them early is harmless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: ScheduleViewModel,
    onFinished: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(Step.Welcome) }
    var preset by remember { mutableStateOf<ShiftPreset?>(null) }
    var startDate by remember { mutableStateOf(today) }
    var editingType by remember { mutableStateOf<ShiftType?>(null) }

    // Resolved here rather than at the tap: what gets stored should be what this reader saw.
    val chosenName = preset?.displayName() ?: ""

    // A rota built in memory only. It is what the preview draws, so the fortnight on screen is
    // exactly what Finish will save — not an approximation of it.
    val provisional = remember(schedule.shiftTypes, preset, startDate) {
        preset?.let {
            Schedule(
                shiftTypes = schedule.shiftTypes,
                pattern = ShiftPattern(
                    id = "preview",
                    name = it.name,
                    cycle = it.cycle,
                    anchorEpochDay = startDate.toEpochDay(),
                ),
            )
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step.ordinal + 1f) / Step.entries.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )

            Box(Modifier.weight(1f)) {
                when (step) {
                    Step.Welcome -> WelcomeStep()

                    Step.Rotation -> RotationStep(
                        shiftTypes = schedule.shiftTypes,
                        selected = preset,
                        onSelect = { preset = it },
                    )

                    Step.StartDate -> StartDateStep(
                        preview = provisional,
                        startDate = startDate,
                        today = today,
                        onNudge = { startDate = startDate.plusDays(it) },
                    )

                    Step.Hours -> HoursStep(
                        schedule = schedule,
                        cycle = preset?.cycle.orEmpty(),
                        onEdit = { editingType = it },
                    )

                    Step.Done -> DoneStep(preview = provisional)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step != Step.Welcome) {
                    OutlinedButton(
                        onClick = { step = Step.entries[step.ordinal - 1] },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.onboarding_back)) }
                }

                Button(
                    onClick = {
                        if (step == Step.Done) {
                            preset?.let { chosen ->
                                viewModel.applyPreset(chosen, startDate, chosenName)
                            }
                            viewModel.markOnboardingComplete()
                            onFinished()
                        } else {
                            step = Step.entries[step.ordinal + 1]
                        }
                    },
                    // Every later step is about a rotation, so there is nothing to advance to
                    // until one is chosen.
                    enabled = step == Step.Welcome || preset != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (step == Step.Done) R.string.onboarding_finish
                            else R.string.onboarding_next
                        )
                    )
                }
            }

            TextButton(
                onClick = {
                    viewModel.markOnboardingComplete()
                    onFinished()
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp),
            ) { Text(stringResource(R.string.onboarding_skip)) }
        }
    }

    editingType?.let { type ->
        // Hours are global rather than part of this rota, so saving one mid-walkthrough is safe.
        ShiftTypeEditor(
            type = type,
            // Deleting a shift part-way through choosing a rotation that uses it is a trap.
            canDelete = false,
            onSave = {
                viewModel.saveShiftType(it)
                editingType = null
            },
            onDelete = { editingType = null },
            onDismiss = { editingType = null },
        )
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_1),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RotationStep(
    shiftTypes: List<ShiftType>,
    selected: ShiftPreset?,
    onSelect: (ShiftPreset) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StepHeading(
                title = stringResource(R.string.onboarding_rotation_title),
                body = stringResource(R.string.onboarding_rotation_body),
            )
        }
        items(Presets.all, key = { it.key }) { preset ->
            PresetCard(
                preset = preset,
                shiftTypes = shiftTypes,
                isSelected = preset.key == selected?.key,
                onClick = { onSelect(preset) },
            )
        }
    }
}

@Composable
private fun StartDateStep(
    preview: Schedule?,
    startDate: LocalDate,
    today: LocalDate,
    onNudge: (Long) -> Unit,
) {
    val fullDate = rememberDateFormatter(DateSkeleton.SHORT_FULL_DATE, "EEE d MMM yyyy")

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_start_title),
            body = stringResource(R.string.onboarding_start_body),
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(
                        R.string.onboarding_day_one_is,
                        autoIsolate(startDate.format(fullDate)),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
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

        Text(
            text = stringResource(R.string.onboarding_preview_label),
            style = MaterialTheme.typography.labelLarge,
        )
        RotationPreview(preview = preview, today = today)
        Text(
            text = stringResource(R.string.onboarding_preview_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HoursStep(schedule: Schedule, cycle: List<String>, onEdit: (ShiftType) -> Unit) {
    // Only the shifts this rotation actually uses. Offering to set the hours of an evening shift
    // to someone on a days-and-nights rota is noise.
    val used = remember(schedule.shiftTypes, cycle) {
        cycle.distinct().mapNotNull { schedule.typeOrNull(it) }.filter { it.isWorking }
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_hours_title),
            body = stringResource(R.string.onboarding_hours_body),
        )
        used.forEach { type ->
            // The same row and editor the shift-times screen uses, so the hours set here and the
            // hours set later are the one control.
            ShiftTypeRow(type = type, onClick = { onEdit(type) })
        }
    }
}

@Composable
private fun DoneStep(preview: Schedule?) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepHeading(
            title = stringResource(R.string.onboarding_done_title),
            body = stringResource(R.string.onboarding_done_body),
        )
        preview?.pattern?.let { pattern ->
            Text(
                text = stringResource(
                    R.string.rotation_summary,
                    pattern.name,
                    pattern.cycleLength,
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StepHeading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The next fortnight as the calendar would draw it, with the weekday above each square. */
@Composable
private fun RotationPreview(preview: Schedule?, today: LocalDate) {
    if (preview == null) return

    val weekday = rememberDateFormatter("EEE", "EEE")
    val days = remember(preview, today) {
        (0 until PREVIEW_DAYS).map { offset ->
            val date = today.plusDays(offset.toLong())
            DayShift(date, preview.shiftOn(date), preview.isOverridden(date))
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.date.format(weekday),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.width(44.dp)) {
                    DayCell(day = day, isToday = day.date == today, isSelected = false)
                }
            }
        }
    }
}

package com.shiftly.planner.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.shiftly.planner.R
import com.shiftly.planner.domain.ShiftPreset
import com.shiftly.planner.domain.ShiftType

/**
 * Turns the domain's stable ids into text in the reader's language.
 *
 * `domain/` must not import Android, so it cannot reach a string resource — which is why the shift
 * types and presets carry English names at all. Those names are the fallback; this is where they
 * become Hebrew on a Hebrew phone. The lookup goes through the ids the domain already has, so
 * nothing about the pattern model has to change to add a language.
 */

/** True while a built-in still carries the name the app shipped it with. */
private fun ShiftType.isUnrenamedBuiltin(): Boolean =
    ShiftType.defaults.any { it.id == id && it.name == name && it.abbreviation == abbreviation }

private fun builtinNameRes(id: String): Int? = when (id) {
    ShiftType.ID_OFF -> R.string.shift_off
    ShiftType.ID_DAY -> R.string.shift_day
    ShiftType.ID_NIGHT -> R.string.shift_night
    ShiftType.ID_EVENING -> R.string.shift_evening
    ShiftType.ID_FULL_DAY -> R.string.shift_full_day
    else -> null
}

private fun builtinAbbreviationRes(id: String): Int? = when (id) {
    ShiftType.ID_OFF -> R.string.shift_off_short
    ShiftType.ID_DAY -> R.string.shift_day_short
    ShiftType.ID_NIGHT -> R.string.shift_night_short
    ShiftType.ID_EVENING -> R.string.shift_evening_short
    ShiftType.ID_FULL_DAY -> R.string.shift_full_day_short
    else -> null
}

/**
 * The shift's name, translated when it is one of the built-ins.
 *
 * A type the user created — or renamed — keeps whatever they typed. Translating that would be
 * overwriting their words, not localising ours.
 */
fun ShiftType.displayName(context: Context): String =
    builtinNameRes(id)?.takeIf { isUnrenamedBuiltin() }?.let(context::getString) ?: name

/** The one or two characters drawn inside a calendar square. */
fun ShiftType.displayAbbreviation(context: Context): String =
    builtinAbbreviationRes(id)?.takeIf { isUnrenamedBuiltin() }?.let(context::getString)
        ?: abbreviation

@Composable
fun ShiftType.displayName(): String = displayName(LocalContext.current)

@Composable
fun ShiftType.displayAbbreviation(): String = displayAbbreviation(LocalContext.current)

private fun presetNameRes(key: String): Int? = when (key) {
    "4on4off_days" -> R.string.preset_4on4off_days
    "4on4off_dn" -> R.string.preset_4on4off_dn
    "panama_days" -> R.string.preset_panama_days
    "panama_dn" -> R.string.preset_panama_dn
    "dupont" -> R.string.preset_dupont
    "3on3off" -> R.string.preset_3on3off
    "7on7off" -> R.string.preset_7on7off
    "5on2off" -> R.string.preset_5on2off
    "6on2off" -> R.string.preset_6on2off
    "week_days_nights" -> R.string.preset_week_days_nights
    "24_48" -> R.string.preset_24_48
    "48_96" -> R.string.preset_48_96
    "kelly" -> R.string.preset_kelly
    else -> null
}

private fun presetDescriptionRes(key: String): Int? = when (key) {
    "4on4off_days" -> R.string.preset_4on4off_days_desc
    "4on4off_dn" -> R.string.preset_4on4off_dn_desc
    "panama_days" -> R.string.preset_panama_days_desc
    "panama_dn" -> R.string.preset_panama_dn_desc
    "dupont" -> R.string.preset_dupont_desc
    "3on3off" -> R.string.preset_3on3off_desc
    "7on7off" -> R.string.preset_7on7off_desc
    "5on2off" -> R.string.preset_5on2off_desc
    "6on2off" -> R.string.preset_6on2off_desc
    "week_days_nights" -> R.string.preset_week_days_nights_desc
    "24_48" -> R.string.preset_24_48_desc
    "48_96" -> R.string.preset_48_96_desc
    "kelly" -> R.string.preset_kelly_desc
    else -> null
}

fun ShiftPreset.displayName(context: Context): String =
    presetNameRes(key)?.let(context::getString) ?: name

fun ShiftPreset.displayDescription(context: Context): String =
    presetDescriptionRes(key)?.let(context::getString) ?: description

@Composable
fun ShiftPreset.displayName(): String = displayName(LocalContext.current)

@Composable
fun ShiftPreset.displayDescription(): String = displayDescription(LocalContext.current)

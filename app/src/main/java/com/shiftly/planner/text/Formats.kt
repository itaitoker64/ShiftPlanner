package com.shiftly.planner.text

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dates, clock times and right-to-left text.
 *
 * Two things go wrong in Hebrew if every screen formats its own dates, and both did.
 *
 * The first is language. A `DateTimeFormatter` built as a top-level `val` captures
 * `Locale.getDefault()` once, when its class is first touched — so the month name stayed in the
 * phone's language while every other word on screen turned Hebrew, and switching language in
 * Settings could not move it without killing the process. Everything here takes the locale as an
 * argument, read from the context that is actually being drawn.
 *
 * The second is direction, which is subtler and worse. See [ltrIsolate].
 */

/** LEFT-TO-RIGHT ISOLATE: opens a run that reads left to right whatever surrounds it. */
private const val LRI = '\u2066'

/** FIRST STRONG ISOLATE: the run takes the direction of its own first strong character. */
private const val FSI = '\u2068'

/** POP DIRECTIONAL ISOLATE: closes either of the above. */
private const val PDI = '\u2069'

/**
 * The locale the app is being shown in — not the phone's.
 *
 * `Locale.getDefault()` is the phone's, and below API 33 an in-app language choice never reaches
 * it: the choice is applied by wrapping the activity's context, so the context is the only thing
 * that knows. Reading it here is what keeps a date in step with the words around it.
 */
val Context.appLocale: Locale
    get() = resources.configuration.locales[0]

/**
 * A formatter for [skeleton] in this locale's own word order.
 *
 * A fixed pattern like `"EEE d MMM yyyy"` is English word order frozen into the app. Hebrew puts a
 * prefix on the month — the fifth of August is "5 באוגוסט", not "5 אוגוסט" — and ICU already knows
 * that for every locale. A skeleton names the fields wanted and lets the locale arrange them.
 *
 * [fallback] is used if ICU hands back a pattern `DateTimeFormatter` cannot parse. That should not
 * happen for these skeletons, but a wrong-looking date is a far better failure than a crash on the
 * first screen.
 */
fun dateFormatter(locale: Locale, skeleton: String, fallback: String): DateTimeFormatter =
    runCatching {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }.getOrElse {
        DateTimeFormatter.ofPattern(fallback, locale)
    }

/** Skeletons, named so the call sites read as intent rather than as punctuation. */
object DateSkeleton {
    /** "August 2026" */
    const val MONTH_AND_YEAR = "MMMMy"

    /** "Saturday 8 August" */
    const val WEEKDAY_DAY_MONTH = "EEEEdMMMM"

    /** "Sat 8 Aug 2026" */
    const val SHORT_FULL_DATE = "EEEdMMMy"

    /** "Sat 8 Aug" */
    const val SHORT_DATE = "EEEdMMM"
}

/**
 * Remembers a formatter for as long as the app's language stays put.
 *
 * Keyed on the configuration's locale so that recomposing after a language change rebuilds it,
 * which a plain top-level `val` cannot do.
 */
@Composable
fun rememberDateFormatter(skeleton: String, fallback: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, skeleton) { dateFormatter(locale, skeleton, fallback) }
}

/**
 * A 24-hour clock time.
 *
 * Forced to ASCII digits via [Locale.ROOT]: `"%02d".format(…)` would otherwise follow the default
 * locale's numbering system. Not isolated on its own — see [ltrIsolate] for why a lone time does
 * not need it and a range does.
 */
fun clockText(minuteOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/**
 * Pins [text] to left-to-right order inside right-to-left text.
 *
 * The bidirectional algorithm treats a run of digits as taking the direction of the paragraph
 * around it. In a Hebrew paragraph that means "07:00 – 19:00" is laid out right to left as a whole:
 * both times survive intact, but they swap ends, and the reader is shown the finish time first with
 * nothing on screen to say so. A shift that runs 07:00 to 19:00 and one that runs 19:00 to 07:00
 * are both real rotas, so there is no reading that recovers the right answer.
 *
 * Isolating the range keeps the start on the left, which is how a clock is written in Hebrew too.
 * It has to wrap the *whole* range: isolating each time separately leaves two neutral objects with
 * a neutral dash between them, and they reorder exactly as before.
 */
fun ltrIsolate(text: String): String = "$LRI$text$PDI"

/**
 * Isolates text whose direction the app does not control.
 *
 * A rotation the user named "Panama nights" and one they named "לילות" have to sit in the same
 * Hebrew sentence. Without an isolate the Latin one drags the punctuation around it to the wrong
 * end of the line; with a first-strong isolate each name is laid out in its own direction and the
 * sentence around it is unaffected.
 */
fun autoIsolate(text: String): String = "$FSI$text$PDI"

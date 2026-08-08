package com.shiftly.planner.text

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Dates and direction in Hebrew.
 *
 * Every failure this covers is invisible in English, which is why none of it was caught before: the
 * app renders perfectly in one language and is wrong in the other in ways that look, at a glance,
 * like formatting taste rather than bugs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FormatsTest {

    private val hebrew = Locale.forLanguageTag("he-IL")

    private fun contextIn(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    @Test
    fun `a clock time is ASCII digits whatever the default locale is`() {
        val previous = Locale.getDefault()
        try {
            // An Arabic-Indic numbering locale is the case that catches a bare "%02d".format().
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("07:00", clockText(7 * 60))
            assertEquals("19:30", clockText(19 * 60 + 30))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a time range is pinned left to right`() {
        val isolated = ltrIsolate("07:00 – 19:00")

        // U+2066 LRI … U+2069 PDI. Without them the bidi algorithm lays the pair out right to
        // left in Hebrew and the reader is shown the finish time first.
        assertEquals('\u2066', isolated.first())
        assertEquals('\u2069', isolated.last())
        assertTrue(isolated.contains("07:00 – 19:00"))
    }

    @Test
    fun `a name of unknown direction is isolated without being changed`() {
        val isolated = autoIsolate("Panama nights")

        assertEquals('\u2068', isolated.first())
        assertEquals('\u2069', isolated.last())
        assertEquals("Panama nights", isolated.trim('\u2068', '\u2069'))
    }

    @Test
    fun `a Hebrew context formats its dates in Hebrew`() {
        val locale = contextIn(hebrew).appLocale
        val formatted = LocalDate.of(2026, 8, 5)
            .format(dateFormatter(locale, DateSkeleton.SHORT_FULL_DATE, "EEE d MMM yyyy"))

        // The specific wording is ICU's business; that it is Hebrew at all is ours. This is the
        // regression that a top-level DateTimeFormatter caused — it captured the phone's locale
        // once, so the month name stayed English under an otherwise Hebrew screen.
        assertTrue(formatted, formatted.any { it in '\u0590'..'\u05ff' })
        assertFalse(formatted, formatted.contains("Aug"))
    }

    @Test
    fun `the month title differs between English and Hebrew`() {
        val date = LocalDate.of(2026, 8, 5)
        val skeleton = DateSkeleton.MONTH_AND_YEAR

        val english = date.format(dateFormatter(Locale.US, skeleton, "MMMM yyyy"))
        val israeli = date.format(dateFormatter(hebrew, skeleton, "MMMM yyyy"))

        assertNotEquals(english, israeli)
        assertTrue(english, english.contains("August"))
    }

    @Test
    fun `a Hebrew calendar opens its week on Sunday`() {
        // The property that matters to a reader, whoever derives it. The calendar takes its week
        // start from the language the app is being shown in rather than from Locale.getDefault(),
        // which below API 33 is still the phone's — so a Hebrew app on an English phone used to
        // draw its first column on Monday.
        assertEquals(DayOfWeek.SUNDAY, WeekFields.of(hebrew).firstDayOfWeek)
        assertEquals(
            DayOfWeek.SUNDAY,
            WeekFields.of(Locale.forLanguageTag(AppLanguage.HEBREW)).firstDayOfWeek,
        )
    }

    @Test
    fun `a British phone still opens its week on Monday`() {
        // The other half of the same rule: reading the week start from a locale rather than
        // hardcoding one keeps the rest of the world right too.
        assertEquals(DayOfWeek.MONDAY, WeekFields.of(Locale.UK).firstDayOfWeek)
    }
}

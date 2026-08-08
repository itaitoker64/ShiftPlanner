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
    fun `choosing Hebrew keeps the phone's country`() {
        // Country decides conventions, language decides words, and picking a language is not a
        // statement about where you live.
        val onAnAmericanPhone = AppLanguage.localeFor(AppLanguage.HEBREW, Locale.US)

        assertEquals("US", onAnAmericanPhone.country)
        assertEquals(Locale.forLanguageTag("he").language, onAnAmericanPhone.language)
    }

    @Test
    fun `a country-less Hebrew locale would open the week on the wrong day`() {
        // The reason the rule above exists. A bare "he" carries no country, so WeekFields falls
        // back to a Monday start and a Hebrew calendar draws its first column on the wrong day.
        val bare = Locale.forLanguageTag("he")
        val withCountry = AppLanguage.localeFor(AppLanguage.HEBREW, Locale.US)

        assertEquals(DayOfWeek.MONDAY, WeekFields.of(bare).firstDayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, WeekFields.of(withCountry).firstDayOfWeek)
    }

    @Test
    fun `a phone with no country is left alone rather than guessed at`() {
        val locale = AppLanguage.localeFor(AppLanguage.HEBREW, Locale.forLanguageTag("en"))

        assertEquals("", locale.country)
    }
}

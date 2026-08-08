package com.shiftly.planner.text

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The language the app is shown in, independently of the phone's.
 *
 * Android only started offering a per-app language picker in its own settings on API 33, and even
 * there people do not find it. Someone who wants the app in Hebrew on an English phone has to be
 * able to say so from inside the app.
 *
 * Two things are kept in step. [AppCompatDelegate] is the androidx-blessed route: on API 33 and up
 * it hands the choice to the platform, so the widget, the notifications and the system's own
 * per-app language screen all agree with it. Underneath that we also keep the tag in
 * SharedPreferences, because [applyTo] runs from `attachBaseContext` — before anything asynchronous
 * exists — and needs an answer synchronously. DataStore cannot give one there.
 */
object AppLanguage {

    /** Follow the phone. */
    const val SYSTEM = ""

    const val ENGLISH = "en"
    const val HEBREW = "he"

    private const val PREFS = "shiftly_settings"
    private const val KEY_TAG = "language_tag"

    fun stored(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, SYSTEM)
            .orEmpty()

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, tag)
            .apply()

        AppCompatDelegate.setApplicationLocales(
            if (tag == SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
        )
    }

    /**
     * Wraps a context in the chosen language.
     *
     * Needed because the activity is a plain ComponentActivity rather than an AppCompatActivity, so
     * appcompat's pre-33 backport has nothing of ours to recreate. Setting the layout direction
     * from the locale as well is what gets Hebrew laid out right to left.
     */
    fun applyTo(base: Context): Context {
        val tag = stored(base)
        if (tag == SYSTEM) return base

        val locale = localeFor(tag, base.resources.configuration.locales[0])
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    /**
     * The chosen language, keeping the phone's country.
     *
     * Choosing Hebrew is a statement about words, not about where you live, and country is what
     * decides the conventions around them — which day a week starts on, above all. A bare "he"
     * carries no country, so `WeekFields` falls back to a Monday start and a Hebrew calendar opens
     * on the wrong day. Carrying the phone's country over leaves those conventions where the owner
     * of the phone already put them.
     */
    internal fun localeFor(tag: String, current: Locale): Locale {
        val chosen = Locale.forLanguageTag(tag)
        val country = current.country
        if (country.isEmpty()) return chosen

        return runCatching {
            Locale.Builder().setLanguage(chosen.language).setRegion(country).build()
        }.getOrDefault(chosen)
    }
}

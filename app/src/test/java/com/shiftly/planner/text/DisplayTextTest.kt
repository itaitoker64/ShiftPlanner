package com.shiftly.planner.text

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shiftly.planner.domain.Presets
import com.shiftly.planner.domain.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The seam between the domain's English names and the translated ones.
 *
 * `domain/` cannot reach a string resource, so every preset and built-in shift type carries an
 * English name *and* has a translation keyed off its id. Two copies of the same text is exactly the
 * arrangement that drifts, so these tests hold them together: in the default locale the resource
 * must still say what the domain says, and every id must actually have a resource — a missing one
 * would fall back silently and leave that one row in English forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DisplayTextTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
    }

    /** Resolves by name so that a missing resource fails here rather than falling back quietly. */
    private fun stringByName(name: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        assertTrue("no string resource named $name", id != 0)
        return context.getString(id)
    }

    @Test
    fun `every preset has a name and description resource matching the domain`() {
        Presets.all.forEach { preset ->
            assertEquals(preset.key, preset.name, stringByName("preset_${preset.key}"))
            assertEquals(
                preset.key,
                preset.description,
                stringByName("preset_${preset.key}_desc"),
            )
        }
    }

    @Test
    fun `presets resolve to the translated name`() {
        Presets.all.forEach { preset ->
            assertEquals(preset.key, preset.name, preset.displayName(context))
            assertEquals(preset.key, preset.description, preset.displayDescription(context))
        }
    }

    @Test
    fun `every built-in shift type resolves to the translated name and abbreviation`() {
        ShiftType.defaults.forEach { type ->
            assertEquals(type.id, type.name, type.displayName(context))
            assertEquals(type.id, type.abbreviation, type.displayAbbreviation(context))
        }
    }

    @Test
    fun `a shift type the user made keeps their own words`() {
        val mine = ShiftType(
            id = UUID.randomUUID().toString(),
            name = "On call",
            abbreviation = "OC",
            colorArgb = 0xFF123456,
        )

        assertEquals("On call", mine.displayName(context))
        assertEquals("OC", mine.displayAbbreviation(context))
    }

    @Test
    fun `a renamed built-in keeps the new name rather than being translated back`() {
        // Nothing renames a built-in today, but the translation is keyed on the id, so a future
        // shift-type editor must not have its edits overwritten by the shipped name.
        val renamed = ShiftType.DAY.copy(name = "Early", abbreviation = "Er")

        assertEquals("Early", renamed.displayName(context))
        assertEquals("Er", renamed.displayAbbreviation(context))
    }
}

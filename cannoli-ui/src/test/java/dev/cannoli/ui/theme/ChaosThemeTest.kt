package dev.cannoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A theme resolves into all seven slots, and the accent is the only saturated one. */
class ChaosThemeTest {

    @Test
    fun `every theme is in the table and round trips by id`() {
        for (t in ChaosTheme.entries) assertEquals(t, ChaosTheme.fromId(t.id))
        assertEquals(ChaosTheme.ORANGE, ChaosTheme.fromId(null))
        assertEquals(ChaosTheme.ORANGE, ChaosTheme.fromId("nonsense"))
    }

    @Test
    fun `stepping wraps both ways`() {
        assertEquals(ChaosTheme.entries[1], ChaosTheme.step(ChaosTheme.entries[0], 1))
        assertEquals(ChaosTheme.entries.last(), ChaosTheme.step(ChaosTheme.entries[0], -1))
        assertEquals(ChaosTheme.entries[0], ChaosTheme.step(ChaosTheme.entries.last(), 1))
    }

    @Test
    fun `the accent is the highlight and the title`() {
        for (t in ChaosTheme.entries) {
            val c = t.colors()
            assertEquals("${t.id} highlight", t.color, c.highlight)
            assertEquals("${t.id} title", t.color, c.title)
        }
    }

    @Test
    fun `text on a filled row is dark for every accent`() {
        // Cannoli's highlight is a FILL, so its text is dark whatever the accent is. Cream is
        // the case this is worth holding: a light accent with white text would be invisible.
        for (t in ChaosTheme.entries) {
            assertEquals("${t.id} highlightText", ChaosPalette.ON_ACCENT, t.colors().highlightText)
        }
    }

    @Test
    fun `the ground and the text ramp are the same for every theme`() {
        for (t in ChaosTheme.entries) {
            val c = t.colors()
            assertEquals("${t.id} background", ChaosPalette.GROUND, c.background)
            assertEquals("${t.id} text", ChaosPalette.TEXT, c.text)
            assertEquals("${t.id} accent", ChaosPalette.TEXT_SOFT, c.accent)
            assertEquals("${t.id} statusBar", ChaosPalette.TEXT_DIM, c.statusBar)
        }
    }

    @Test
    fun `the hex form has every key the settings store`() {
        val keys = setOf(
            "color_highlight", "color_text", "color_highlight_text", "color_accent",
            "color_title", "color_background", "color_status_bar",
        )
        for (t in ChaosTheme.entries) {
            val hex = t.hexColors()
            assertEquals("${t.id}", keys, hex.keys)
            for ((k, v) in hex) assertTrue("${t.id} $k is $v", Regex("^#[0-9A-F]{6}$").matches(v))
        }
    }

    @Test
    fun `the orange theme is the accent ChaOS ships as its default`() {
        assertEquals("#FF7A2F", ChaosTheme.ORANGE.hexColors()["color_highlight"])
        assertEquals("#17151C", ChaosTheme.ORANGE.hexColors()["color_background"])
    }
}

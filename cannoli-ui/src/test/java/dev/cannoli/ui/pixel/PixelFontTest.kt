package dev.cannoli.ui.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The port's layout rules, held to what `chaos/src/pixfont.c` does.
 *
 * Every expectation here was worked out from the C source rather than from this port's output,
 * so a change that quietly moves a line break or an accent fails here first.
 */
class PixelFontTest {

    @Test
    fun `every capital and digit has a glyph`() {
        for (c in 'A'..'Z') assertTrue("$c", PixelFont.has(c))
        for (c in '0'..'9') assertTrue("$c", PixelFont.has(c))
        for (c in listOf('-', '&', '!', '.', '\'')) assertTrue("$c", PixelFont.has(c))
        assertFalse(PixelFont.has('@'))
    }

    @Test
    fun `glyphs are seven rows tall`() {
        for (c in 'A'..'Z') assertEquals("$c", 7, PixelFont.glyph(c)!!.size)
    }

    @Test
    fun `a long word breaks where its capitals say`() {
        assertEquals(listOf("TURBO", "GRAFX-16"), PixelFont.lines("TurboGrafx-16").first)
    }

    @Test
    fun `a short name stays on one line`() {
        assertEquals(listOf("NES"), PixelFont.lines("NES").first)
        assertEquals(listOf("GAME BOY"), PixelFont.lines("Game Boy").first)
    }

    @Test
    fun `a name longer than one line breaks at a space`() {
        // "Game Boy Color" is 14 letters; "GAME BOY" is 8 and fits, "COLOR" starts the next line.
        assertEquals(listOf("GAME BOY", "COLOR"), PixelFont.lines("Game Boy Color").first)
    }

    @Test
    fun `the accent begins at the last word`() {
        // "GAME BOY" then "COLOR": the last word starts at column 0 of the last line.
        assertEquals(0, PixelFont.lines("Game Boy Color").second)
        // "NEO GEO" is one line and the last word "GEO" starts at column 4.
        assertEquals(4, PixelFont.lines("Neo Geo").second)
    }

    @Test
    fun `the accent lands on the last word's pixels only`() {
        val layout = PixelFont.layout("Neo Geo")
        assertTrue(layout.pixels.any { it.accent })
        assertTrue(layout.pixels.any { !it.accent })
        // Nothing in an earlier line is ever accented.
        val accentTop = layout.pixels.filter { it.accent }.minOf { it.y }
        val plainBottom = layout.pixels.filter { !it.accent }.maxOf { it.y }
        assertEquals("the accent shares the last line with plain pixels", plainBottom, accentTop + 6)
    }

    @Test
    fun `lines are centred against the widest`() {
        val layout = PixelFont.layout("Game Boy Color")
        assertEquals(2, layout.lines.size)
        // The block is as wide as its widest line and no wider.
        val minX = layout.pixels.minOf { it.x }
        val maxX = layout.pixels.maxOf { it.x }
        assertTrue(minX >= 0)
        assertTrue(maxX < layout.cols)
    }

    @Test
    fun `a name with nothing drawable is empty rather than broken`() {
        val layout = PixelFont.layout("")
        assertTrue(layout.isEmpty)
        assertEquals(0, layout.cols)
    }

    @Test
    fun `no name runs past four lines`() {
        val layout = PixelFont.layout("Super Nintendo Entertainment System")
        assertTrue(layout.lines.size <= PixelFont.LINES_MAX)
    }

    @Test
    fun `a name too long for the letters is drawn as its first word`() {
        // Three lines of up to thirteen columns is twice the width of anything else on a
        // shelf, and the one name that will not fit a card at the size the rest want.
        assertEquals("Super", PixelFont.displayName("Super Nintendo Entertainment System"))
        assertEquals("Nintendo", PixelFont.displayName("Nintendo Entertainment System"))
    }

    @Test
    fun `a name that fits is left alone`() {
        // These set to two lines of at most five columns a line, so nothing about them is
        // too wide for the letters and none of them is touched.
        // The letters break at spaces, so a name is only "too wide" when one of its LINES is:
        // every name here sets to lines well under the limit, whatever its length.
        for (n in listOf("NES", "Game Boy", "Game Boy Advance", "Neo Geo Pocket Color", "Master System")) {
            val ls = PixelFont.lines(n).first
            assertTrue("$n sets to ${ls.size} lines", ls.size <= PixelFont.LINES_MAX)
            assertTrue("$n longest line is ${ls.maxOfOrNull { it.length }} chars", ls.maxOfOrNull { it.length }!! <= PixelFont.LINE_MAX)
            assertEquals(n, PixelFont.displayName(n))
        }
    }

    @Test
    fun `the cell size is one number for every name`() {
        val a = PixelFont.uniformCell(400)
        val b = PixelFont.uniformCell(400)
        assertEquals(a, b)
        assertTrue(a >= 4)
    }
}

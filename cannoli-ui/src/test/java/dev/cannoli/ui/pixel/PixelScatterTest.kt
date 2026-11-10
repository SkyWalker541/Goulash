package dev.cannoli.ui.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scatter is deterministic, bounded, and eases the way the C does. */
class PixelScatterTest {

    @Test
    fun `the same name always flies the same way`() {
        val a = PixelScatter.piece("Neo Geo Pocket Color")
        val b = PixelScatter.piece("Neo Geo Pocket Color")
        assertEquals(a.flights, b.flights)
    }

    @Test
    fun `different names fly differently`() {
        val a = PixelScatter.piece("NES")
        val b = PixelScatter.piece("SNES")
        assertNotEquals(a.flights, b.flights)
    }

    @Test
    fun `every pixel is at rest once the name has formed`() {
        val piece = PixelScatter.piece("Game Boy")
        val layout = piece.layout
        for (i in 0 until piece.count) {
            val tx = layout.pixels[i].x * 11f
            val ty = layout.pixels[i].y * 11f
            val (x, y) = PixelScatter.position(
                piece, i, 1f, tx, ty, 11, 1f, PixelScatter.UNBOUNDED, PixelScatter.UNBOUNDED,
            )
            assertEquals("pixel $i x", tx, x, 0.001f)
            assertEquals("pixel $i y", ty, y, 0.001f)
        }
    }

    @Test
    fun `a pixel that has not set off is still out at its scatter point`() {
        val piece = PixelScatter.piece("GBA")
        // t = 0 is before every delay, so nothing has moved.
        for (i in 0 until piece.count) assertEquals(0f, PixelScatter.progress(piece, i, 0f), 0.0001f)
    }

    @Test
    fun `progress is monotonic and never leaves 0 to 1`() {
        val piece = PixelScatter.piece("TurboGrafx-16")
        for (i in 0 until piece.count) {
            var last = -1f
            var t = 0f
            while (t <= 1f) {
                val p = PixelScatter.progress(piece, i, t)
                assertTrue("pixel $i at $t is $p", p in 0f..1f)
                assertTrue("pixel $i went backwards at $t", p >= last)
                last = p
                t += 0.05f
            }
        }
    }

    @Test
    fun `a bounded pixel folds back inside its box`() {
        val piece = PixelScatter.piece("WonderSwan Color")
        val w = 200f
        val h = 200f
        for (i in 0 until piece.count) {
            val (x, y) = PixelScatter.position(piece, i, 0f, 100f, 100f, 11, 1f, w, h)
            assertTrue("x $x", x >= 0f && x <= w)
            assertTrue("y $y", y >= 0f && y <= h)
        }
    }

    @Test
    fun `the sparks fade in only after the name has formed`() {
        assertEquals(0f, PixelScatter.sparkAlpha(0.5f), 0f)
        assertTrue(PixelScatter.sparkAlpha(0.8f) > 0f)
        assertEquals(1f, PixelScatter.sparkAlpha(1f), 0.0001f)
    }

    @Test
    fun `a reflection fades out with depth and stops`() {
        // A cell of 40 makes the reflection 11 cells deep, so 440px below the block is
        // past the end of it.
        val near = PixelScatter.reflection(0f, 20f, 40, 100f)
        // Past the end of it: 11 cells of 40 is 440px, and the reflection starts at the
        // block's bottom plus half a cell.
        val far = PixelScatter.reflection(0f, -800f, 40, 100f)
        assertTrue(near != null)
        assertTrue("a reflection deep below the block is not drawn", far == null)
        assertTrue(near!!.fade > 0f && near.fade <= 1f)
    }
}

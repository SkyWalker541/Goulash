package dev.cannoli.ui.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shelf's arithmetic: wrapping, easing, and one slot per item. */
class PixelShelfTest {

    @Test
    fun `a press moves the cursor at once and the row later`() {
        val shelf = PixelShelf(count = 11)
        shelf.reset(0)
        shelf.stepTo(1, dir = 1, now = 1000)
        assertEquals("the selection is where you pressed", 1, shelf.cursor)
        assertEquals("the row has not started moving yet", 0f, shelf.pos, 0.0001f)
        shelf.tick(1120)
        assertTrue("mid-glide", shelf.pos > 0f && shelf.pos < 1f)
        shelf.tick(2000)
        assertEquals(1f, shelf.pos, 0.0001f)
    }

    @Test
    fun `stepping back from the first item goes the short way round`() {
        val shelf = PixelShelf(count = 11)
        shelf.reset(0)
        shelf.stepTo(10, dir = -1, now = 0)
        // Mid-glide the row is left of where it started: it took the short way round rather
        // than sliding through every other console to the right.
        shelf.tick(120)
        assertTrue("pos ${shelf.pos}", shelf.pos < 0f)
        // And it lands on the last console, which the ring folds back into the list.
        shelf.tick(1000)
        assertEquals(10, shelf.nearest)
        assertEquals(10f, shelf.pos, 0.0001f)
    }

    @Test
    fun `a ring of two takes the direction it was given`() {
        val shelf = PixelShelf(count = 2)
        shelf.reset(0)
        shelf.stepTo(1, dir = 1, now = 0)
        shelf.tick(1000)
        assertEquals(1f, shelf.pos, 0.0001f)

        val back = PixelShelf(count = 2)
        back.reset(1)
        back.stepTo(0, dir = -1, now = 0)
        back.tick(1000)
        assertEquals(0f, back.pos, 0.0001f)
    }

    @Test
    fun `a non-looping row does not wrap`() {
        val shelf = PixelShelf(count = 5, loops = false)
        shelf.reset(0)
        shelf.stepTo(4, dir = 1, now = 0)
        shelf.tick(1000)
        assertEquals(4f, shelf.pos, 0.0001f)
    }

    @Test
    fun `every slot names a real item and no item twice`() {
        val shelf = PixelShelf(count = 11)
        shelf.reset(0)
        for (half in 1..3) {
            val slots = shelf.slots(halfWindow = half)
            assertEquals("window $half", minOf(11, half * 2 + 1), slots.size)
            assertEquals("window $half is unique", slots.size, slots.map { it.item }.distinct().size)
            for (s in slots) assertTrue("item ${s.item}", s.item in 0..10)
        }
    }

    @Test
    fun `the centre slot is the nearest and the only full-size one`() {
        val shelf = PixelShelf(count = 11)
        shelf.reset(3)
        val slots = shelf.slots()
        val centre = slots.first { it.distance == 0f }
        assertEquals(3, centre.item)
        assertEquals(1f, shelf.scaleOf(centre, PixelShelf.SIDE_SCALE), 0.0001f)
        assertEquals(255, shelf.alphaOf(centre, PixelShelf.SIDE_ALPHA))
        for (s in slots.filter { it.distance != 0f }) {
            assertTrue("neighbour scale", shelf.scaleOf(s, PixelShelf.SIDE_SCALE) < 1f)
            assertTrue("neighbour alpha", shelf.alphaOf(s, PixelShelf.SIDE_ALPHA) < 255)
        }
    }

    @Test
    fun `slots are ordered far to near so the centre is drawn last`() {
        val shelf = PixelShelf(count = 11)
        shelf.reset(5)
        val slots = shelf.slots()
        val distances = slots.map { kotlin.math.abs(it.distance) }
        assertEquals(distances.sortedDescending(), distances)
    }

    @Test
    fun `the ease starts and ends at its bounds`() {
        assertEquals(0f, PixelShelf.easeOut(0f), 0.0001f)
        assertEquals(1f, PixelShelf.easeOut(1f), 0.0001f)
        assertTrue(PixelShelf.easeOut(0.5f) > 0.5f) // cubic-out is ahead of linear
    }

    @Test
    fun `a long hold does not leave the row drifting`() {
        val shelf = PixelShelf(count = 11)
        shelf.reset(0)
        var now = 0L
        for (i in 1..30) {
            shelf.stepTo(i % 11, dir = 1, now = now)
            now += 90
            shelf.tick(now)
        }
        shelf.tick(now + 1000)
        assertEquals("the row settles on the cursor's item", shelf.cursor.toFloat(), shelf.pos, 0.0001f)
    }

    @Test
    fun `the visible half shrinks to what the width allows`() {
        assertTrue(PixelShelf.visibleHalf(200f, 1000f) <= PixelShelf.HALF_WINDOW)
        assertTrue(PixelShelf.visibleHalf(10_000f, 100f) >= 1)
    }
}

package dev.cannoli.ui.pixel

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A ChaOS shelf: one item at the centre of the screen and its neighbours either side, slid
 * left and right rather than listed up and down.
 *
 * Ported from `chaos/src/coverflow.c`, reduced to what a systems row needs: a continuous
 * eased position between items, the neighbour scale/alpha ramp, and the wrap that makes the
 * first console's left neighbour the last one. ChaOS also yaws and tilts cards, turns a cube
 * and reflects each card in a floor; none of that is here, because Cannoli has no card art
 * for a system and a row that only ever slides does not need the rest.
 *
 * ## Why `pos` and `cursor` are separate
 *
 * `cursor` is where the selection IS — it moves on the frame of the press, and every action
 * reads it. `pos` is where the row is DRAWN, which eases toward `cursor` over [ANIM_MS]. Draw
 * from `pos` and the cards glide; read `cursor` and pressing A acts on what you aimed at
 * rather than on whatever happened to be under the middle of the screen mid-glide.
 *
 * ## Why the wrap is done on the shortest way round
 *
 * A shelf that loops must not slide the long way when you step from the first console to the
 * last. [stepTo] takes the shorter arc, and a ring of two gets its direction from the caller
 * because "shortest way round" has no answer when both neighbours are one step away.
 *
 * Pure Kotlin, no Compose: the geometry is arithmetic, so it can be tested without a screen.
 */
class PixelShelf(
    val count: Int,
    /** Whether the row wraps: the first item's left neighbour is the last. */
    val loops: Boolean = count >= 2,
) {
    /** Where the row is drawn, in items. Fractional while moving. */
    var pos: Float = 0f
        private set

    /** Where the selection is. Whole, and what a press acts on. */
    var cursor: Int = 0
        private set

    private var from = 0f
    private var target = 0f
    private var startedAt = 0L
    private var animating = false

    /** One step of the row, in the same eased position the cards are drawn at. */
    fun setCursor(index: Int, dir: Int = 0, now: Long = 0L) {
        if (count <= 0) return
        val next = ((index % count) + count) % count
        if (next == cursor && !animating) {
            cursor = next
            return
        }
        stepTo(next, dir, now)
    }

    /** Jump without animating — the first frame of a screen, or a restored position. */
    fun reset(index: Int) {
        if (count <= 0) return
        cursor = ((index % count) + count) % count
        pos = cursor.toFloat()
        from = pos
        target = pos
        animating = false
    }

    /**
     * Move to [index] by the shortest way round, easing from wherever the row currently is.
     *
     * [dir] is only consulted for a ring of two, where the arithmetic cannot say which way the
     * press went — +1 and -1 are both one step, so every press would otherwise pick the same
     * side and the row would rock instead of turning.
     */
    fun stepTo(index: Int, dir: Int = 0, now: Long = 0L) {
        if (count <= 0) return
        val next = ((index % count) + count) % count
        var raw = next - cursor

        if (loops) {
            while (raw > count / 2) raw -= count
            while (raw < -count / 2) raw += count
            if (count == 2 && dir != 0) raw = dir
        }

        cursor = next
        // Start from where the row IS, not from where the last move was headed: holding a
        // direction retargets on every repeat, and taking the live position each time is what
        // makes that one continuous glide rather than a stutter back to the old start.
        from = pos
        target = target + raw
        startedAt = now
        animating = true
    }

    /** Advance the ease to [now]. Returns whether the row is still moving. */
    fun tick(now: Long): Boolean {
        if (!animating) return false
        val u = ((now - startedAt).toFloat() / ANIM_MS).coerceIn(0f, 1f)
        val e = easeOut(u)
        pos = from + (target - from) * e
        if (u >= 1f) {
            pos = target
            animating = false
            // Fold the drawn position back into the ring so it cannot drift away from the
            // items it names over a long session.
            if (loops && count > 0) {
                while (pos >= count) { pos -= count; target -= count; from -= count }
                while (pos < 0f) { pos += count; target += count; from += count }
            }
        }
        return animating
    }

    val isAnimating: Boolean get() = animating

    /** The item `pos` is nearest, wrapped. */
    val nearest: Int
        get() = if (count <= 0) 0 else (((floor(pos + 0.5f).toInt()) % count) + count) % count

    /**
     * The slots to draw: every item within [halfWindow] of the drawn position, each with its
     * distance from the centre.
     *
     * One slot per item even when the window is wider than the list — a two-item ring offers
     * the same console on both sides, and drawing it twice is worse than the tie it fixes. On
     * an exact tie the copy BEHIND the direction of travel wins, so the one you just moved
     * past is the one you see.
     */
    fun slots(halfWindow: Int = HALF_WINDOW, lastDir: Int = 1): List<Slot> {
        if (count <= 0) return emptyList()
        val base = floor(pos + 0.5f).toInt()
        val out = ArrayList<Slot>(halfWindow * 2 + 1)
        for (k in -halfWindow..halfWindow) {
            val i = base + k
            val d = i - pos
            if (abs(d) > halfWindow + 0.5f) continue
            val item = if (loops) ((i % count) + count) % count else i
            if (!loops && (i < 0 || i >= count)) continue
            val held = out.indexOfFirst { it.item == item }
            if (held >= 0) {
                val was = out[held].distance
                val nearer = abs(d) < abs(was)
                val tieBehind = abs(d) == abs(was) && lastDir != 0 && (d < 0) == (lastDir > 0)
                if (nearer || tieBehind) out[held] = Slot(item, d)
                continue
            }
            out.add(Slot(item, d))
        }
        // Far to near, so the centre card is drawn last and wins.
        out.sortByDescending { abs(it.distance) }
        return out
    }

    /** One item's place in the row. [distance] is in items: 0 is centred, ±1 is a neighbour. */
    data class Slot(val item: Int, val distance: Float) {
        /** 1 at the centre, 0 at a full step out: the ramp the scale and alpha ride. */
        val closeness: Float get() = 1f - min(abs(distance), 1f)
    }

    /** The scale a slot is drawn at, between [sideScale] and 1. */
    fun scaleOf(slot: Slot, sideScale: Float): Float =
        sideScale + (1f - sideScale) * slot.closeness

    /** The opacity a slot is drawn at, between [sideAlpha] and 255. */
    fun alphaOf(slot: Slot, sideAlpha: Int): Int =
        (sideAlpha + (255 - sideAlpha) * slot.closeness).toInt().coerceIn(0, 255)

    companion object {
        /** ChaOS's ease: fast off the mark, settling in. */
        const val ANIM_MS = 240f

        /** Three either side: enough for the centre and a neighbour each way. */
        const val HALF_WINDOW = 3

        /** The row's proportions, from `CF_LAYOUT_SYSTEMS`: a big centre, small neighbours. */
        const val SIDE_SCALE = 0.38f
        const val SIDE_ALPHA = 140

        /**
         * The centre card's height as a fraction of the screen's HEIGHT — ChaOS measures the
         * card from the height and takes the width from the aspect, which is why a card is
         * 0.81 tall and only 0.78 of that wide.
         */
        const val CENTRE_SIZE = 0.81f

        /** Where the centre card's middle sits, as a fraction of the height. */
        const val CENTRE_Y = 0.415f

        /** How far below the card's centre the reflection reaches, in half-heights. */
        const val REFLECT = 1.34f

        /**
         * Neighbour spacing in centre card WIDTHS. ChaOS uses 0.82, which puts a neighbour's
         * centre about two thirds of a screen out — far enough that a 0.38 neighbour sits
         * fully on screen with a little to spare.
         */
        const val STEP = 0.82f

        fun easeOut(u: Float): Float {
            val k = 1f - u.coerceIn(0f, 1f)
            return 1f - k * k * k
        }

        /** The distance from the centre an item at [distance] sits, in centre widths. */
        fun offsetOf(distance: Float, step: Float = STEP): Float = distance * step

        /** How many items fit either side before one would be off screen. */
        fun visibleHalf(widthPx: Float, centreWidthPx: Float, step: Float = STEP): Int {
            if (centreWidthPx <= 0f) return HALF_WINDOW
            val per = centreWidthPx * step
            return max(1, min(HALF_WINDOW, (widthPx / 2f / per).toInt() + 1))
        }
    }
}

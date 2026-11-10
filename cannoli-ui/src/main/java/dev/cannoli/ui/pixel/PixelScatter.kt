package dev.cannoli.ui.pixel

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * How a name's pixels fly apart and come together, ported from `chaos/src/pixname.c`.
 *
 * Every pixel gets an angle, a distance and a stagger from a seed taken from the NAME, so a
 * system always assembles the same way — scroll past NES twice and its letters fly in from the
 * same directions. That determinism is the whole reason this is a seeded generator and not
 * `Random()`: a name that assembled differently every time reads as noise, where one that
 * assembles the same way reads as a signature.
 *
 * Pure Kotlin, no Compose: the animation is a function of elapsed time, so it can be tested
 * and so a redraw never has to remember anything between frames.
 */
object PixelScatter {

    /** One pixel's flight: the direction it comes from, how far out it starts, and when. */
    data class Flight(val angle: Float, val distance: Float, val delay: Float)

    /** A name's layout plus each of its pixels' flight. */
    data class Piece(val name: String, val layout: PixelFont.Layout, val flights: List<Flight>) {
        val count: Int get() = layout.pixels.size
    }

    private const val TAU = 6.2831855f

    /**
     * ChaOS's scatter distances: 120 to 520 pixels, in its reference cell of 11px. A name set
     * larger scatters proportionally further, which is what [scale] is for.
     */
    private const val DIST_MIN = 120f
    private const val DIST_SPREAD = 400f

    /** Up to a third of the way through, later pixels are still waiting to start. */
    private const val DELAY_SPREAD = 0.35f

    /** How far a folded pixel is pushed back in, and the period it folds on. */
    private const val EDGE = 8f
    private const val FOLD = 40f

    /** FNV-1a over the name, then an LCG — the same two generators the C uses. */
    private fun seedOf(name: String): Int {
        var seed = -2128831035 // 2166136261u, as a signed Int
        for (c in name) seed = (seed xor c.code) * 16777619
        return seed
    }

    private class Lcg(private var state: Int) {
        fun next(): Int {
            state = state * 1103515245 + 12345
            return state ushr 8
        }

        fun float(): Float = (next() and 0xFFFF).toFloat() / 65535.0f
    }

    /** Lay a name out and give every pixel its flight. Deterministic in [name] alone. */
    fun piece(name: String): Piece {
        val layout = PixelFont.layout(name)
        val rng = Lcg(seedOf(name))
        val flights = ArrayList<Flight>(layout.pixels.size)
        for (i in layout.pixels.indices) {
            val angle = rng.float() * TAU
            val distance = DIST_MIN + rng.float() * DIST_SPREAD
            val delay = rng.float() * DELAY_SPREAD
            flights.add(Flight(angle, distance, delay))
        }
        return Piece(name, layout, flights)
    }

    /**
     * How far along pixel [i] is at [t] (0 scattered, 1 assembled), on ChaOS's cubic-out
     * ease: fast off the mark, settling into place.
     */
    fun progress(piece: Piece, i: Int, t: Float): Float {
        val delay = piece.flights[i].delay
        var k = (t - delay) / (1f - delay)
        if (k < 0f) k = 0f
        if (k > 1f) k = 1f
        val inv = 1f - k
        return 1f - inv * inv * inv
    }

    /**
     * Where pixel [i] is drawn at [t], its resting place being ([tx], [ty]).
     *
     * [scale] multiplies the scatter distance so a large name scatters proportionally, and
     * [boundsW]/[boundsH] keep a loose pixel inside its box — one that leaves folds back in
     * rather than being drawn over the neighbour. Pass [UNBOUNDED] for a name with the whole
     * screen to itself.
     */
    fun position(
        piece: Piece,
        i: Int,
        t: Float,
        tx: Float,
        ty: Float,
        cell: Int,
        scale: Float,
        boundsW: Float,
        boundsH: Float,
    ): Pair<Float, Float> {
        val flight = piece.flights[i]
        val sx = fold(tx + cos(flight.angle) * flight.distance * scale, cell, boundsW)
        val sy = fold(ty + sin(flight.angle) * flight.distance * scale, cell, boundsH)
        val e = progress(piece, i, t)
        return (sx + (tx - sx) * e) to (sy + (ty - sy) * e)
    }

    /** A bound that does not fold: for a name drawn over the whole screen. */
    const val UNBOUNDED = Float.MAX_VALUE

    /** Keep a coordinate inside [0, extent) by folding it back rather than clamping it. */
    private fun fold(v: Float, cell: Int, extent: Float): Float {
        if (extent == UNBOUNDED) return v
        val lo = EDGE
        val hi = extent - EDGE - cell
        if (hi <= lo) return lo
        return when {
            v < lo -> lo + (lo - v) % FOLD
            v > hi -> hi - abs(v) % FOLD
            else -> v
        }
    }

    /**
     * The five escaping pixels the ChaOS logo throws off its top right once a name has
     * formed. Two of them are the logo's cyan whatever the accent is.
     */
    val SPARKS: List<Triple<Int, Int, Boolean>> = listOf(
        Triple(2, -2, false),
        Triple(4, -3, true),
        Triple(5, -1, false),
        Triple(7, -4, true),
        Triple(3, -5, false),
    )

    /**
     * How far the escaping pixels reach past a block's last column, in cells.
     *
     * The logo's flourish sits at the name's top right, so a block is wider than its letters:
     * anything sizing a name to fit a box has to count these or the sparks fall off the edge.
     */
    const val SPARKS_MAX_DX: Int = 8

    /** When the sparks start fading in. */
    const val SPARK_FROM = 0.6f

    /** The sparks' opacity at [t], or 0 while there are none. */
    fun sparkAlpha(t: Float): Float =
        if (t <= SPARK_FROM) 0f else (t - SPARK_FROM) / (1f - SPARK_FROM)

    /**
     * The blurred reflection under a large name, as if it stood on a glossy floor: squashed
     * to half height and gone a line and a half down whatever the name, so three lines
     * reflect no deeper than one.
     *
     * Returns the reflected pixel's top-left and its fade, or null where nothing is drawn.
     */
    fun reflection(x: Float, y: Float, cell: Int, blockBottom: Float): Reflection? {
        val base = blockBottom + cell / 2f
        val my = base + (base - y - cell) * 0.5f
        var fade = 1f - (my - base) / (cell * 11f)
        if (fade <= 0f) return null
        fade *= fade
        return Reflection(x = x, y = my, cell = cell, fade = fade)
    }

    data class Reflection(val x: Float, val y: Float, val cell: Int, val fade: Float)

}

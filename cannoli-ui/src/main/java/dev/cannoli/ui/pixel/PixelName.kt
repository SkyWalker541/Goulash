package dev.cannoli.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope



/**
 * How fast a name forms, and how long it then stays: ChaOS's Transition Speed.
 *
 * `form` is the flight time each way; `hold` is how long a formed name rests before it
 * scatters on its own. Ported from `PN_SPEEDS` in `chaos/src/main.c`.
 */
enum class PixelSpeed(val id: String, val label: String, val formMs: Int, val holdMs: Int) {
    SLOW("slow", "Slow", 1400, 1600),
    NORMAL("normal", "Normal", 950, 1100),
    FAST("fast", "Fast", 450, 500);

    companion object {
        fun fromId(id: String?): PixelSpeed = entries.firstOrNull { it.id == id } ?: NORMAL
        fun step(current: PixelSpeed, direction: Int): PixelSpeed =
            entries[(current.ordinal + direction + entries.size) % entries.size]
    }
}

/**
 * A console's name in the ChaOS pixel letters, assembling while [focused] and scattering
 * when it is not.
 *
 * The state that has to survive between frames is one float — how far formed the name is —
 * so this is a `Canvas` driven by a frame clock rather than anything in the composition. It
 * only runs while the name is moving, which matters: the system list redraws on a handheld
 * and an idle animation that never settles would keep it awake.
 *
 * [accent] is the system's own color, used for the last word of the name (as OS is orange in
 * the ChaOS logo) and for the escaping sparks. [cream] is the letter color, [cyan] the
 * second spark color; both come from the theme so the letters sit in Cannoli's palette
 * rather than ChaOS's.
 */
@Composable
fun PixelName(
    name: String,
    accent: Color,
    modifier: Modifier = Modifier,
    focused: Boolean = true,
    speed: PixelSpeed = PixelSpeed.NORMAL,
    cream: Color = Color(0xFFF4EAD5),
    cyan: Color = Color(0xFF39C5D6),
    cellFraction: Float = 0.94f,
    /** Draw the squashed reflection and drop shadow of the large presentation. */
    decorated: Boolean = true,
    /** How far a formed name rests before it scatters, in ms. 0 holds it for good. */
    holdMs: Int = 0,
    /** Where the block's centre sits vertically, as a fraction of the height. */
    centerYFraction: Float = 0.5f,
) {
    val piece = remember(name) { PixelScatter.piece(name) }

    // Where this name is on its own arc, and where the one before it left off. A name that
    // has never been focused starts scattered, so the first thing you see is it forming.
    var t by remember(name) { mutableFloatStateOf(0f) }

    // The name scatters when the cursor leaves it. On the shelf the callers pass the focus
    // through and holdMs 0; a title that has no focus to lose just forms and stays.
    LaunchedEffect(name, focused, speed, holdMs) {
        val step = 1f / speed.formMs.toFloat()
        var last = withFrameMillis { it }
        var formedAt = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = (now - last).coerceAtMost(100L) // a long gap is one step, not a jump
            last = now
            var next = if (focused) (t + dt * step).coerceAtMost(1f) else (t - dt * step).coerceAtLeast(0f)
            if (focused && next >= 1f && holdMs > 0) {
                if (formedAt == 0L) formedAt = now
                if (now - formedAt >= holdMs) next = (t - dt * step).coerceAtLeast(0f)
            } else if (focused) {
                formedAt = 0L
            }
            if (next == t) break
            t = next
        }
    }

    Canvas(modifier = modifier) {
        if (piece.count == 0) return@Canvas
        val cell = PixelFont.uniformCell(size.width.toInt(), cellFraction)
        val scale = cell / 11f
        val ox = ((size.width - piece.layout.cols * cell) / 2f)
        val oy = size.height * centerYFraction - piece.layout.rows * cell / 2f
        drawPiece(this, piece, t, ox, oy, cell, scale, accent, cream, cyan, decorated)
    }
}

/**
 * Draw a piece at [t] with its block's top-left at ([ox], [oy]).
 *
 * Split out from the Composable so the same drawing can be used for a name over a game, or
 * in a preview, without a second implementation to drift.
 *
 * Three passes, in the order ChaOS draws them and for the reason it does: the reflection
 * first so it sits under everything, then the drop shadow, then the letters. Drawing the
 * shadow after the letters would put it over them.
 */
internal fun drawPiece(
    scope: DrawScope,
    piece: PixelScatter.Piece,
    t: Float,
    ox: Float,
    oy: Float,
    cell: Int,
    scale: Float,
    accent: Color,
    cream: Color,
    cyan: Color,
    decorated: Boolean,
    /** Overall opacity, for a name drawn as a neighbour on a shelf. */
    alpha: Float = 1f,
) {
    if (t <= 0f || alpha <= 0f) return
    val layout = piece.layout
    val blockBottom = oy + layout.rows * cell

    if (decorated) {
        // The accent at 55%, which is the drop shadow's colour in ChaOS.
        val deep = Color(red = accent.red * 0.55f, green = accent.green * 0.55f, blue = accent.blue * 0.55f, alpha = 1f)
        for (i in 0 until piece.count) {
            val p = layout.pixels[i]
            val tx = ox + p.x * cell
            val ty = oy + p.y * cell
            val (x, y) = PixelScatter.position(
                piece, i, t, tx, ty, cell, scale,
                PixelScatter.UNBOUNDED, PixelScatter.UNBOUNDED,
            )
            val e = PixelScatter.progress(piece, i, t)
            val base = if (p.accent) accent else cream

            // Reflection: a wide faint halo around a fainter core, so it reads soft rather
            // than drawn.
            PixelScatter.reflection(x, y, cell, blockBottom)?.let { r ->
                val a = (22f / 255f) * r.fade * e * alpha
                if (a > 0f) {
                    val g = cell.toFloat()
                    scope.drawRect(
                        color = base.copy(alpha = a),
                        topLeft = Offset(x - g, r.y - g / 2f),
                        size = Size(cell + g * 2, cell / 2f + g),
                    )
                    scope.drawRect(
                        color = base.copy(alpha = (28f / 255f) * r.fade * e * alpha),
                        topLeft = Offset(x, r.y),
                        size = Size(cell.toFloat(), cell / 2f + 1),
                    )
                }
            }

            // Drop shadow, offset by a quarter cell.
            val d = (cell / 4).coerceAtLeast(2).toFloat()
            scope.drawRect(
                color = deep.copy(alpha = 0.85f * e * alpha),
                topLeft = Offset(x + d, y + d),
                size = Size(cell.toFloat(), cell.toFloat()),
            )
        }
    }

    for (i in 0 until piece.count) {
        val p = layout.pixels[i]
        val tx = ox + p.x * cell
        val ty = oy + p.y * cell
        val (x, y) = PixelScatter.position(
            piece, i, t, tx, ty, cell, scale,
            PixelScatter.UNBOUNDED, PixelScatter.UNBOUNDED,
        )
        val e = PixelScatter.progress(piece, i, t)
        scope.drawRect(
            color = (if (p.accent) accent else cream).copy(alpha = e * alpha),
            topLeft = Offset(x, y),
            size = Size(cell.toFloat(), cell.toFloat()),
        )
    }

    val spark = PixelScatter.sparkAlpha(t)
    if (spark > 0f) {
        for ((dx, dy, isCyan) in PixelScatter.SPARKS) {
            scope.drawRect(
                color = (if (isCyan) cyan else accent).copy(alpha = spark * alpha),
                topLeft = Offset(
                    ox + (layout.cols + dx - 3) * cell,
                    oy + dy * cell,
                ),
                size = Size(cell.toFloat(), cell.toFloat()),
            )
        }
    }
}

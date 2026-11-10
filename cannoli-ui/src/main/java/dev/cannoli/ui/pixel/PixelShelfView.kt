package dev.cannoli.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cannoli.ui.theme.ChaosPalette
import dev.cannoli.ui.theme.LocalCannoliColors

/** One console on a pixel shelf. */
data class PixelShelfItem(
    val name: String,
    /** The platform tag: which card the deck draws for it, and which accent it wears. */
    val tag: String,
    /** The console's own accent: its name's last word, its sparks, its rail marker. */
    val accent: Color,
    /** The quiet line under the name: "23 games", or nothing. */
    val meta: String = "",
)

/**
 * A ChaOS shelf: one card large in the middle, its neighbours small and dim either side,
 * slid left and right, each reflected in the floor under it.
 *
 * The geometry is `CF_LAYOUT_SYSTEMS` from `chaos/src/coverflow.c`, in its own terms: the
 * centre card is 0.81 of the SCREEN'S HEIGHT tall (the width follows from the card's 0.78
 * aspect), its middle sits 0.415 of the way down, a neighbour is 0.38 of that size and 140
 * of 255 opaque, neighbours are spaced 0.82 card widths apart, and the reflection reaches
 * 1.34 half-heights below the card's centre.
 *
 * Two things are done differently, both because of what is under them. A card's reflection is
 * drawn as the card's own pixels flipped and faded rather than by re-rendering the art
 * mirrored — the card is an image, so a flip is exact and costs one draw call. And the name is
 * not drawn on top of the card: the deck's cards have their label baked in, so the row shows
 * the card and nothing else, which is what makes it look like the hardware on a shelf.
 *
 * The shelf is a view of state the caller already holds: [selectedIndex] comes in, nothing
 * here decides what is selected.
 */
@Composable
fun PixelShelfView(
    items: List<PixelShelfItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    /** A card per tag. Missing ones fall through to the deck's DEFAULT card. */
    cardFor: (String) -> ImageBitmap? = { null },
    /** Whether the row wraps: the last console's right neighbour is the first. */
    loops: Boolean = true,
    /** The quiet line under the row. */
    metaStyle: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    /** Which of the two ChaOS themes this is: names flying together, or the card deck. */
    mode: PixelShelfMode = PixelShelfMode.NAMES,
    /** How fast a name forms, for [PixelShelfMode.NAMES]. */
    speed: PixelSpeed = PixelSpeed.NORMAL,
    cream: Color = ChaosPalette.CREAM,
    cyan: Color = ChaosPalette.CYAN,
) {
    if (items.isEmpty()) return

    val shelf = remember(items.size, loops) { PixelShelf(items.size, loops) }
    var frame by remember { mutableLongStateOf(0L) }

    // Each card's own name, 0 scattered to 1 formed. A card eases toward 1 while it is the
    // cursor and toward 0 while it is not, which is the whole transition: the row can move
    // fast and the names still take their own time, exactly as `pn_card` gives each one its
    // own t.
    val cardFormed = remember(items.size) { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    // One clock for the row's glide, running only while the row moves: a shelf at rest must
    // not keep a handheld awake.
    LaunchedEffect(shelf) {
        shelf.reset(selectedIndex)
        var now = withFrameMillis { it }
        frame = now
        while (true) {
            now = withFrameMillis { it }
            val moving = shelf.tick(now)
            frame = now
            if (!moving) break
        }
    }

    // A new selection from anywhere — a press, a restore, a page jump — eases the row to it.
    LaunchedEffect(selectedIndex, items.size) {
        val dir = when {
            selectedIndex > shelf.cursor -> 1
            selectedIndex < shelf.cursor -> -1
            else -> 0
        }
        shelf.stepTo(selectedIndex, dir, System.currentTimeMillis())
        var now = withFrameMillis { it }
        while (shelf.tick(now)) {
            frame = now
            now = withFrameMillis { it }
        }
        frame = now
    }

    // The names' clock. It runs while any card's name is between 0 and 1, so a settled shelf
    // still sleeps.
    LaunchedEffect(selectedIndex, items.size, mode) {
        if (mode != PixelShelfMode.CARDS) return@LaunchedEffect
        val step = 1f / speed.formMs.toFloat()
        var now = withFrameMillis { it }
        while (true) {
            val dt = 16f
            var moving = false
            for (i in items.indices) {
                val want = if (i == selectedIndex) 1f else 0f
                val cur = cardFormed[i] ?: 0f
                if (cur != want) {
                    val next = if (want > cur) (cur + step * dt).coerceAtMost(1f)
                               else (cur - step * dt).coerceAtLeast(0f)
                    cardFormed[i] = next
                    moving = true
                }
            }
            if (!moving) break
            now = withFrameMillis { it }
        }
    }

    val colors = LocalCannoliColors.current
    val selected = items[selectedIndex.coerceIn(items.indices)]

    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // ChaOS takes the card from the HEIGHT and the width from the aspect, so a card is
        // tall on a wide screen and the row still fills it.
        val cardH = h * PixelShelf.CENTRE_SIZE
        val cardW = cardH * SystemCards.CARD_ASPECT
        val stepPx = cardW * PixelShelf.STEP

        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame
            if (mode == PixelShelfMode.NAMES) return@Canvas
            val slots = shelf.slots(halfWindow = PixelShelf.visibleHalf(w, cardW), lastDir = 1)
            val cy = h * PixelShelf.CENTRE_Y
            // Far to near, so the centre card is drawn last and wins — the same order ChaOS
            // sorts its slots into, and for the same reason.
            for (slot in slots) {
                val item = items[slot.item]
                val scale = shelf.scaleOf(slot, PixelShelf.SIDE_SCALE)
                val alpha = shelf.alphaOf(slot, PixelShelf.SIDE_ALPHA) / 255f
                val cardCx = w / 2f + slot.distance * stepPx
                val cardWpx = cardW * scale
                val cardHpx = cardH * scale
                drawCard(
                    card = cardFor(item.tag),
                    cx = cardCx,
                    cy = cy,
                    w = cardWpx,
                    h = cardHpx,
                    alpha = alpha,
                    reflect = PixelShelf.REFLECT,
                    accent = item.accent,
                )
                // The name ON the card, in the pixel letters: it flies together when the
                // cursor lands on this card and scatters when it leaves, which is
                // `pn_card` in pixname.c. The card art carries no label of its own, so
                // this is the only name on it.
                drawCardName(
                    name = item.name,
                    cx = cardCx,
                    cy = cy,
                    cardW = cardWpx,
                    cardH = cardHpx,
                    alpha = alpha,
                    accent = item.accent,
                    cream = cream,
                    cyan = cyan,
                    // Formed on the card under the cursor, empty on a neighbour: the name
                    // you leave scatters as the row moves and the one you land on
                    // assembles, which is `pn_card`. The card's own t is eased by
                    // [cardFormed], a frame clock shared by the row.
                    t = cardFormed[slot.item] ?: 0f,
                )
            }
        }

        if (mode == PixelShelfMode.NAMES) {
            // ONE system at a time: its name flies together from scattered pixels. The one
            // you left is simply gone — there is no card on screen for it to leave from,
            // which is why ChaOS only ever forms a name here and never scatters one
            // (pixname.c, pn_big).
            val item = items[shelf.cursor.coerceIn(items.indices)]
            PixelName(
                name = item.name,
                accent = item.accent,
                speed = speed,
                cream = cream,
                cyan = cyan,
                cellFraction = 0.94f,
                centerYFraction = 0.44f,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (selected.meta.isNotEmpty()) {
            Text(
                text = selected.meta,
                style = metaStyle.copy(textAlign = TextAlign.Center),
                color = colors.statusBar,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = (h * 0.83f).dp),
            )
        }
    }
}

/**
 * A card's name, filling the card's upper panel.
 *
 * The name is the card now — there is no console art under it — so it takes the whole area
 * above the rule rather than sitting where a small caption used to, and it is set as large as
 * that area allows.
 *
 * ONE SIZE FOR EVERY CONSOLE, as ChaOS has it and as the shelf does: the name you are looking
 * at is the same weight whether it is three letters or twenty, so stepping along the row
 * changes the word and never the type. Sizing each name to its own card was tried and
 * rejected for exactly that reason — NES grew to twice the size of Neo Geo Pocket Color and
 * the row stopped reading as one deck.
 *
 * The size is therefore set from the WORST case a name can present, which is the font's
 * maximum: [PixelFont.LINE_MAX] letters a line, [PixelFont.LINES_MAX] lines, plus the
 * escaping pixels beside the last column. That is the size at which any name at all fits its
 * card, and every shorter name simply leaves more air. [PixelFont.uniformCell] is the same
 * rule the big centred name uses.
 *
 * Only the card under the cursor has its name formed; a neighbour's is empty. That is the
 * transition: the name you left scatters to nothing as the row moves, and the one you land on
 * assembles out of loose pixels.
 */
private fun DrawScope.drawCardName(
    name: String,
    cx: Float,
    cy: Float,
    cardW: Float,
    cardH: Float,
    alpha: Float,
    accent: Color,
    cream: Color,
    cyan: Color,
    t: Float,
) {
    if (t <= 0f || cardW <= 0f || cardH <= 0f) return
    val piece = PixelScatter.piece(name)
    if (piece.count == 0) return
    // The panel above the rule, as fractions of the card: the deck puts the rule at 587 of
    // 820, and the panel is everything above it.
    val panelH = cardH * (587f / 820f)
    val panelTop = cy - cardH / 2f

    // ONE cell size, from the worst name the font can set rather than from this one: a name
    // never changes size as you step along the row. The escaping pixels reach past the last
    // column, so the widest line is [PixelFont.LINE_MAX] letters plus that reach.
    val widestCols = PixelFont.LINE_MAX * 6 - 1 + PixelScatter.SPARKS_MAX_DX
    val byWidth = cardW * 0.90f / widestCols
    val byHeight = panelH * 0.94f / (PixelFont.LINES_MAX * (PixelFont.GLYPH_H + PixelFont.LINE_GAP))
    val cell = minOf(byWidth, byHeight).toInt().coerceAtLeast(2)
    val ox = cx - piece.layout.cols * cell / 2f
    // Centred in the panel, and if a name runs taller than it, the top of the block is pulled
    // back inside rather than the name being shrunk: the size belongs to the shelf, not to
    // the name.
    val blockH = piece.layout.rows * cell
    val oy = (panelTop + (panelH - blockH) / 2f).coerceAtLeast(panelTop)

    drawPiece(
        scope = this,
        piece = piece,
        t = t,
        ox = ox,
        oy = oy,
        cell = cell,
        scale = cell / 11f,
        accent = accent,
        cream = cream,
        cyan = cyan,
        decorated = false,
        alpha = alpha,
    )
}

/**
 * One card, with its reflection in the floor.
 *
 * The reflection is the card flipped about its own bottom edge and faded from [REFLECT] of the
 * card's alpha at the seam to nothing at [PixelShelf.REFLECT] half-heights below the centre —
 * which is the same distance `draw_card` measures, so a short card and a tall one reflect to
 * the same depth rather than to the same proportion.
 */
private fun DrawScope.drawCard(
    card: ImageBitmap?,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    alpha: Float,
    reflect: Float,
    accent: Color,
) {
    if (w <= 0f || h <= 0f) return
    val left = cx - w / 2f
    val top = cy - h / 2f
    val size = Size(w, h)

    if (card == null) {
        // No deck for this console: a slab in its accent, so the row still has a card.
        drawRect(
            color = ChaosPalette.CARD_BOTTOM.copy(alpha = alpha),
            topLeft = Offset(left, top),
            size = size,
        )
        drawRect(
            color = accent.copy(alpha = alpha * 0.6f),
            topLeft = Offset(left, top + h * 0.71f),
            size = Size(w, h * 0.015f),
        )
        return
    }

    drawImage(
        image = card,
        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
        alpha = alpha,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
    )

    // The reflection: the card flipped, below the seam, fading downward.
    val seam = top + h
    val depth = (cy + h * reflect / 2f - seam).coerceAtLeast(0f)
    if (depth <= 0f) return
    withTransform({
        translate(0f, seam)
        scale(1f, -1f, pivot = Offset(0f, 0f))
        translate(0f, -seam)
    }) {
        drawImage(
            image = card,
            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
            alpha = alpha * 0.35f,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
        )
    }
    // A wash over the mirrored half so it fades out rather than stopping dead at its bottom.
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color.Transparent,
            1f to ChaosPalette.GROUND.copy(alpha = 1f),
            startY = seam,
            endY = seam + depth,
        ),
        topLeft = Offset(left, seam),
        size = Size(w, depth),
    )
}

/**
 * The two themes ChaOS ships for its systems shelf, from `cards.h`:
 *
 * - [NAMES] is `pixelbig` — no cards at all, the console's name alone, flying together large
 *   in the middle. One system at a time, because the name IS the screen.
 * - [CARDS] is `pixel` — the deck, one card large in the middle and its neighbours shrunk and
 *   faded to the sides.
 *
 * They are one setting in ChaOS (UI Theme) rather than two booleans, and one here too.
 */
enum class PixelShelfMode { NAMES, CARDS }

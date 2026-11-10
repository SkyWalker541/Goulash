package dev.cannoli.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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

    // Whether the shelf has been placed on its first index yet: the first pass jumps, every
    // one after it slides.
    var primed by remember { mutableStateOf(false) }

    // Each card's own name, 0 scattered to 1 formed. A card eases toward 1 while it is the
    // cursor and toward 0 while it is not, which is the whole transition: the row can move
    // fast and the names still take their own time, exactly as `pn_card` gives each one its
    // own t.
    val cardFormed = remember(items.size) { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    // A new selection from anywhere — a press, a restore, a page jump — eases the row to it.
    LaunchedEffect(selectedIndex, items.size) {
        // `dir` is 0 on the first pass, so the shelf lands on the restored index instead of
        // sliding to it from 0 — which is what opened the screen with a carousel turning by
        // itself.
        val first = !primed
        val dir = if (first) 0 else when {
            selectedIndex > shelf.cursor -> 1
            selectedIndex < shelf.cursor -> -1
            else -> 0
        }
        if (first) {
            shelf.reset(selectedIndex)
            primed = true
            frame = System.currentTimeMillis()
            return@LaunchedEffect
        }
        shelf.stepTo(selectedIndex, dir, System.currentTimeMillis())
        var now = withFrameMillis { it }
        while (shelf.tick(now)) {
            frame = now
            now = withFrameMillis { it }
        }
        frame = now
    }


    // The names' clock: every card's name eases toward 1 while it is the cursor and toward 0
    // while it is not, on the same frame clock as the row.
    //
    // SEEDED from the selection, not from zero. A press changes `selectedIndex`, which cancels
    // and restarts this effect — and starting every card at 0 meant the name you had just
    // moved TO began scattered and the one you left vanished, so the row looked like it had
    // lost its names rather than turned over. Seed the card you are on at 1 and only the
    // ones you are moving between animate.
    LaunchedEffect(selectedIndex, items.size, mode, speed) {
        if (mode != PixelShelfMode.CARDS) return@LaunchedEffect
        if (cardFormed.isEmpty()) {
            for (i in items.indices) cardFormed[i] = if (i == selectedIndex) 1f else 0f
        }
        val step = 1f / speed.formMs.toFloat()
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
            withFrameMillis { it }
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

        // ONE cell size for the whole shelf, from the biggest name on it: the largest name
        // fits its card and every shorter one leaves more air. Recomputed only when the
        // names or the card's size change, so stepping along the row never resizes anything.
        val shelfCell = remember(items, cardW.toInt(), cardH.toInt()) {
            // The names as the shelf can actually set them: a name too long for the letters
            // is drawn as its first word, so that is what the size is measured against.
            val (cols, rows) = PixelFont.biggestBlock(items.map { PixelFont.displayName(it.name) })
            val panelH = cardH * (587f / 820f)
            val byWidth = cardW * 0.90f / (cols + PixelScatter.SPARKS_MAX_DX)
            val byHeight = panelH * 0.94f / rows.coerceAtLeast(1)
            minOf(byWidth, byHeight).toInt().coerceAtLeast(2)
        }

        // Snapshot reads, so a change to either redraws the row: the glide clock and every
        // card's name clock.
        val formedSnapshot = androidx.compose.runtime.remember(cardFormed) {
            androidx.compose.runtime.snapshotFlow { cardFormed.toMap() }
        }
        val formedNow by formedSnapshot.collectAsState(initial = cardFormed.toMap())

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
                    t = formedNow[slot.item] ?: 0f,
                    cell = shelfCell,
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
 * The size is set from the WORST NAME ON THIS SHELF, not from the font's theoretical maximum:
 * one size, chosen so the biggest name in the list fits its card, and every shorter name
 * leaves more air. Sizing from the font's maximum instead left a row of short names small to
 * make room for a name that was not on it — and a name longer than any name in the list could
 * still overrun the card, which is what pushed some of them off the edge.
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
    cell: Int,
) {
    if (t <= 0f || cardW <= 0f || cardH <= 0f || cell < 2) return
    // The name as the letters can set it: a name too long for nine-wide glyphs is drawn as
    // its first word, which is what the cell size was measured against.
    val piece = PixelScatter.piece(PixelFont.displayName(name))
    if (piece.count == 0) return
    // The panel above the rule, as fractions of the card: the deck puts the rule at 587 of
    // 820, and the panel is everything above it.
    val panelH = cardH * (587f / 820f)
    val panelTop = cy - cardH / 2f
    val ox = cx - piece.layout.cols * cell / 2f
    // Centred in the panel. The size is the shelf's, so a name never changes size; one that
    // runs past the panel is centred anyway rather than pushed down onto the rule.
    val blockH = piece.layout.rows * cell
    val oy = panelTop + (panelH - blockH) / 2f

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

package dev.cannoli.ui.pixel

/**
 * ChaOS's pixel letters, ported from `chaos/src/pixfont.c`.
 *
 * The logo's own letterforms: every capital, the digits and a few marks, 5x7 (I, 1 and the
 * marks narrower), a column between letters. This is original artwork drawn pixel by pixel,
 * not a bitmap font — C, O, S and the shapes of the rest follow the ChaOS logo.
 *
 * A name is laid out in capitals, at most [LINE_MAX] letters to a line, broken at spaces —
 * and inside a long word where its capitals say so ("TurboGrafx-16" is "TURBO" over
 * "GRAFX-16") — with its last word in the console's accent, as OS is orange in the logo.
 *
 * Pure Kotlin with no Compose and no Android in it, so the layout can be unit tested against
 * the same answers the C version gives.
 */
object PixelFont {

    const val LINE_MAX = 9
    const val LINES_MAX = 4
    const val GLYPH_H = 7

    /** Empty rows between lines. */
    const val LINE_GAP = 2

    /** One pixel of a laid-out name. */
    data class Pixel(val x: Int, val y: Int, val accent: Boolean)

    /** A name set into a block [cols] x [rows] pixels, in capitals. */
    data class Layout(
        val pixels: List<Pixel>,
        val cols: Int,
        val rows: Int,
        val lines: List<String>,
        /** The column, within the last line, the accent starts at; -1 for none. */
        val accentFrom: Int,
    ) {
        val isEmpty: Boolean get() = pixels.isEmpty()
    }

    private val GLYPHS: Map<Char, Array<String>> = mapOf(
        'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to arrayOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to arrayOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
        'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to arrayOf("###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"),
        'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to arrayOf("#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"),
        'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
        'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf(".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
        '-' to arrayOf("...", "...", "...", "###", "...", "...", "..."),
        '&' to arrayOf(".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#"),
        '!' to arrayOf("#", "#", "#", "#", "#", ".", "#"),
        '.' to arrayOf(".", ".", ".", ".", ".", ".", "#"),
        '\'' to arrayOf("#", "#", ".", ".", ".", ".", "."),
        ' ' to arrayOf("..", "..", "..", "..", "..", "..", ".."),
    )

    /** The glyph for [c], uppercased, or null when the font has none. */
    fun glyph(c: Char): Array<String>? = GLYPHS[c.uppercaseChar()]

    /** The widest line a name sets to, in columns: the width of its block. */
    fun blockWidth(name: String): Int = layout(name).cols

    /** Whether a character has a glyph (anything else is drawn as a space). */
    fun has(c: Char): Boolean = glyph(c) != null

    private fun glyphOrSpace(c: Char): Array<String> = glyph(c) ?: GLYPHS.getValue(' ')

    private fun width(g: Array<String>): Int = g[0].length

    /**
     * The words of a name, with a long one broken where its capitals say ("TurboGrafx").
     *
     * The break lands before the first capital that follows a lower-case letter, which is
     * exactly where a camel-cased hardware name puts its seam: Turbo|Grafx, Neo|Geo (though
     * that one breaks at the space first), Wonder|Swan.
     */
    internal fun words(name: String): List<String> {
        val out = ArrayList<String>(8)
        for (raw in name.split(' ')) {
            if (raw.isEmpty()) continue
            if (raw.length > LINE_MAX) {
                var brk = -1
                for (j in 1 until raw.length) {
                    if (raw[j].isUpperCase() && raw[j - 1].isLowerCase()) { brk = j; break }
                }
                if (brk > 0) {
                    out.add(raw.substring(0, brk))
                    out.add(raw.substring(brk))
                    continue
                }
            }
            out.add(raw)
        }
        return out
    }

    private fun lineWidth(s: String): Int {
        var w = 0
        for ((i, c) in s.withIndex()) {
            w += (if (i > 0) 1 else 0) + width(glyphOrSpace(c))
        }
        return w
    }

    /**
     * The lines a name breaks into, in capitals, and the column within the last line its
     * accent starts at.
     *
     * Greedy and left-to-right: a word joins the current line if it fits in [LINE_MAX] with a
     * space, and otherwise starts a new one. The accent always begins at the name's last
     * word, wherever that word landed.
     */
    fun lines(name: String): Pair<List<String>, Int> {
        val words = words(name)
        val lines = ArrayList<String>(LINES_MAX)
        var accentFrom = 0
        for (w in words) {
            val cur = lines.lastOrNull()
            if (cur != null && cur.length + 1 + w.length <= LINE_MAX) {
                lines[lines.lastIndex] = "$cur $w"
            } else {
                if (lines.size == LINES_MAX) break
                lines.add(w)
            }
            accentFrom = lines.last().length - w.length
        }
        return lines.map { it.uppercase() } to accentFrom
    }

    /**
     * The name's pixels, centred line by line in a block [cols] x [rows], with the last
     * word in the accent.
     */
    fun layout(name: String): Layout {
        val (lines, accentFrom) = lines(name)
        val widest = lines.maxOfOrNull { lineWidth(it) } ?: 0
        val pixels = ArrayList<Pixel>(256)
        var rows = 0

        for ((l, line) in lines.withIndex()) {
            var x0 = (widest - lineWidth(line)) / 2
            val y0 = l * (GLYPH_H + LINE_GAP)
            rows = y0 + GLYPH_H
            for ((i, c) in line.withIndex()) {
                val g = glyphOrSpace(c)
                val gw = width(g)
                for (r in 0 until GLYPH_H) {
                    val row = g[r]
                    for (x in 0 until gw) {
                        if (row[x] == '#') {
                            pixels.add(
                                Pixel(
                                    x = x0 + x,
                                    y = y0 + r,
                                    accent = l == lines.lastIndex && i >= accentFrom,
                                )
                            )
                        }
                    }
                }
                x0 += gw + 1
            }
        }
        return Layout(pixels = pixels, cols = widest, rows = rows, lines = lines, accentFrom = accentFrom)
    }

    /**
     * A name short enough to set in the pixel letters, or its own first word.
     *
     * The letters are nine wide, so "Super Nintendo Entertainment System" cannot be set at all:
     * it breaks into three lines whose longest is thirteen columns — half again as wide as
     * anything else on a shelf, and the one name that will not fit a card at the size the rest
     * of them want. The console's everyday name is its first word, which is also what somebody
     * says out loud: SNES's is Super, Nintendo Entertainment System's is Nintendo.
     *
     * The test is the block's COLUMNS, not its length, because the letters break at spaces —
     * "Game Boy" is eight characters and only five columns a line, where a name that has to
     * split mid-word runs to thirteen.
     */
    fun displayName(name: String, maxChars: Int = LINE_MAX): String {
        // Judged by the LINES, not by the name's length: the letters break at spaces, so
        // "Game Boy" is eight characters and sets as two five-character lines. A name is only
        // cut when one of its lines is longer than the letters should ever run — which is the
        // case that produced SUPER / NINTENDO / ENTERTAINMENT / SYSTEM.
        val ls = lines(name).first
        if (ls.isEmpty()) return name
        if (ls.size <= LINES_MAX && ls.all { it.length <= maxChars }) return name
        return name.substringBefore(' ')
    }

    /**
     * The biggest block a LIST of names presents: the widest columns and the tallest rows
     * across every name in it.
     *
     * This is what a shelf sizes from, so that every name on it is set at one size and that
     * size is the largest the shelf's own names allow — not the largest the font can hold,
     * which leaves a row of short names small to make room for a name that is not there.
     */
    fun biggestBlock(names: List<String>): Pair<Int, Int> {
        var cols = 0
        var rows = 0
        for (n in names) {
            val l = layout(n)
            if (l.cols > cols) cols = l.cols
            if (l.rows > rows) rows = l.rows
        }
        return cols to rows
    }

    /**
     * One cell size for every name, so moving from NES to Neo Geo Pocket Color changes the
     * word and not the scale.
     *
     * ChaOS sizes this from the widest line any name can have — [LINE_MAX] letters at six
     * columns each, plus the escaping pixels beside it — across most of the available width.
     * Lines are what differ between names, never the size.
     */
    fun uniformCell(widthPx: Int, widthFraction: Float = 0.94f): Int {
        val widest = LINE_MAX * 6 - 1 + 5
        return maxOf(4, (widthPx * widthFraction).toInt() / widest)
    }
}

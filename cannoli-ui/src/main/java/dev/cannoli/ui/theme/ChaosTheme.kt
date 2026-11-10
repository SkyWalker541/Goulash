package dev.cannoli.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ChaOS's colour scheme, as Cannoli can wear it.
 *
 * ChaOS does not hand you seven colours to set one at a time. It picks ONE named accent and
 * derives everything from it: the ground is a fixed near-black, the text a four-step ramp
 * that never changes, and the accent is the only saturated thing on screen — panel borders,
 * the heading's rule, the position rail, notices, and the last word of a console's name. That
 * is the idea this file ports, not a swatch list.
 *
 * The seven slots [CannoliColors] already has are how it reaches the widgets. A theme resolves
 * into all seven at once, so choosing one changes the whole launcher rather than one row.
 *
 * ## Why the ground and the ramp are not the accent
 *
 * Because ChaOS does not tint them either, and because a themed ground at the wrong lightness
 * is how a handheld becomes unreadable in daylight. [GROUND] and the [TEXT] ramp are fixed
 * across every ChaOS theme; only [accent] moves. A user who wants a different ground still has
 * the individual colour rows below the theme row, which is why this is a *starting point*
 * written into the existing settings rather than a replacement for them.
 */
enum class ChaosTheme(
    val id: String,
    val label: String,
    /** 0xRRGGBB. The one saturated colour: borders, rules, rail, the name's last word. */
    val accent: Long,
    /** Whether the accent is light enough that it needs its own darker text on it. */
    val light: Boolean = false,
) {
    /** ChaOS, the logo's orange. The default. */
    ORANGE("chaos", "ChaOS Orange", 0xFF7A2F),

    /** The logo's other colour, and the one the pixel names read best in. */
    CYAN("cyan", "ChaOS Cyan", 0x39C5D6),

    /** The logo's paper. */
    CREAM("cream", "Cream", 0xF4EAD5, light = true),

    /** The accent ChaOS started from. */
    TORTOS("tortos", "TortOS Cyan", 0x3DD6FF),

    LIME("lime", "Lime", 0x9BE15D, light = true),
    GOLD("gold", "Gold", 0xFFC23D, light = true),
    RED("red", "Red", 0xFF4F5A),
    PINK("pink", "Pink", 0xFF6FB5),
    PURPLE("purple", "Purple", 0xA98BFF),
    BLUE("blue", "Blue", 0x5B8CFF);

    companion object {
        fun fromId(id: String?): ChaosTheme = entries.firstOrNull { it.id == id } ?: ORANGE
        fun step(current: ChaosTheme, direction: Int): ChaosTheme =
            entries[(current.ordinal + direction + entries.size) % entries.size]
    }
}

/**
 * The fixed half of the scheme, from `chaos/src/ui.h`.
 *
 * ChaOS's ground is the logo's dark violet-black (0x17151C), not pure black — a black ground
 * next to a saturated accent reads as a hole, where this reads as a room. The text ramp is
 * four steps, and an ordinary unselected row sits at [TEXT_SOFT]: selection is said by the
 * highlight behind it, not by dimming everything else down to it.
 */
object ChaosPalette {
    val GROUND = Color(0xFF17151C)
    val GROUND_DEEP = Color(0xFF121017)

    val TEXT = Color(0xFFEDEDF2)
    val TEXT_SOFT = Color(0xFFC6C9D6)
    val TEXT_DIM = Color(0xFF9499AC)

    /** The logo's paper, for pixel letters that are not accented. */
    val CREAM = Color(0xFFF4EAD5)

    /** The logo's cyan, the second spark colour. */
    val CYAN = Color(0xFF39C5D6)

    /** Text on a filled row: near-black rather than black, so it sits in the ground's tone. */
    val ON_ACCENT = Color(0xFF17151C)

    /** The pixel cards' ground, top and bottom of a vertical gradient. */
    val CARD_TOP = Color(0xFF222633)
    val CARD_BOTTOM = Color(0xFF191C26)
}

/**
 * A console's own accent, from ChaOS's `systems.cfg`.
 *
 * ChaOS samples these from the hardware itself, which is why they are muted: a Master System's
 * red is plastic red, not a UI red. They are the one thing on a shelf that is not the theme's,
 * and the reason a row of consoles does not read as one flat color.
 *
 * An unknown tag gets the theme's accent rather than nothing, so a console Cannoli knows and
 * ChaOS does not is still legible.
 */
val CONSOLE_ACCENTS: Map<String, Color> = mapOf(
    "NES" to Color(0xFFC4443A),
    "SMS" to Color(0xFFC12216),
    "GB" to Color(0xFF7E9B47),
    "MD" to Color(0xFF3E82D6),
    "PCE" to Color(0xFFE8641E),
    "GG" to Color(0xFF00B589),
    "SFC" to Color(0xFF816EBA),
    "SNES" to Color(0xFF816EBA),
    "NGP" to Color(0xFFC2478F),
    "GBC" to Color(0xFFE0B23A),
    "NGPC" to Color(0xFF00AA4F),
    "GBA" to Color(0xFF6B5BD6),
)

/** A console's accent by platform tag, or [fallback] for one ChaOS never colored. */
fun platformAccent(tag: String, fallback: Color = ChaosPalette.TEXT_SOFT): Color =
    CONSOLE_ACCENTS[tag.uppercase()] ?: fallback

/** The accent as a Compose colour. */
val ChaosTheme.color: Color get() = Color(0xFF000000L or accent)

/**
 * The seven slots this theme resolves to.
 *
 * Read the assignments as the sentence the theme is making:
 *
 * - [CannoliColors.highlight] is the accent, because in Cannoli the selected row is a filled
 *   pill and in ChaOS the selected thing is the only thing in the accent.
 * - [CannoliColors.highlightText] is the ground, because the accent is a fill and text on it
 *   has to be dark. [ChaosTheme.light] records the accents where that matters most.
 * - [CannoliColors.text] is the ramp's top step, [CannoliColors.accent] the soft one. Accent
 *   in Cannoli means "the quieter value text", not "the brand colour", so it takes the ramp's
 *   second step rather than the accent hue — otherwise every value column on the screen would
 *   be orange and the one selected row would stop being the loudest thing on it.
 * - [CannoliColors.title] is the accent, which is where a heading's rule lives.
 * - [CannoliColors.statusBar] is the dim step, because a status bar is context, not content.
 */
fun ChaosTheme.colors(): CannoliColors {
    val acc = color
    val ground = ChaosPalette.GROUND
    return CannoliColors(
        highlight = acc,
        text = ChaosPalette.TEXT,
        highlightText = ChaosPalette.ON_ACCENT,
        accent = ChaosPalette.TEXT_SOFT,
        title = acc,
        background = ground,
        statusBar = ChaosPalette.TEXT_DIM,
    )
}

/** The same palette as the hex strings [dev.cannoli.scorza.settings.SettingsRepository] stores. */
fun ChaosTheme.hexColors(): Map<String, String> {
    val c = colors()
    fun hex(v: Color): String {
        val r = (v.red * 255f + 0.5f).toInt()
        val g = (v.green * 255f + 0.5f).toInt()
        val b = (v.blue * 255f + 0.5f).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }
    return linkedMapOf(
        "color_highlight" to hex(c.highlight),
        "color_text" to hex(c.text),
        "color_highlight_text" to hex(c.highlightText),
        "color_accent" to hex(c.accent),
        "color_title" to hex(c.title),
        "color_background" to hex(c.background),
        "color_status_bar" to hex(c.statusBar),
    )
}

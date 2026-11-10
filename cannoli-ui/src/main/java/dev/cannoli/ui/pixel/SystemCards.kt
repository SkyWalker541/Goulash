package dev.cannoli.ui.pixel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * A console's card, as ChaOS's deck draws it.
 *
 * The card is 640x820 with a 0.78 aspect, and it is not a frame around art — the stock, the
 * accent rule and the label are all IN the image, so a card is drawn by scaling it, not by
 * compositing. That is why this is a lookup and a decode rather than a layout.
 *
 * Cards ship in `assets/system_cards/`, named by platform tag, generated from the deck's
 * measurements by `tools/gencards.py`. A console with no card of its own falls back to
 * `DEFAULT`, so a platform Cannoli knows and ChaOS does not still gets a card rather than a
 * hole in the row.
 */
object SystemCards {

    /** The deck's proportions, which every number in [PixelShelf] is measured against. */
    const val CARD_ASPECT = 0.78f

    private val cache = HashMap<String, ImageBitmap?>()

    /**
     * The card for a platform tag, decoded once and kept. Null when even the fallback is gone.
     *
     * [plain] picks the deck with the console's name left OFF the card, which is the one the
     * Pixel Cards theme needs: there the name is composed on top in the pixel letters, so it
     * can fly together as the cursor arrives and scatter when it leaves. The two decks are
     * otherwise identical — same stock, same rule, same art — so switching themes changes
     * nothing but whether the label is part of the picture.
     */
    fun load(assets: android.content.res.AssetManager, tag: String, plain: Boolean = false): ImageBitmap? {
        val key = (if (plain) "plain:" else "") + tag.uppercase()
        cache[key]?.let { return it }
        if (key in cache) return null

        val dir = if (plain) "system_cards_plain" else "system_cards"
        val bitmap = decode(assets, "$dir/${tag.uppercase()}.png")
            ?: decode(assets, "$dir/DEFAULT.png")
        cache[key] = bitmap
        return bitmap
    }

    /** A card from a file, for a user who has dropped their own into the launcher's folder. */
    fun loadFile(file: File): ImageBitmap? {
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun decode(assets: android.content.res.AssetManager, path: String): ImageBitmap? =
        try {
            assets.open(path).use { stream ->
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }

    /** Forget the decoded cards: after a theme change, or to free memory on a low-RAM device. */
    fun clear() = cache.clear()
}

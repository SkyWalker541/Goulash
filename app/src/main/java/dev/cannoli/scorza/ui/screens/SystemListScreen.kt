package dev.cannoli.scorza.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cannoli.scorza.R
import dev.cannoli.scorza.settings.ArtScale
import dev.cannoli.scorza.ui.viewmodel.SystemListViewModel
import dev.cannoli.scorza.ui.viewmodel.SystemListViewModel.ListItem
import dev.cannoli.ui.ButtonStyle
import dev.cannoli.ui.components.BottomBar
import dev.cannoli.ui.components.ConfirmOverlay
import dev.cannoli.ui.components.List
import dev.cannoli.ui.components.PillRowText
import dev.cannoli.ui.components.ScreenBackground
import dev.cannoli.ui.components.ScreenTitle
import dev.cannoli.ui.components.WithoutScreenTitle
import dev.cannoli.ui.components.footerReservation
import dev.cannoli.ui.components.listTitleSpacing
import dev.cannoli.ui.pixel.PixelName
import dev.cannoli.ui.pixel.PixelShelfItem
import dev.cannoli.ui.pixel.SystemCards
import dev.cannoli.ui.pixel.PixelShelfMode
import dev.cannoli.ui.pixel.PixelShelfView
import dev.cannoli.ui.pixel.PixelSpeed
import dev.cannoli.ui.theme.ChaosPalette
import dev.cannoli.ui.theme.ChaosTheme
import dev.cannoli.ui.theme.platformAccent
import dev.cannoli.ui.theme.color
import dev.cannoli.ui.components.pillItemHeight
import dev.cannoli.ui.components.screenInsets
import dev.cannoli.ui.theme.LocalCannoliColors
import dev.cannoli.ui.theme.Radius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which ChaOS shelf the system list wears, mirroring ChaOS's UI Theme row (`cards.h`).
 *
 * OFF is the ordinary Cannoli list, and the default. PIXEL_NAMES is ChaOS's `pixelbig`: one
 * console at a time, its name flying together large in the middle. PIXEL_CARDS is `pixel`: the
 * deck, one card large and its neighbours faded to the sides.
 */
enum class ChaosShelf { OFF, PIXEL_NAMES, PIXEL_CARDS }

/**
 * A row of the system list as a shelf item.
 *
 * Only a system has an accent of its own; everything else in the list - a folder, a game, a
 * collection - wears the theme's accent, so the shelf is never colorless.
 */
private fun SystemListViewModel.ListItem.toShelfItem(): PixelShelfItem = when (this) {
    is SystemListViewModel.ListItem.PlatformItem -> PixelShelfItem(
        name = platform.displayName,
        tag = platform.tag,
        accent = platformAccent(platform.tag),
        meta = if (platform.gameCount > 0) "${platform.gameCount} games" else "",
    )
    is SystemListViewModel.ListItem.GameItem -> PixelShelfItem(
        name = displayName,
        tag = (item as? dev.cannoli.scorza.model.ListItem.RomItem)?.rom?.platformTag.orEmpty(),
        accent = platformAccent(
            (item as? dev.cannoli.scorza.model.ListItem.RomItem)?.rom?.platformTag.orEmpty()
        ),
    )
    is SystemListViewModel.ListItem.CollectionItem -> PixelShelfItem(name = name, tag = "", accent = Color.White)
    is SystemListViewModel.ListItem.RecentlyPlayedItem -> PixelShelfItem(name = "Recently Played", tag = "", accent = Color.White)
    is SystemListViewModel.ListItem.FavoritesItem -> PixelShelfItem(name = "Favorites", tag = "FAVORITES", accent = Color.White)
    is SystemListViewModel.ListItem.CollectionsFolder -> PixelShelfItem(name = "Collections", tag = "", accent = Color.White)
    is SystemListViewModel.ListItem.ToolsFolder -> PixelShelfItem(name = name, tag = "", accent = Color.White)
    is SystemListViewModel.ListItem.PortsFolder -> PixelShelfItem(name = name, tag = "", accent = Color.White)
}

@Composable
fun SystemListScreen(
    viewModel: SystemListViewModel,
    backgroundImagePath: String? = null,
    backgroundTint: Int = 0,
    listFontSize: TextUnit = 22.sp,
    listLineHeight: TextUnit = 32.sp,
    listVerticalPadding: Dp = 8.dp,
    onListStateChanged: ((androidx.compose.foundation.lazy.LazyListState?) -> Unit)? = null,
    title: String = "",
    mainMenuQuit: Boolean = false,
    artWidth: Int = 40,
    artScale: ArtScale = ArtScale.FIT,
    /** The ChaOS theme the colors came from, or "" for none: turns on the pixel names. */
    chaosTheme: String = "",
    /** Which ChaOS shelf to draw: a list with a pixel name beside it, or the shelf itself. */
    chaosShelf: ChaosShelf = ChaosShelf.OFF,
    chaosPixelSpeed: PixelSpeed = PixelSpeed.NORMAL,
    resumableGames: Set<String> = emptySet(),
    swapPlayResume: Boolean = false,
    fiveGameHandheld: Boolean = false,
    buttonStyle: ButtonStyle = ButtonStyle(),
) {
    val state by viewModel.state.collectAsState()
    val itemHeight = pillItemHeight(listLineHeight, listVerticalPadding)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.scrollTarget.coerceAtLeast(0))

    DisposableEffect(Unit) {
        onDispose { viewModel.savePosition(listState.firstVisibleItemIndex) }
    }
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.firstVisibleIndex = it }
    }

    val recentlyPlayedLabel = stringResource(R.string.label_recently_played)
    val favoritesLabel = stringResource(R.string.label_favorites)
    val collectionsLabel = stringResource(R.string.label_collections)

    val selectedItem = state.items.getOrNull(state.selectedIndex)

    // The pixel names: a ChaOS theme is chosen AND the cursor is on a system, not on a
    // folder or a game. Only a system has a name in the pixel letters, because the letters
    // are consoles' - "Recently Played" is not one, and neither is a game.
    val chaosThemeOrNull = chaosTheme.takeIf { it.isNotEmpty() }?.let { ChaosTheme.fromId(it) }
    val pixelSystem = (selectedItem as? ListItem.PlatformItem)?.platform
    val showPixelName = chaosThemeOrNull != null && pixelSystem != null

    // A name that has just been left keeps drawing while it scatters, so scrolling the shelf
    // takes one name apart as it builds the next rather than snapping between them. It is
    // held here rather than in the pixel name so the box it draws in outlives the cursor.
    var lastPixelName by remember { mutableStateOf<String?>(null) }
    var lastPixelAccent by remember { mutableStateOf(Color.White) }
    if (showPixelName) {
        lastPixelName = pixelSystem?.displayName
        lastPixelAccent = chaosThemeOrNull?.color ?: Color.White
    }

    val artPath = (selectedItem as? ListItem.GameItem)?.artFile?.absolutePath
    val selectedArt by produceState<ImageBitmap?>(null, artPath) {
        value = if (artPath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options()
                    opts.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(artPath, opts)
                    val maxDim = 1024
                    var sampleSize = 1
                    while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) sampleSize *= 2
                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = sampleSize
                    BitmapFactory.decodeFile(artPath, opts)?.asImageBitmap()
                } catch (_: Exception) { null }
            }
        } else null
    }
    val showArt = state.hasGameItems && selectedArt != null && artWidth > 0
    val showPixelPane = showPixelName || lastPixelName != null
    val duplicateGameNames = remember(state.items) {
        state.items.asSequence()
            .filterIsInstance<ListItem.GameItem>()
            .groupingBy { it.displayName }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }

    ScreenBackground(backgroundImagePath = backgroundImagePath, backgroundTint = backgroundTint) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(288.dp)
                )
            }
        } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenInsets())
        ) {
            WithoutScreenTitle(active = title.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = footerReservation())
            ) {
            if (title.isNotEmpty()) {
                ScreenTitle(
                    text = title,
                    fontSize = listFontSize,
                    lineHeight = listLineHeight
                )
                Spacer(modifier = Modifier.height(listTitleSpacing()))
            }
            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(if (fiveGameHandheld) R.string.empty_fgh_collection else R.string.empty_content),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = listFontSize,
                            lineHeight = listLineHeight
                        ),
                        color = LocalCannoliColors.current.text
                    )
                }
            } else if (chaosShelf != ChaosShelf.OFF) {
                // A ChaOS shelf: the consoles ARE the screen. The list is still the source of
                // truth for what is selected, so input, reorder and every action carry on
                // working; this only changes what is drawn.
                val shelfItems = remember(state.items) {
                    state.items.map { it.toShelfItem() }
                }
                // The row is the whole screen, so the footer reservation comes out of the
                // shelf's own height rather than being padded away from it: the name has to
                // sit in the space that is actually there, not in the space minus a bar.
                val assets = LocalContext.current.assets
                PixelShelfView(
                    items = shelfItems,
                    selectedIndex = state.selectedIndex,
                    loops = true,
                    // The Pixel Cards theme composes each card's name in the pixel letters so
                    // it can fly in, so it takes the deck with the label left off.
                    cardFor = { tag -> SystemCards.load(assets, tag, plain = true) },
                    metaStyle = MaterialTheme.typography.bodyMedium,
                    mode = if (chaosShelf == ChaosShelf.PIXEL_CARDS) PixelShelfMode.CARDS else PixelShelfMode.NAMES,
                    speed = chaosPixelSpeed,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(if (showArt || showPixelPane) Modifier.fillMaxWidth(1f - artWidth / 100f) else Modifier.fillMaxWidth())
                ) {
                List(
                    items = state.items,
                    selectedIndex = state.selectedIndex,
                    itemHeight = itemHeight,
                    scrollTarget = state.scrollTarget,
                    listState = listState,
                    reorderMode = state.reorderMode,
                    onListStateChanged = onListStateChanged,
                    key = if (state.reorderMode) null else { _, item ->
                        when (item) {
                            is ListItem.RecentlyPlayedItem -> "recently_played"
                            is ListItem.FavoritesItem -> "favorites"
                            is ListItem.CollectionsFolder -> "collections"
                            is ListItem.PlatformItem -> item.platform.tag
                            is ListItem.CollectionItem -> "col:${item.id}"
                            is ListItem.GameItem -> "game:${item.recentKey}"
                            is ListItem.ToolsFolder -> "tools"
                            is ListItem.PortsFolder -> "ports"
                        }
                    }
                ) { _, item, isSelected ->
                    val label = when (item) {
                        is ListItem.RecentlyPlayedItem -> recentlyPlayedLabel
                        is ListItem.FavoritesItem -> favoritesLabel
                        is ListItem.CollectionsFolder -> collectionsLabel
                        is ListItem.PlatformItem -> item.platform.displayName
                        is ListItem.CollectionItem -> item.name
                        is ListItem.GameItem -> item.displayName
                        is ListItem.ToolsFolder -> item.name
                        is ListItem.PortsFolder -> item.name
                    }
                    val showReorder = state.reorderMode && isSelected && (item is ListItem.PlatformItem || item is ListItem.ToolsFolder || item is ListItem.PortsFolder || item is ListItem.CollectionItem || item is ListItem.GameItem)
                    val tagSuffix = (item as? ListItem.GameItem)
                        ?.takeIf { it.displayName in duplicateGameNames }
                        ?.let { gi ->
                            val platformTag = (gi.item as? dev.cannoli.scorza.model.ListItem.RomItem)?.rom?.platformTag
                            val platformPart = platformTag?.let { "(${it.uppercase()})" }
                            listOfNotNull(platformPart, gi.tags).joinToString(" ").ifEmpty { null }
                        }
                    PillRowText(
                        label = label,
                        isSelected = isSelected,
                        fontSize = listFontSize,
                        lineHeight = listLineHeight,
                        verticalPadding = listVerticalPadding,
                        showReorderIcon = showReorder,
                        tagSuffix = tagSuffix,
                    )
                }
                }
                if (showArt) {
                    val artTopPadding = if (fiveGameHandheld && title.isEmpty()) {
                        pillItemHeight(listLineHeight, 4.dp) + 8.dp
                    } else {
                        0.dp
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = artTopPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        // A system under the cursor wears its name in the pixel letters,
                        // assembling as it arrives and scattering when it leaves. Anything
                        // else in the list shows its art, or nothing.
                        if (showPixelPane) {
                            val name = if (showPixelName) pixelSystem!!.displayName else lastPixelName!!
                            val accent = if (showPixelName) (chaosThemeOrNull?.color ?: Color.White) else lastPixelAccent
                            PixelName(
                                name = name,
                                accent = accent,
                                speed = chaosPixelSpeed,
                                focused = showPixelName,
                                cream = ChaosPalette.CREAM,
                                cyan = ChaosPalette.CYAN,
                                modifier = Modifier.fillMaxSize(),
                            )
                            return@Box
                        }
                        val art = selectedArt ?: return@Box
                        val artModifier: Modifier
                        val artContentScale: ContentScale
                        when (artScale) {
                            ArtScale.FIT -> { artModifier = Modifier.fillMaxSize(); artContentScale = ContentScale.Fit }
                            ArtScale.ORIGINAL -> { artModifier = Modifier.wrapContentSize(); artContentScale = ContentScale.None }
                            ArtScale.FIT_WIDTH -> { artModifier = Modifier.fillMaxWidth(); artContentScale = ContentScale.FillWidth }
                            ArtScale.FIT_HEIGHT -> { artModifier = Modifier.fillMaxHeight(); artContentScale = ContentScale.FillHeight }
                        }
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            modifier = artModifier.clip(RoundedCornerShape(Radius.Lg)),
                            contentScale = artContentScale,
                            filterQuality = FilterQuality.High
                        )
                    }
                }
            }
            }
            }
            }

            val selectedGameItem = selectedItem as? ListItem.GameItem
            val hasResumeState = selectedGameItem != null && resumableGames.contains(selectedGameItem.recentKey)
            val playLabel = if (selectedGameItem != null) stringResource(R.string.label_play) else stringResource(R.string.label_select)
            val resumeLabel = stringResource(R.string.label_resume)
            val rightItems = if (fiveGameHandheld) {
                if (state.items.isEmpty()) {
                    emptyList()
                } else {
                    buildList {
                        if (hasResumeState && swapPlayResume) {
                            add(buttonStyle.north to playLabel)
                            add(buttonStyle.confirm to resumeLabel)
                        } else {
                            if (hasResumeState) add(buttonStyle.north to resumeLabel)
                            add(buttonStyle.confirm to playLabel)
                        }
                    }
                }
            } else if (state.items.isEmpty()) {
                listOf(buttonStyle.west to stringResource(R.string.label_kitchen))
            } else {
                listOf(buttonStyle.confirm to stringResource(R.string.label_select))
            }
            val leftItems = buildList {
                if (mainMenuQuit) add(buttonStyle.back to stringResource(R.string.label_quit))
                if (fiveGameHandheld) {
                    add(buttonStyle.west to stringResource(R.string.label_settings))
                } else {
                    add(buttonStyle.north to stringResource(R.string.label_settings))
                }
            }
            BottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                leftItems = leftItems,
                rightItems = rightItems
            )
        }
        }
    }

}

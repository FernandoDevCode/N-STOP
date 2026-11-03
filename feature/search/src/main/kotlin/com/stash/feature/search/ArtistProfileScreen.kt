package com.stash.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.media.preview.LosslessUrlPrefetcher
import com.stash.core.ui.components.AlbumsRowSkeleton
import com.stash.core.ui.components.DiscoveryErrorCard
import com.stash.core.ui.components.PopularListSkeleton
import com.stash.core.model.TrackItem
import com.stash.data.ytmusic.model.AlbumSummary
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import com.stash.core.ui.components.bottomChromePadding

/**
 * Artist Profile screen.
 *
 * Layout (top → bottom):
 *  1. [ArtistHero] — paints from nav args on first frame.
 *  2. "Popular"   — up to 5 [PreviewDownloadRow]s from `popular`.
 *  3. "Albums"    — [AlbumsRow] horizontal rail.
 *  4. "Singles & EPs" — [SinglesRow] horizontal rail.
 *  5. "Fans also like" — [RelatedArtistsRow].
 *
 * While the first cache emission is in flight the sections render
 * shimmer skeletons rather than jumping layout when the data arrives.
 * If the cold cache miss throws, the hero keeps painting from nav args
 * and the shelves are replaced by [DiscoveryErrorCard] with a Retry
 * button (spec §6.2).
 *
 * `userMessages` from the VM are surfaced through a local [Scaffold]'s
 * Snackbar host — refresh failures show a one-liner but the cached data
 * keeps rendering underneath, matching §3.4's stale-while-revalidate UX.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistProfileScreen(
    onBack: () -> Unit,
    onNavigateToAlbum: (album: AlbumSummary) -> Unit,
    onNavigateToArtist: (artistId: String, name: String, avatarUrl: String?) -> Unit,
    vm: ArtistProfileViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    // Optional image lookup is UI-owned, never part of catalog or playback VM.
    val wordmarkUrl by produceState<String?>(initialValue = null, key1 = state.hero.name) {
        value = ArtistWordmarkLookup.resolve(state.hero.name)
    }
    // The artist wordmark is a secondary overlay only. Never substitute
    // it for the photograph selected by the ViewModel.
    val context = LocalContext.current
    val photoPalette by produceState(
        initialValue = ArtistNeutralPalette,
        key1 = state.hero.avatarUrl,
    ) {
        value = artistPaletteFromPhoto(context, state.hero.avatarUrl)
    }
    // The photographic continuation is sampled off-thread once per image.
    val continuation by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = state.hero.avatarUrl,
    ) {
        value = artistPhotoContinuation(context, state.hero.avatarUrl)
    }
    var photoFrameHeightPx by remember(state.hero.avatarUrl) { mutableFloatStateOf(0f) }
    val smoothAtmosphere by animateColorAsState(
        targetValue = photoPalette.surface,
        label = "artistReadingSurface",
    )
    val smoothBridge by animateColorAsState(
        targetValue = photoPalette.bridge,
        label = "artistPhotoBridge",
    )
    val smoothHeroTone by animateColorAsState(
        targetValue = photoPalette.hero,
        label = "artistPhotoTone",
    )
    val featuredAlbum = remember(state.albums, state.singles) {
        selectArtistFeaturedAlbum(state.albums, state.singles)
    }
    val previewState by vm.delegate.previewState.collectAsStateWithLifecycle()
    val downloadingIds by vm.delegate.downloadingIds.collectAsStateWithLifecycle()
    val downloadedIds by vm.delegate.downloadedIds.collectAsStateWithLifecycle()
    val previewLoadingId by vm.delegate.previewLoadingId.collectAsStateWithLifecycle()
    val currentPlayingYoutubeId by vm.currentPlayingYoutubeId.collectAsStateWithLifecycle()
    val streamingEnabled by vm.streamingEnabled.collectAsStateWithLifecycle()
    var showPhotoEditor by rememberSaveable { mutableStateOf(false) }
    var showPhotoLinkDialog by rememberSaveable { mutableStateOf(false) }
    var photoLink by rememberSaveable { mutableStateOf("") }
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) vm.setLocalPhoto(uri) }
    var showStreamingSheet by rememberSaveable { mutableStateOf(false) }
    val streamingSheetState = androidx.compose.material3.rememberModalBottomSheetState()
    val playlistSheetItem by vm.playlistSheetItem.collectAsStateWithLifecycle()
    val userPlaylists by vm.userPlaylists.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        merge(
            vm.userMessages,
            vm.delegate.userMessages,
        ).collect { message -> snackbar.showSnackbar(message) }
    }

    // Tap-to-album focus: when the Now Playing screen navigated here with a
    // focusAlbum, locate it in the Albums/Singles shelves and bring it into
    // view. Two-axis: the outer LazyColumn scrolls to the section (below the
    // hero + Popular, i.e. off-screen on landing) so the inner rail's own
    // scroll + highlight (see AlbumsRow) is actually visible.
    val listState = rememberLazyListState()
    // Preserve absolute page position after the hero leaves LazyColumn's
    // composition. Tracking actual item sizes avoids both index-based fade
    // jumps and a photo backdrop that disappears as soon as item 0 is recycled.
    val itemHeightPx = remember { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index to it.size } }
            .collect { items ->
                items.forEach { (index, height) ->
                    if (height > 0) itemHeightPx[index] = height
                }
            }
    }
    val absoluteScrollPx by remember(listState, itemHeightPx) {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            if (index == 0) listState.firstVisibleItemScrollOffset.toFloat()
            else {
                val measured = (0 until index).map { itemHeightPx[it] }
                if (measured.any { it == null }) null
                else measured.filterNotNull().sum().toFloat() +
                    listState.firstVisibleItemScrollOffset
            }
        }
    }

    val statusBarNeedsContrast by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 180
        }
    }
    val statusBarScrim by animateColorAsState(
        targetValue = if (statusBarNeedsContrast) smoothAtmosphere.copy(alpha = .98f)
            else Color.Transparent,
        label = "artistStatusBarContrast",
    )
    val focus = remember(state.focusAlbum, state.albums, state.singles) {
        findAlbumFocus(state.focusAlbum, state.albums, state.singles)
    }
    // The hero (0) and featured-release slot (1) are fixed.
    // Each shelf contributes two LazyColumn items; count the optional ones
    // centrally so "open this album" lands at the right section.
    val positions = remember(
        state.popular.isNotEmpty(), state.albums.isNotEmpty(),
        state.singles.isNotEmpty(), state.related.isNotEmpty(), state.about != null,
    ) {
        artistSectionPositions(
            hasSongs = state.popular.isNotEmpty(),
            hasAlbums = state.albums.isNotEmpty(),
            hasSingles = state.singles.isNotEmpty(),
            hasRelated = state.related.isNotEmpty(),
            hasAbout = state.about != null,
        )
    }
    // The editorial section navigator is an overlay, not another LazyColumn
    // item: deep-link indices and existing shelf anchors remain unchanged.
    val artistTabs = remember(positions) {
        listOfNotNull(
            positions.songs?.let { "Músicas" to it },
            positions.albums?.let { "Álbuns" to it },
            positions.singles?.let { "Singles e EPs" to it },
            positions.about?.let { "Sobre" to it },
        )
    }
    // A positive scroll offset positions the heading BELOW the floating tabs.
    val tabScrollOffsetPx = with(LocalDensity.current) { 112.dp.roundToPx() }
    val selectedArtistTab by remember(listState, artistTabs) {
        derivedStateOf {
            // Use the visible heading's actual pixel position: when a chapter
            // approaches the overlay, selection advances before its first row
            // becomes the LazyColumn's first visible item.
            val chapterIndices = artistTabs.map { it.second }.toSet()
            val visibleHeading = listState.layoutInfo.visibleItemsInfo
                .filter { it.index in chapterIndices && it.offset <= tabScrollOffsetPx }
                .maxByOrNull { it.index }
            visibleHeading?.index
                ?: artistTabs.lastOrNull { it.second < listState.firstVisibleItemIndex }?.second
                ?: artistTabs.firstOrNull()?.second
        }
    }
    // Switch to sticky only after the in-flow tab row has passed its
    // resting position. A fixed first-visible-index check caused jumps.
    val stickyTopPx = with(LocalDensity.current) { 90.dp.roundToPx() }
    val showArtistTabs by remember(listState, artistTabs, stickyTopPx) {
        derivedStateOf {
            val inFlow = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 1 }
            artistTabs.size > 1 && when {
                inFlow != null -> inFlow.offset + inFlow.size <= stickyTopPx
                listState.firstVisibleItemIndex > 1 -> true
                else -> false
            }
        }
    }
    val sectionOuterIndex = remember(focus, positions) {
        when (focus?.shelf) {
            AlbumShelf.ALBUMS -> positions.albums
            AlbumShelf.SINGLES -> positions.singles
            null -> null
        }
    }
    val scrollScope = rememberCoroutineScope()
    var focusHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sectionOuterIndex, state.status) {
        val target = sectionOuterIndex ?: return@LaunchedEffect
        if (focusHandled) return@LaunchedEffect
        if (state.status is ArtistProfileStatus.Fresh || state.status is ArtistProfileStatus.Stale) {
            focusHandled = true
            listState.animateScrollToItem(target)
        }
    }

    Scaffold(
        // The outer NavHost deliberately draws the hero behind the Android
        // clock. A nested Material3 Scaffold must NOT reserve a second top
        // inset; buttons are kept clear by ArtistHero.statusBarsPadding().
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomChromePadding())) },
    ) { inner ->
        // V4: a single photographed canvas under the hero AND content.
        // Texture starts inside the image's fade region (measured in actual
        // pixels), keeps the image's horizontal light/dark distribution, and
        // dissolves to charcoal through alpha in the bitmap itself.
        Box(
            modifier = Modifier.fillMaxSize().padding(inner).drawBehind {
                drawRect(ArtistNeutralAtmosphere)
                val frameEnd = absoluteScrollPx?.let { photoFrameHeightPx - it }
                if (continuation != null && frameEnd != null && photoFrameHeightPx > 0f) {
                    val bleed = photoFrameHeightPx * .36f
                    val startY = frameEnd - bleed
                    val heightPx = 740.dp.toPx()
                    if (startY < size.height && startY + heightPx > 0f) {
                        // Preserve photo alignment: lateral drift on a
                        // source-derived backdrop produced visible seams and
                        // giant moving columns in the actual Redmi capture.
                        drawImage(
                            image = continuation!!,
                            dstOffset = IntOffset(0, startY.toInt()),
                            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), heightPx.toInt().coerceAtLeast(1)),
                            alpha = 1f,
                        )
                        // Reading scrim joins the photo-derived material
                        // rather than covering it with a second opaque panel.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                .58f to Color(0x3320252B),
                                1f to ArtistNeutralAtmosphere,
                                startY = frameEnd + 60.dp.toPx(),
                                endY = startY + heightPx,
                            ),
                        )
                    }
                }
            },
        ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomChromePadding(96.dp)),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ArtistHero(
                    hero = state.hero,
                    status = state.status,
                    socials = state.about?.socials.orEmpty(),
                    wordmarkUrl = wordmarkUrl,
                    bridgeAtmosphere = smoothBridge,
                    heroAtmosphere = smoothHeroTone,
                    onHeroArtworkLoadFailed = vm::onHeroArtworkLoadFailed,
                    onEditPhoto = { showPhotoEditor = true },
                    onPhotoFrameMeasured = { photoFrameHeightPx = it.toFloat() },
                    onBack = onBack,
                    onPlayArtist = vm::playArtist,
                    onStartRadio = vm::startRadio,
                    streamingEnabled = streamingEnabled,
                    onStreamingClick = { showStreamingSheet = true },
                    onOpenAbout = positions.about?.let { target ->
                        {
                            scrollScope.launch {
                                listState.animateScrollToItem(target, scrollOffset = tabScrollOffsetPx)
                            }
                            Unit
                        }
                    },
                    featuredContent = featuredAlbum?.let { album ->
                        @Composable {
                            ArtistFeaturedAlbum(album = album, onOpen = onNavigateToAlbum)
                        }
                    },
                )
            }
            // The formerly empty reserved index is now the integrated
            // chapter selector. This keeps existing section indices stable.
            item(key = "artist-featured-release") {
                if (artistTabs.size > 1) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                    ) {
                        ArtistSectionTabs(
                            tabs = artistTabs,
                            selectedIndex = selectedArtistTab,
                            onSelect = { target ->
                                scrollScope.launch {
                                    listState.animateScrollToItem(target, scrollOffset = tabScrollOffsetPx)
                                }
                            },
                        )
                    }
                }
            }

            when (val status = state.status) {
                is ArtistProfileStatus.Error -> item {
                    DiscoveryErrorCard(
                        title = "Couldn't load artist",
                        message = status.message,
                        onRetry = vm::retry,
                    )
                }
                ArtistProfileStatus.Loading -> if (state.popular.isEmpty()) {
                    item { PopularListSkeleton() }
                    item { AlbumsRowSkeleton() }
                } else {
                    contentSections(
                        state = state,
                        previewState = previewState,
                        downloadingIds = downloadingIds,
                        downloadedIds = downloadedIds,
                        previewLoadingId = previewLoadingId,
                    currentPlayingYoutubeId = currentPlayingYoutubeId,
                        losslessPrefetcher = vm.losslessPrefetcher,
                        onPreview = { track -> vm.delegate.previewTrack(track) },
                        onStopPreview = vm.delegate::stopPreview,
                        onDownload = { vm.delegate.downloadTrack(it.toTrackItem()) },
                        onPlayNext = vm::onPlayNext,
                        onAddToQueue = vm::onAddToQueue,
                        onStartRadio = vm::onStartRadio,
                        onRequestAddToPlaylist = vm::onRequestAddToPlaylist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        wordmarkSource = ArtistWordmarkLookup.attribution(wordmarkUrl),
                        focus = focus,
                    )
                }
                ArtistProfileStatus.Fresh,
                ArtistProfileStatus.Stale -> contentSections(
                    state = state,
                    previewState = previewState,
                    downloadingIds = downloadingIds,
                    downloadedIds = downloadedIds,
                    previewLoadingId = previewLoadingId,
                    currentPlayingYoutubeId = currentPlayingYoutubeId,
                    losslessPrefetcher = vm.losslessPrefetcher,
                    onPreview = { track -> vm.delegate.previewTrack(track) },
                    onStopPreview = vm.delegate::stopPreview,
                    onDownload = { vm.delegate.downloadTrack(it.toTrackItem()) },
                    onPlayNext = vm::onPlayNext,
                    onAddToQueue = vm::onAddToQueue,
                    onStartRadio = vm::onStartRadio,
                    onRequestAddToPlaylist = vm::onRequestAddToPlaylist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist,
                    wordmarkSource = ArtistWordmarkLookup.attribution(wordmarkUrl),
                    focus = focus,
                )
            }
        }
        // Preserve edge-to-edge photo at the top, but protect Android's clock
        // when album art and labels scroll behind the status bar.
        Box(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp)
                .background(
                    Brush.verticalGradient(
                        0f to statusBarScrim,
                        1f to statusBarScrim.copy(alpha = 0f),
                    ),
                ),
        )
        // A quiet floating chapter selector appears only after the hero has
        // left the viewport. It retains the Android clock inset and does not
        // introduce another destination or affect the app's bottom navigation.
        if (showArtistTabs) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 9.dp)
                    .shadow(8.dp)
                    // Fully opaque reading surface: album artwork and text
                    // must not bleed through the sticky chapter navigation.
                    .background(ArtistNeutralAtmosphere),
            ) {
                ArtistSectionTabs(
                    tabs = artistTabs,
                    selectedIndex = selectedArtistTab,
                    onSelect = { target ->
                        scrollScope.launch {
                            listState.animateScrollToItem(target, scrollOffset = tabScrollOffsetPx)
                        }
                    },
                )
            }
        }
        } // artist atmosphere canvas

        if (showStreamingSheet) {
            com.stash.core.ui.components.streaming.StreamingModeSheet(
                streamingEnabled = streamingEnabled,
                onSelect = { requested ->
                    vm.applyStreamingMode(requested)
                    showStreamingSheet = false
                },
                onDismiss = { showStreamingSheet = false },
                sheetState = streamingSheetState,
            )
        }

        if (showPhotoEditor) {
            AlertDialog(
                onDismissRequest = { showPhotoEditor = false },
                title = { Text("Fotografia de " + state.hero.name) },
                text = {
                    Column {
                        Text("A mudança ficará salva somente neste aparelho.")
                        TextButton(onClick = {
                            showPhotoEditor = false
                            photoLink = ""
                            showPhotoLinkDialog = true
                        }) { Text("Usar link de imagem") }
                        TextButton(onClick = {
                            showPhotoEditor = false
                            pickPhoto.launch("image/*")
                        }) { Text("Escolher imagem do celular") }
                        TextButton(onClick = {
                            showPhotoEditor = false
                            vm.restoreSpotifyPhoto()
                        }) { Text("Restaurar fotografia padrão") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPhotoEditor = false }) { Text("Fechar") }
                },
            )
        }

        if (showPhotoLinkDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoLinkDialog = false },
                title = { Text("Usar imagem por link") },
                text = {
                    OutlinedTextField(
                        value = photoLink,
                        onValueChange = { photoLink = it },
                        label = { Text("URL HTTPS da fotografia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.setPhotoLink(photoLink)
                        showPhotoLinkDialog = false
                    }) { Text("Salvar") }
                },
                dismissButton = {
                    TextButton(onClick = { showPhotoLinkDialog = false }) { Text("Cancelar") }
                },
            )
        }

        if (playlistSheetItem != null) {
            com.stash.core.ui.components.SaveToPlaylistSheet(
                playlists = userPlaylists.map {
                    com.stash.core.ui.components.PlaylistInfo(it.id, it.name, it.trackCount)
                },
                onSaveToPlaylist = vm::onSaveToPlaylist,
                onCreatePlaylist = vm::onCreatePlaylistAndAdd,
                onDismiss = vm::onDismissPlaylistSheet,
            )
        }
    }
}

/**
 * Shared helper for the Fresh / Stale / (populated) Loading branches so
 * the `when` in [ArtistProfileScreen] doesn't duplicate four `item { }`
 * blocks. Uses [LazyListScope]-style extension form so call sites read
 * naturally inside `LazyColumn { ... }`.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.contentSections(
    state: ArtistProfileUiState,
    previewState: com.stash.core.media.preview.PreviewState,
    downloadingIds: Set<String>,
    downloadedIds: Set<String>,
    previewLoadingId: String?,
    currentPlayingYoutubeId: String?,
    losslessPrefetcher: LosslessUrlPrefetcher,
    onPreview: (TrackItem) -> Unit,
    onStopPreview: () -> Unit,
    onDownload: (SearchResultItem) -> Unit,
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onStartRadio: (TrackItem) -> Unit,
    onRequestAddToPlaylist: (TrackItem) -> Unit,
    onNavigateToAlbum: (album: AlbumSummary) -> Unit,
    onNavigateToArtist: (artistId: String, name: String, avatarUrl: String?) -> Unit,
    wordmarkSource: String?,
    focus: AlbumFocusTarget?,
) {
    if (state.popular.isNotEmpty()) {
        item { ArtistEditorialHeading("Mais ouvidas", compact = true) }
        item {
            PopularTracksSection(
                tracks = state.popular,
                previewState = previewState,
                downloadingIds = downloadingIds,
                downloadedIds = downloadedIds,
                previewLoadingId = previewLoadingId,
                currentPlayingYoutubeId = currentPlayingYoutubeId,
                losslessPrefetcher = losslessPrefetcher,
                onPreview = onPreview,
                onStopPreview = onStopPreview,
                onDownload = onDownload,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onStartRadio = onStartRadio,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
            )
        }
    }
    if (state.albums.isNotEmpty()) {
        item { ArtistEditorialHeading("Álbuns") }
        item {
            AlbumsRow(
                albums = state.albums,
                onClick = onNavigateToAlbum,
                focusIndex = focus?.takeIf { it.shelf == AlbumShelf.ALBUMS }?.index,
            )
        }
    }
    if (state.singles.isNotEmpty()) {
        item { ArtistEditorialHeading("Singles e EPs") }
        item {
            SinglesRow(
                singles = state.singles,
                onClick = onNavigateToAlbum,
                focusIndex = focus?.takeIf { it.shelf == AlbumShelf.SINGLES }?.index,
            )
        }
    }
    if (state.related.isNotEmpty()) {
        item { ArtistEditorialHeading("Artistas relacionados") }
        item {
            RelatedArtistsRow(
                artists = state.related,
                onClick = { onNavigateToArtist(it.id, it.name, it.avatarUrl) },
            )
        }
    }
    val about = state.about
    if (about != null) {
        item { ArtistEditorialHeading("Sobre") }
        item {
            AboutSection(
                about = about,
                avatarUrl = state.hero.avatarUrl,
                wordmarkSource = wordmarkSource,
            )
        }
    }
}

@Composable
private fun ArtistEditorialHeading(label: String, compact: Boolean = false) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = if (compact) 19.sp else 21.sp,
        lineHeight = if (compact) 24.sp else 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
        modifier = Modifier.fillMaxWidth()
            .padding(
                start = 20.dp, end = 20.dp,
                top = if (compact) 24.dp else 28.dp,
                bottom = if (compact) 10.dp else 12.dp,
            ),
    )
}

/**
 * Small editorial chapter navigator. The page underneath still retains the
 * familiar Spotify-style popular songs, albums, singles, related artists and
 * about sections, so each label scrolls to real catalog content.
 */
@Composable
private fun ArtistSectionTabs(
    tabs: List<Pair<String, Int>>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
) {
    if (tabs.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        tabs.forEach { (title, targetIndex) ->
            val selected = targetIndex == selectedIndex
            val indicator = MaterialTheme.colorScheme.onSurface.copy(alpha = .84f)
            Surface(
                onClick = { onSelect(targetIndex) },
                shape = RoundedCornerShape(0.dp),
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .drawBehind {
                            if (selected) {
                                val y = size.height - 1.5.dp.toPx()
                                drawLine(
                                    color = indicator,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        },
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(top = 12.dp, bottom = 13.dp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

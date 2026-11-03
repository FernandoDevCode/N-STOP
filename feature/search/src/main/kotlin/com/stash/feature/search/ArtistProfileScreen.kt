package com.stash.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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

    // Each destination owns an independent scroll position.
    val musicListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val singlesListState = rememberLazyListState()
    val aboutListState = rememberLazyListState()
    var selectedArtistTab by rememberSaveable(state.hero.name) {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    var releaseFilter by rememberSaveable(state.hero.name) {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    val artistTabs = listOf(
        "Músicas" to 0,
        "Álbuns" to 1,
        "Singles e EPs" to 2,
        "Sobre" to 3,
    )
    val focus = remember(state.focusAlbum, state.albums, state.singles) {
        findAlbumFocus(state.focusAlbum, state.albums, state.singles)
    }
    LaunchedEffect(focus, state.status) {
        if (focus != null && (state.status is ArtistProfileStatus.Fresh ||
            state.status is ArtistProfileStatus.Stale)) {
            when (focus.shelf) {
                AlbumShelf.ALBUMS -> {
                    selectedArtistTab = 1
                    albumsListState.scrollToItem(2 + focus.index / 2)
                }
                AlbumShelf.SINGLES -> {
                    selectedArtistTab = 2
                    releaseFilter = 0
                    singlesListState.scrollToItem(2 + focus.index / 2)
                }
            }
        }
    }
    val listState = when (selectedArtistTab) {
        1 -> albumsListState
        2 -> singlesListState
        3 -> aboutListState
        else -> musicListState
    }
    val statusBarNeedsContrast by remember(listState, selectedArtistTab) {
        derivedStateOf {
            selectedArtistTab != 0 || listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 180
        }
    }
    val statusBarScrim by animateColorAsState(
        targetValue = if (statusBarNeedsContrast) smoothAtmosphere.copy(alpha = .98f)
            else Color.Transparent,
        label = "artistStatusBarContrast",
    )
    val showArtistTabs by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }
    // Photo continuation tracks only the original, image-led music destination.
    val itemHeightPx = remember(state.hero.name) { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(musicListState) {
        snapshotFlow { musicListState.layoutInfo.visibleItemsInfo.map { it.index to it.size } }
            .collect { entries ->
                entries.forEach { (index, height) ->
                    if (height > 0) itemHeightPx[index] = height
                }
            }
    }
    val absoluteScrollPx by remember(musicListState, itemHeightPx) {
        derivedStateOf {
            val index = musicListState.firstVisibleItemIndex
            val preceding = (0 until index).map { itemHeightPx[it] }
            if (preceding.any { it == null }) null else
                preceding.filterNotNull().sum().toFloat() +
                    musicListState.firstVisibleItemScrollOffset
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
                // V2: spatial pools of photo-derived light, not a flat tint.
                drawRect(ArtistNeutralAtmosphere)
                val glow = lerp(smoothAtmosphere, smoothBridge, .86f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = .91f), Color.Transparent),
                        center = Offset(size.width * .12f, size.height * .03f),
                        radius = size.width * 1.50f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = .60f), Color.Transparent),
                        center = Offset(size.width * .94f, size.height * .78f),
                        radius = size.width * 1.17f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to smoothAtmosphere.copy(alpha = .24f),
                    ),
                )
                val frameEnd = if (selectedArtistTab == 0) {
                    absoluteScrollPx?.let { photoFrameHeightPx - it }
                } else null
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
            if (selectedArtistTab == 0) item {
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
                    onOpenAbout = { selectedArtistTab = 3 },
                    featuredContent = featuredAlbum?.let { album ->
                        @Composable {
                            ArtistFeaturedAlbum(album = album, onOpen = onNavigateToAlbum)
                        }
                    },
                )
            }
            // In-flow tabs switch content; they are no longer scroll anchors.
            if (selectedArtistTab != 0) item {
                Text(
                    text = state.hero.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            item(key = "artist-profile-tabs") {
                if (artistTabs.size > 1) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                    ) {
                        ArtistSectionTabs(
                            tabs = artistTabs,
                            selectedIndex = selectedArtistTab,
                            onSelect = { selectedArtistTab = it },
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
                        selectedTab = selectedArtistTab,
                        releaseFilter = releaseFilter,
                        onReleaseFilterChange = { releaseFilter = it },
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
                    selectedTab = selectedArtistTab,
                    releaseFilter = releaseFilter,
                    onReleaseFilterChange = { releaseFilter = it },
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
                    .background(lerp(ArtistNeutralAtmosphere, smoothAtmosphere, .5f)),
            ) {
                ArtistSectionTabs(
                    tabs = artistTabs,
                    selectedIndex = selectedArtistTab,
                    onSelect = { selectedArtistTab = it },
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
    selectedTab: Int,
    releaseFilter: Int,
    onReleaseFilterChange: (Int) -> Unit,
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
    when (selectedTab) {
        0 -> {
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
                item { ArtistEditorialHeading("Discografia · Álbuns") }
                item { AlbumsRow(albums = state.albums.take(8), onClick = onNavigateToAlbum) }
            }
            if (state.singles.isNotEmpty()) {
                item { ArtistEditorialHeading("Lançamentos") }
                item { SinglesRow(singles = state.singles.take(8), onClick = onNavigateToAlbum) }
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
        }
        1 -> {
            item { ArtistEditorialHeading("Álbuns") }
            if (state.albums.isEmpty()) {
                item { ArtistEmptyDestination("Nenhum álbum disponível") }
            } else {
                artistReleaseGridRows(state.albums, onNavigateToAlbum,
                    focus?.takeIf { it.shelf == AlbumShelf.ALBUMS }?.index)
            }
        }
        2 -> {
            item { ArtistEditorialHeading("Singles e EPs") }
            item { ArtistReleaseFilters(releaseFilter, onReleaseFilterChange) }
            val releases = state.singles.filter {
                releaseFilter == 0 ||
                    (releaseFilter == 1 && it.artistReleaseKind() == "single") ||
                    (releaseFilter == 2 && it.artistReleaseKind() == "ep")
            }
            if (releases.isEmpty()) {
                item { ArtistEmptyDestination("Nenhum lançamento nesta categoria") }
            } else {
                artistReleaseGridRows(releases, onNavigateToAlbum,
                    if (releaseFilter == 0) focus?.takeIf {
                        it.shelf == AlbumShelf.SINGLES
                    }?.index else null)
            }
        }
        3 -> {
            item { ArtistEditorialHeading("Sobre") }
            val about = state.about
            if (about != null && (about.bio?.isNotBlank() == true ||
                    about.socials.isNotEmpty())) {
                item {
                    AboutSection(
                        about = about,
                        avatarUrl = state.hero.avatarUrl,
                        wordmarkSource = wordmarkSource,
                    )
                }
            } else {
                item { ArtistEmptyDestination("Informações do artista indisponíveis") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.artistReleaseGridRows(
    releases: List<AlbumSummary>,
    onNavigateToAlbum: (AlbumSummary) -> Unit,
    focusedIndex: Int?,
) {
    releases.chunked(2).forEachIndexed { rowIndex, row ->
        item(key = "artist-grid-${rowIndex}-${row.first().id}") {
            val cardSize = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 52.dp) / 2
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { colIndex, album ->
                    com.stash.core.ui.components.AlbumSquareCard(
                        title = album.title,
                        artist = album.artist,
                        thumbnailUrl = album.thumbnailUrl,
                        year = album.year,
                        cardSize = cardSize,
                        modifier = if (focusedIndex == rowIndex * 2 + colIndex)
                            Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface,
                                RoundedCornerShape(12.dp)) else Modifier,
                        onClick = { onNavigateToAlbum(album) },
                    )
                }
            }
        }
    }
}

private fun AlbumSummary.artistReleaseKind(): String? {
    val kind = releaseType?.trim()?.lowercase()
    return when {
        kind?.contains("single") == true -> "single"
        kind == "ep" || kind?.contains("extended play") == true -> "ep"
        kind == null && trackCount == 1 -> "single"
        kind == null && trackCount != null && trackCount in 2..6 -> "ep"
        else -> null
    }
}

@Composable
private fun ArtistReleaseFilters(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("Todos", "Singles", "EPs").forEachIndexed { index, label ->
            Surface(
                onClick = { onSelect(index) },
                color = if (selected == index)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = .17f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .19f),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistEmptyDestination(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 30.dp),
    )
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
                top = if (compact) 24.dp else 32.dp,
                bottom = if (compact) 10.dp else 14.dp,
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

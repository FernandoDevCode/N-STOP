package com.stash.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.ui.components.AlbumsRowSkeleton
import com.stash.core.ui.components.DiscoveryErrorCard
import com.stash.core.ui.components.PopularListSkeleton
import com.stash.data.ytmusic.model.AlbumSummary
import kotlinx.coroutines.flow.merge
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
    // One asynchronously decoded and preblurred photo per artist, reused across all tabs.
    // The request key follows edited hero photos; no processing occurs during scroll.
    val photoBackdrop by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = state.hero.avatarUrl,
    ) {
        value = artistPhotoBackdrop(context, state.hero.avatarUrl)
    }
    val smoothAtmosphere by animateColorAsState(
        targetValue = photoPalette.surface,
        label = "artistReadingSurface",
    )
    val smoothMid by animateColorAsState(
        targetValue = photoPalette.mid,
        label = "artistCanvasMid",
    )
    val smoothDeep by animateColorAsState(
        targetValue = photoPalette.deep,
        label = "artistCanvasDeep",
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
    // The non-Music destinations reserve the same top area as their pinned
    // identity and tabs. Focused rows must scroll BELOW that header too.
    val systemTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pinnedHeaderHeight = 52.dp + 48.dp
    val secondaryContentTop = systemTopPadding + 8.dp + pinnedHeaderHeight + 16.dp
    val secondaryContentTopPx = with(LocalDensity.current) { secondaryContentTop.roundToPx() }

    val focus = remember(state.focusAlbum, state.albums, state.singles) {
        findAlbumFocus(state.focusAlbum, state.albums, state.singles)
    }
    LaunchedEffect(focus, state.status, secondaryContentTopPx) {
        if (focus != null && (state.status is ArtistProfileStatus.Fresh ||
            state.status is ArtistProfileStatus.Stale)) {
            when (focus.shelf) {
                AlbumShelf.ALBUMS -> {
                    selectedArtistTab = 1
                    albumsListState.scrollToItem(1 + focus.index / 2, -secondaryContentTopPx)
                }
                AlbumShelf.SINGLES -> {
                    selectedArtistTab = 2
                    releaseFilter = 0
                    singlesListState.scrollToItem(2 + focus.index / 2, -secondaryContentTopPx)
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
    // One consistent pinned header for every secondary destination; on Music
    // it appears only once the in-flow selector has scrolled offscreen.
    val showArtistTabs by remember(listState, selectedArtistTab) {
        derivedStateOf {
            selectedArtistTab != 0 || listState.firstVisibleItemIndex > 1
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
        ArtistProfileBackdrop(
            modifier = Modifier.fillMaxSize().padding(inner),
            mid = smoothMid,
            deep = smoothDeep,
            bridge = smoothBridge,
            photoBackdrop = photoBackdrop,
        ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = if (selectedArtistTab == 0) 0.dp else secondaryContentTop,
                bottom = bottomChromePadding(96.dp),
            ),
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
                    onPhotoFrameMeasured = {},
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
            // The music hero owns its in-flow tabs. Secondary destinations use
            // the single pinned header below; no duplicate title or tab rows in
            // their lazy content, preventing content from sliding under the tabs.
            if (selectedArtistTab == 0) {
                item(key = "artist-profile-tabs") {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
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
                ArtistProfileStatus.Loading -> if (selectedArtistTab == 0 &&
                    state.popular.isEmpty()) {
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
                .height(systemTopPadding + 12.dp)
                .background(
                    if (showArtistTabs) {
                        // When navigation is pinned, tracks must not bleed behind
                        // the Android clock or through the gap above the tabs.
                        Brush.verticalGradient(
                            0f to smoothDeep,
                            1f to smoothDeep,
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to statusBarScrim,
                            1f to statusBarScrim.copy(alpha = 0f),
                        )
                    },
                ),
        )
        // One pinned artist identity/navigation shell for all four destinations.
        // All secondary content starts BELOW it (see secondaryContentTop).
        if (showArtistTabs) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(top = systemTopPadding + 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .background(smoothDeep)
                        .padding(start = 8.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("‹ Voltar") }
                    Text(
                        text = state.hero.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().background(smoothDeep)) {
                    ArtistSectionTabs(
                        tabs = artistTabs,
                        selectedIndex = selectedArtistTab,
                        onSelect = { selectedArtistTab = it },
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to smoothDeep,
                                1f to Color.Transparent,
                            ),
                        ),
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

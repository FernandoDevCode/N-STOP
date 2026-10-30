package com.stash.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
    val context = LocalContext.current
    val photoPalette by produceState(
        initialValue = ArtistNeutralPalette,
        key1 = state.hero.avatarUrl,
    ) {
        value = artistPaletteFromPhoto(context, state.hero.avatarUrl)
    }
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomChromePadding())) },
    ) { inner ->
        // A single photo-to-content fade follows the actual scroll position.
        // Unlike a viewport-fixed wash, this does not recolour later sections
        // after the hero scrolls away. It never touches the app's mini-player.
        Box(
            modifier = Modifier.fillMaxSize().padding(inner).drawBehind {
                drawRect(smoothAtmosphere)
                val visible = listState.layoutInfo.visibleItemsInfo
                val heroEnd = visible.firstOrNull { it.index == 0 }
                    ?.let { (it.offset + it.size).toFloat() }
                val firstHeading = visible.firstOrNull { it.index == 2 }
                    ?.offset?.toFloat()
                // The first shelf follows the heading. Its approximate heading
                // lead-in is only used once that heading itself is offscreen.
                val firstShelf = visible.firstOrNull { it.index == 3 }
                    ?.let { it.offset - 56.dp.toPx() }
                val fadeStart = heroEnd ?: firstHeading ?: firstShelf
                if (fadeStart != null) {
                    val fadeEnd = fadeStart + 340.dp.toPx()
                    if (fadeEnd > 0f && fadeStart < size.height) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to smoothBridge,
                                .48f to lerp(smoothBridge, smoothAtmosphere, .72f),
                                1f to smoothAtmosphere,
                                startY = fadeStart,
                                endY = fadeEnd,
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
                    atmosphere = smoothAtmosphere,
                    bridgeAtmosphere = smoothBridge,
                    heroAtmosphere = smoothHeroTone,
                    onBack = onBack,
                    onPlayArtist = vm::playArtist,
                    onStartRadio = vm::startRadio,
                    streamingEnabled = streamingEnabled,
                    onStreamingClick = { showStreamingSheet = true },
                    onOpenAbout = positions.about?.let { target ->
                        {
                            scrollScope.launch {
                                listState.animateScrollToItem(target, scrollOffset = -120)
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
            // The release now belongs to the hero composition. Keep its old
            // index reserved, including when absent, for album/About navigation.
            item(key = "artist-featured-release") { }

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
                        wordmarkFromAudioDb = wordmarkUrl != null,
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
                    wordmarkFromAudioDb = wordmarkUrl != null,
                    focus = focus,
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
    wordmarkFromAudioDb: Boolean,
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
                wordmarkFromAudioDb = wordmarkFromAudioDb,
            )
        }
    }
}

@Composable
private fun ArtistEditorialHeading(label: String, compact: Boolean = false) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = if (compact) 17.sp else 20.sp,
        lineHeight = if (compact) 21.sp else 25.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp,
                top = if (compact) 20.dp else 15.dp,
                bottom = if (compact) 10.dp else 7.dp),
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
            .background(MaterialTheme.colorScheme.background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        tabs.forEach { (title, targetIndex) ->
            val selected = targetIndex == selectedIndex
            val indicator = MaterialTheme.colorScheme.primary
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
                                    strokeWidth = 3.dp.toPx(),
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

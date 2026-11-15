package com.stash.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stash.core.ui.components.AlbumSquareCard
import com.stash.core.ui.components.bottomChromePadding
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Spotify details reuse the exact artist/album UI primitives from the original
 * Sonora screens, but consume Spotify identities directly. Never navigate a
 * Spotify artist/album ID into a YouTube Music browseId cache.
 */
@Composable
internal fun SpotifyCatalogBrowseContent(
    viewModel: SonoraSearchViewModel,
    page: SpotifyBrowsePage,
    snackbar: SnackbarHostState,
) {
    BackHandler { viewModel.closeSpotifyBrowse() }
    when (page.entry.type) {
        SearchResultType.ARTIST -> SpotifyArtistCatalogContent(viewModel, page, snackbar)
        SearchResultType.ALBUM -> SpotifyAlbumCatalogContent(viewModel, page, snackbar)
        else -> Text("Conteúdo indisponível.")
    }
}

private fun UniversalSearchResult.isSingleOrEp(): Boolean =
    releaseKind?.lowercase()?.let { it.contains("single") || it.contains("ep") } == true

private fun UniversalSearchResult.spotifyUrl(): String? {
    if (identity.source != CatalogSource.SPOTIFY ||
        !identity.catalogId.matches(Regex("[A-Za-z0-9]{22}"))) return null
    val kind = when (type) {
        SearchResultType.ARTIST -> "artist"
        SearchResultType.ALBUM -> "album"
        SearchResultType.TRACK -> "track"
        else -> return null
    }
    return "https://open.spotify.com/" + kind + "/" + identity.catalogId
}

@Composable
private fun SpotifyArtistCatalogContent(
    vm: SonoraSearchViewModel,
    page: SpotifyBrowsePage,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val avatar = page.entry.artworkUrl
    val palette by produceState(initialValue = ArtistNeutralPalette, key1 = avatar) {
        value = artistPaletteFromPhoto(context, avatar)
    }
    val backdrop by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, key1 = avatar,
    ) {
        value = artistPhotoBackdrop(context, avatar)
    }
    val wordmark by produceState<String?>(initialValue = null, key1 = page.entry.title) {
        value = ArtistWordmarkLookup.resolve(page.entry.title)
    }
    val heroColor by animateColorAsState(palette.hero, label = "spotifyArtistHeroColor")
    val bridgeColor by animateColorAsState(palette.bridge, label = "spotifyArtistBridgeColor")
    val midColor by animateColorAsState(palette.mid, label = "spotifyArtistMidColor")
    val deepColor by animateColorAsState(palette.deep, label = "spotifyArtistDeepColor")

    val all = page.items.filter { it.type == SearchResultType.ALBUM }
    val albums = all.filterNot { it.isSingleOrEp() }
    val singles = all.filter { it.isSingleOrEp() }
    val featured = albums.firstOrNull { it.artworkUrl != null }
        ?: singles.firstOrNull { it.artworkUrl != null }
        ?: all.firstOrNull()
    val featuredTint by produceState<Color?>(
        initialValue = null, key1 = featured?.artworkUrl,
    ) {
        value = featuredArtworkTintFromCover(context, featured?.artworkUrl)
    }

    // Keep Sonora's distinct destinations and their editorial album grids.
    var tab by rememberSaveable(page.entry.stableKey) { mutableIntStateOf(0) }
    val tabs = listOf(
        "Destaques" to 0,
        "Álbuns" to 1,
        "Singles e EPs" to 2,
        "Sobre" to 3,
    )
    val scroll = rememberLazyListState()
    LaunchedEffect(tab, page.entry.stableKey) { scroll.scrollToItem(0) }
    SpotifyBrowsePaging(scroll, page, vm)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomChromePadding())) },
    ) { inner ->
        ArtistProfileBackdrop(
            modifier = Modifier.fillMaxSize().padding(inner),
            mid = midColor,
            deep = deepColor,
            bridge = bridgeColor,
            photoBackdrop = backdrop,
        ) {
            LazyColumn(
                state = scroll,
                contentPadding = PaddingValues(bottom = bottomChromePadding(96.dp)),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tab == 0) item(key = "spotify-artist-hero") {
                    ArtistHero(
                        hero = HeroState(
                            name = page.entry.title,
                            avatarUrl = avatar,
                            subscribersText = null,
                            spotifyArtistUrl = page.entry.spotifyUrl(),
                        ),
                        status = if (page.loading && page.items.isEmpty())
                            ArtistProfileStatus.Loading else ArtistProfileStatus.Fresh,
                        socials = emptyList(),
                        wordmarkUrl = wordmark,
                        heroAtmosphere = heroColor,
                        bridgeAtmosphere = bridgeColor,
                        onBack = vm::closeSpotifyBrowse,
                        onPlayArtist = vm::playSpotifyArtist,
                        onStartRadio = {},
                        radioEnabled = false,
                        photoEditingEnabled = false,
                        streamingSettingsEnabled = false,
                        streamingEnabled = true,
                        onStreamingClick = {},
                        onOpenAbout = { tab = 3 },
                        featuredContent = featured?.let { album ->
                            @Composable {
                                ArtistFeaturedReleaseCard(
                                    title = album.title,
                                    thumbnailUrl = album.artworkUrl,
                                    year = album.releaseDate?.take(4),
                                    trackCount = null,
                                    artworkTint = featuredTint,
                                    onOpen = { vm.openSpotifyBrowse(album) },
                                )
                            }
                        },
                    )
                } else {
                    item(key = "spotify-artist-identity") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(
                                top = androidx.compose.foundation.layout.WindowInsets.statusBars
                                    .asPaddingValues().calculateTopPadding() + 8.dp,
                                start = 16.dp, end = 16.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = vm::closeSpotifyBrowse) { Text("‹ Voltar") }
                            Text(
                                page.entry.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
                item(key = "spotify-artist-tabs") {
                    ArtistSectionTabs(tabs = tabs, selectedIndex = tab, onSelect = { tab = it })
                }

                when (tab) {
                    0 -> {
                        if (albums.isNotEmpty()) spotifyAlbumGrid(
                            "Discografia · Álbuns", albums.take(8), vm::openSpotifyBrowse)
                        if (singles.isNotEmpty()) spotifyAlbumGrid(
                            "Lançamentos", singles.take(8), vm::openSpotifyBrowse)
                    }
                    1 -> spotifyAlbumGrid("Álbuns", albums, vm::openSpotifyBrowse)
                    2 -> spotifyAlbumGrid("Singles e EPs", singles, vm::openSpotifyBrowse)
                    3 -> item(key = "spotify-artist-about") {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {
                            Text(
                                "Perfil do Spotify",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Metadados e lançamentos vinculados à identidade oficial do artista.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            page.entry.spotifyUrl()?.let { url ->
                                TextButton(onClick = { uriHandler.openUri(url) }) {
                                    Text("Abrir artista no Spotify")
                                }
                            }
                        }
                    }
                }
                spotifyBrowseFooter(page, vm, "Carregar mais lançamentos")
            }
        }
    }
}

private fun LazyListScope.spotifyAlbumGrid(
    title: String,
    releases: List<UniversalSearchResult>,
    onOpen: (UniversalSearchResult) -> Unit,
) {
    item(key = "heading-" + title) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, top = 23.dp, bottom = 10.dp),
        )
    }
    if (releases.isEmpty()) {
        item { Text("Nenhum lançamento nesta categoria.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) }
    }
    releases.chunked(2).forEachIndexed { rowIndex, row ->
        item(key = "spotify-grid-" + title + "-" + rowIndex) {
            val size = (LocalConfiguration.current.screenWidthDp.dp - 52.dp) / 2
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { release ->
                    AlbumSquareCard(
                        title = release.title,
                        artist = release.subtitle?.substringBefore(" · ").orEmpty(),
                        thumbnailUrl = release.artworkUrl,
                        year = release.releaseDate?.take(4),
                        cardSize = size,
                        onClick = { onOpen(release) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotifyAlbumCatalogContent(
    vm: SonoraSearchViewModel,
    page: SpotifyBrowsePage,
    snackbar: SnackbarHostState,
) {
    val uriHandler = LocalUriHandler.current
    val loadingKey by vm.resolving.collectAsStateWithLifecycle()
    val tracks = page.items.filter { it.type == SearchResultType.TRACK }
    val scroll = rememberLazyListState()
    LaunchedEffect(page.entry.stableKey) { scroll.scrollToItem(0) }
    SpotifyBrowsePaging(scroll, page, vm)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomChromePadding())) },
    ) { inner ->
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(bottom = bottomChromePadding(120.dp)),
        ) {
            item(key = "spotify-album-hero") {
                AlbumHero(
                    hero = AlbumHeroState(
                        title = page.entry.title,
                        artist = page.entry.subtitle?.substringBefore(" · ").orEmpty(),
                        thumbnailUrl = page.entry.artworkUrl,
                        year = page.entry.releaseDate?.take(4),
                        trackCount = tracks.size,
                        totalDurationMs = tracks.sumOf { it.durationMs ?: 0L },
                    ),
                    hasDownloaded = false,
                    onBack = vm::closeSpotifyBrowse,
                    onShuffle = {},
                    onDownloadAll = {},
                    onPlayAlbum = {
                        tracks.firstOrNull()?.let {
                            vm.onAction(it, SearchResultAction.PLAY)
                        }
                    },
                    onAddToQueue = {},
                    streamingEnabled = true,
                    onStreamingClick = {},
                    downloadSupported = false,
                    collectionActionsEnabled = false,
                    actionsEnabled = tracks.isNotEmpty(),
                )
            }
            itemsIndexed(tracks, key = { _, track -> track.stableKey }) { index, track ->
                SongRow(
                    item = SearchResultItem(
                        // No Spotify catalog ID is ever supplied as a YouTube video ID.
                        videoId = "",
                        title = track.title,
                        artist = track.subtitle.orEmpty(),
                        durationSeconds = (track.durationMs ?: 0L) / 1000.0,
                        thumbnailUrl = page.entry.artworkUrl,
                    ),
                    isDownloading = false,
                    isDownloaded = false,
                    isPreviewLoading = false,
                    isPreviewPlaying = false,
                    onPlay = { vm.onAction(track, SearchResultAction.PLAY) },
                    onStopPreview = {},
                    onDownload = {},
                    downloadSupported = false,
                    isResolving = loadingKey == track.stableKey,
                    editorial = true,
                    editorialIndex = index + 1,
                    editorialAlbum = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onOpenCatalog = {
                        track.spotifyUrl()?.let(uriHandler::openUri)
                    },
                )
            }
            spotifyBrowseFooter(page, vm, "Carregar mais faixas")
        }
    }
}

/** Shared pagination + error state, retaining Spotify identity and current page. */
private fun LazyListScope.spotifyBrowseFooter(
    page: SpotifyBrowsePage,
    vm: SonoraSearchViewModel,
    moreLabel: String,
) {
    if (page.loading) item(key = "spotify-detail-loading") {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    if (page.error != null) item(key = "spotify-detail-error") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                page.error,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::retrySpotifyBrowse) { Text("Tentar novamente") }
                val uriHandler = LocalUriHandler.current
                page.entry.spotifyUrl()?.let { url ->
                    TextButton(onClick = { uriHandler.openUri(url) }) {
                        Text("Abrir no Spotify")
                    }
                }
            }
        }
    }
    if (!page.loading && page.error == null && page.items.isEmpty()) {
        item(key = "spotify-detail-empty") {
            Text(
                "Nenhum lançamento disponível neste catálogo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
    if (!page.loading && page.continuation != null) item(key = "spotify-detail-more") {
        TextButton(
            onClick = vm::loadSpotifyBrowse,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) { Text(moreLabel) }
    }
}

@Composable
private fun SpotifyBrowsePaging(
    scroll: androidx.compose.foundation.lazy.LazyListState,
    page: SpotifyBrowsePage,
    vm: SonoraSearchViewModel,
) {
    LaunchedEffect(scroll, page.entry.stableKey, page.items.size, page.continuation, page.loading) {
        snapshotFlow {
            val layout = scroll.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layout.totalItemsCount
            last >= total - 3 && total > 0
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && page.continuation != null && !page.loading && page.error == null)
                vm.loadSpotifyBrowse()
        }
    }
}

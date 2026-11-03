package com.stash.feature.search

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stash.core.media.preview.LosslessUrlPrefetcher
import com.stash.core.model.TrackItem
import com.stash.data.ytmusic.model.AlbumSummary

/**
 * Shared helper for the Fresh / Stale / (populated) Loading branches so
 * the `when` in [ArtistProfileScreen] doesn't duplicate four `item { }`
 * blocks. Uses [LazyListScope]-style extension form so call sites read
 * naturally inside `LazyColumn { ... }`.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.contentSections(
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
internal fun ArtistSectionTabs(
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

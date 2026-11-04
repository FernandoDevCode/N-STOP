package com.stash.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.VisualCard
import com.stash.core.ui.visual.VisualDesign
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.TrackSummary

/** Three storefronts share precisely the same recommendation data as the rails. */
@Composable
internal fun SonoraWeeklyHero(
    feed: SonoraWeeklyDiscovery,
    onAlbum: (AlbumSummary) -> Unit,
    onSong: (TrackSummary) -> Unit,
) {
    val pages = buildList<Triple<String, String, Int>> {
        if (feed.releases.isNotEmpty()) add(Triple("NOVIDADES", "LANÇAMENTO PARA VOCÊ", 0))
        if (feed.current.isNotEmpty()) add(Triple("SUA FASE", "NA SUA FREQUÊNCIA", 1))
        if (feed.forgotten.isNotEmpty()) add(Triple("FORA DO RADAR", "UM OUTRO LADO SEU", 2))
    }
    if (pages.isEmpty()) return
    val pager = rememberPagerState(pageCount = { pages.size })
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
            pageSpacing = 12.dp,
        ) { page ->
            when (pages[page].third) {
                0 -> {
                    val release = feed.releases.first()
                    SonoraFeaturedRelease(
                        label = pages[page].second,
                        title = release.title,
                        artist = release.artist,
                        artwork = release.thumbnailUrl,
                        onOpen = { onAlbum(release) },
                    )
                }
                1 -> {
                    val song = feed.current.first()
                    SonoraFeaturedRelease(
                        label = pages[page].second,
                        title = song.title,
                        artist = song.artist,
                        artwork = song.thumbnailUrl,
                        actionLabel = "Ouvir agora",
                        onOpen = { onSong(song) },
                    )
                }
                else -> {
                    val song = feed.forgotten.first()
                    SonoraFeaturedRelease(
                        label = pages[page].second,
                        title = song.title,
                        artist = song.artist,
                        artwork = song.thumbnailUrl,
                        actionLabel = "Ouvir agora",
                        onOpen = { onSong(song) },
                    )
                }
            }
        }
        if (pages.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pages.size) { position ->
                    Spacer(Modifier.padding(horizontal = 4.dp).size(6.dp)
                        .background(
                            if (pager.currentPage == position) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = .30f),
                            CircleShape,
                        ))
                }
            }
        }
    }
}

@Composable
internal fun SonoraWeeklyAlbumRail(
    albums: List<AlbumSummary>,
    onAlbum: (AlbumSummary) -> Unit,
) {
    if (albums.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        SonoraSectionTitle("Novidades para você")
        LazyRow(
            contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                SonoraContentCard(
                    card = VisualCard("album", album.title, album.artist, album.thumbnailUrl),
                    onClick = { onAlbum(album) },
                )
            }
        }
    }
}

@Composable
internal fun SonoraWeeklySongRail(
    title: String,
    tracks: List<TrackSummary>,
    onSong: (TrackSummary) -> Unit,
    onReject: (TrackSummary) -> Unit,
) {
    var selected by remember { mutableStateOf<TrackSummary?>(null) }
    if (tracks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        SonoraSectionTitle(title)
        Text("Segure uma música para ajustar suas sugestões",
            Modifier.padding(horizontal = VisualDesign.Gutter, vertical = 6.dp),
            style = VisualDesign.Caption, color = VisualDesign.Muted)
        LazyRow(
            contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { it.videoId }) { track ->
                SonoraContentCard(
                    card = VisualCard("song", track.title, track.artist, track.thumbnailUrl),
                    onClick = { onSong(track) },
                    onLongPress = { selected = track },
                )
            }
        }
    }
    selected?.let { song ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleLarge)
                Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    onReject(song)
                    selected = null
                }) {
                    Text("Não foi pra mim")
                }
            }
        }
    }
}

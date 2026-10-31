package com.stash.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stash.core.model.*
import com.stash.core.ui.components.*
import com.stash.core.ui.theme.*
import com.stash.feature.settings.components.*
import com.stash.data.ytmusic.model.*
import kotlinx.coroutines.CancellationException
import com.stash.core.ui.visual.*

@Composable
internal fun SearchPage(controller: AppController, localTracks: List<Track>, player: PlayerState,
    onPlay: (List<Track>, Int) -> Unit, onMore: (Track) -> Unit,
    onAlbum: (AlbumSummary) -> Unit, onArtist: (ArtistSummary) -> Unit,
    onSettings: () -> Unit = {}, initialQuery: String = "") {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val results by controller.search.collectAsState()
    val loading by controller.searchBusy.collectAsState()
    val error by controller.searchError.collectAsState()
    val local = remember(query, localTracks) {
        if (query.isBlank()) emptyList() else localTracks.filter { "${it.title} ${it.artist} ${it.album}".contains(query, true) }
    }
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) { submitted = true; controller.search(initialQuery) } }
    VisualScreen(VisualScreenState(VisualPage.Search, query = query,
        categories = if (query.isBlank()) liveSearchCategories else emptyList()),
        VisualActions(onSettings = onSettings, onQuery = { query = it; submitted = false },
            onSearch = { if (query.isNotBlank() && !loading) { submitted = true; controller.search(query) } },
            onCard = { id -> query = id.removePrefix("category:"); submitted = true; controller.search(query) }),
        extraContent = {
        if (local.isNotEmpty()) {
            item { SectionHeader("Na sua biblioteca") }
            itemsIndexed(local, key = { _, track -> "local:${track.id}" }) { index, track ->
                TrackListItem(track, { onPlay(local, index) }, isPlaying = player.currentTrack?.id == track.id, onMoreClick = { onMore(track) })
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if (error != null) item { Column(Modifier.padding(20.dp)) {
            Text(error!!); TextButton(onClick = { controller.search(query) }) { Text("Tentar novamente") }
        } }
        if (submitted && !loading && results?.sections?.isEmpty() == true && error == null) item { Text("Nenhum resultado encontrado.", Modifier.padding(20.dp)) }
        if (submitted) results?.sections?.forEach { section ->
            when (section) {
                is SearchResultSection.Top -> when (val item = section.item) {
                    is TopResultItem.ArtistTop -> item {
                        ListItem(headlineContent = { Text(item.artist.name) }, supportingContent = { Text("Artista · YouTube Music") },
                            leadingContent = { Artwork(item.artist.avatarUrl, Modifier.size(64.dp)) },
                            modifier = Modifier.clickable { onArtist(item.artist) })
                    }
                    is TopResultItem.TrackTop -> item {
                        val track = item.track.model()
                        TrackListItem(track, { onPlay(listOf(track), 0) }, onMoreClick = { onMore(track) })
                    }
                }
                is SearchResultSection.Songs -> {
                    item { SectionHeader("Músicas") }
                    val tracks = section.tracks.map { it.model() }
                    itemsIndexed(tracks, key = { index, track -> "search:$index:${track.id}" }) { index, track ->
                        TrackListItem(track, { onPlay(tracks, index) }, onMoreClick = { onMore(track) })
                    }
                }
                is SearchResultSection.Albums -> {
                    item { SectionHeader("Álbuns") }
                    item { AlbumRail(section.albums, onAlbum) }
                }
                is SearchResultSection.Artists -> {
                    item { SectionHeader("Artistas") }
                    items(section.artists, key = { "artist:${it.id}" }) { artist ->
                        ListItem(headlineContent = { Text(artist.name) }, leadingContent = { Artwork(artist.avatarUrl, Modifier.size(56.dp)) },
                            modifier = Modifier.clickable { onArtist(artist) })
                    }
                }
            }
        }
    })
}

@Composable
internal fun CatalogDetail(controller: AppController, page: Page, player: PlayerState, onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit, onMore: (Track) -> Unit, onAlbum: (AlbumSummary) -> Unit) {
    var tracks by remember(page) { mutableStateOf<List<Track>>(emptyList()) }
    var albums by remember(page) { mutableStateOf<List<AlbumSummary>>(emptyList()) }
    var artwork by remember(page) { mutableStateOf<String?>(null) }
    var error by remember(page) { mutableStateOf<String?>(null) }
    var loading by remember(page) { mutableStateOf(true) }
    var reload by remember(page) { mutableIntStateOf(0) }
    LaunchedEffect(page, reload) {
        loading = true; error = null
        try {
            if (page.name == "album") {
                val album = controller.album(page.remoteId)
                tracks = album.tracks.map { it.model() }; artwork = album.thumbnailUrl; albums = album.moreByArtist
            } else {
                val artist = controller.artist(page.remoteId)
                tracks = artist.popular.map { it.model() }; artwork = artist.avatarUrl; albums = artist.albums + artist.singles
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
        finally { loading = false }
    }
    LazyColumn {
        item { PageHeading(page.title, onBack) }
        if (artwork != null) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Artwork(artwork, Modifier.size(240.dp)) } }
        if (loading) item { CircularProgressIndicator(Modifier.padding(20.dp)) }
        if (error != null) item { Column(Modifier.padding(20.dp)) {
            Text(error!!); TextButton(onClick = { reload++ }) { Text("Tentar novamente") }
        } }
        if (tracks.isNotEmpty()) item { Button(onClick = { onPlay(tracks, 0) }, modifier = Modifier.padding(20.dp)) { Text("Reproduzir") } }
        itemsIndexed(tracks, key = { index, track -> "$index:${track.id}" }) { index, track ->
            TrackListItem(track, { onPlay(tracks, index) }, isPlaying = player.currentTrack?.youtubeId == track.youtubeId,
                onMoreClick = { onMore(track) })
        }
        if (albums.isNotEmpty()) { item { SectionHeader("Álbuns") }; item { AlbumRail(albums, onAlbum) } }
    }
}

@Composable
private fun AlbumRail(albums: List<AlbumSummary>, onAlbum: (AlbumSummary) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(albums, key = { it.id }) { album -> AlbumSquareCard(album.title, album.artist, album.thumbnailUrl, album.year) { onAlbum(album) } }
    }
}

@Composable
internal fun SettingsPage(library: LibrarySnapshot, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = listOf(
        Triple("Reprodução", "Volume e reprodução", "playback"),
        Triple("Qualidade de áudio", "Saída de áudio no Windows", "quality"),
        Triple("Contas", "Conexões dos serviços", "accounts"),
        Triple("Sincronização", "Músicas e playlists dos serviços", "sync"),
        Triple("Biblioteca e armazenamento", "${library.tracks.size} faixas · ${library.playlists.size} playlists", "storage"),
        Triple("Aparência", "Tema, AMOLED e tamanho de fonte", "appearance"),
        Triple("Sobre", "NØNSTOP Desktop", "about"),
    ).filter { "${it.first} ${it.second}".contains(query, true) }
    ScrollPage("Configurações", onBack) {
        SettingsSearchField(query, { query = it })
        SettingsGroupCard(rows = rows.map { row -> { SettingsNavRow(row.first, { onOpen(row.third) }, subtitle = row.second) } })
    }
}


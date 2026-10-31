package com.stash.desktop

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.stash.core.model.*
import com.stash.core.ui.visual.*
import com.stash.core.ui.theme.StashTheme
import com.stash.data.ytmusic.model.AlbumSummary
import kotlinx.coroutines.CancellationException

@Composable internal fun DesktopPresentationTheme(player: Boolean, dark: Boolean, amoled: Boolean, content: @Composable () -> Unit) {
    StashTheme(darkTheme = dark, amoled = amoled) {
        if (player) content() else VisualTheme(dark, amoled, content)
    }
}

internal fun Track.visualCard() = VisualCard("track:$id", title, artist, albumArtPath ?: albumArtUrl, liked = stashLikedAt != null)

/** Pure presentation adapters: no fixtures or screenshot assets enter the live app. */
@Composable internal fun LiveHome(library: LibrarySnapshot, tracks: List<Track>,
    onSettings: () -> Unit, onSearch: () -> Unit, onPlay: (List<Track>, Int) -> Unit,
    onPlaylist: (SavedPlaylist) -> Unit, onMore: (Track) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Todas") }
    val playlists = library.playlists.map { VisualCard("playlist:${it.id}", it.name, "${it.trackIds.size} músicas", it.art) }
    val sections = buildList {
        if (playlists.isNotEmpty()) add(VisualSection("playlists", "Feito para você", playlists))
        if (tracks.isNotEmpty()) add(VisualSection("recent", "Adicionadas recentemente", tracks.takeLast(12).reversed().map { it.visualCard() }, compact = true))
    }
    val available = filter in listOf("Todas", "Músicas")
    VisualScreen(VisualScreenState(VisualPage.Home, listOf("Todas", "Músicas", "Podcasts", "Audiolivros"), filter,
        hero = if (available) tracks.lastOrNull()?.let { VisualHero(it.visualCard(), "Da sua biblioteca") } else null,
        sections = if (available) sections else emptyList(),
        status = if (!available || sections.isEmpty()) VisualStatus.Empty else VisualStatus.Content,
        message = if (available) "Comece a ouvir. Busque músicas e artistas ou adicione seus arquivos para montar sua biblioteca." else "$filter ainda não estão disponíveis no Desktop."),
        VisualActions(onSettings = onSettings, onFilter = { filter = it }, onEmpty = onSearch,
            onSection = { onSearch() }, onMore = { id -> tracks.firstOrNull { "track:${it.id}" == id }?.let(onMore) },
            onCard = { id ->
                library.playlists.firstOrNull { "playlist:${it.id}" == id }?.let(onPlaylist)
                tracks.firstOrNull { "track:${it.id}" == id }?.let { onPlay(tracks, tracks.indexOf(it)) }
            }))
}

@Composable internal fun LiveLibrary(library: LibrarySnapshot, tracks: List<Track>, onSettings: () -> Unit,
    onAdd: () -> Unit, onSearch: () -> Unit, onCreate: () -> Unit, onPlaylist: (SavedPlaylist) -> Unit,
    onDetail: (String, List<Long>) -> Unit, onPlay: (List<Track>, Int) -> Unit, onMore: (Track) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Playlists") }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var reverse by rememberSaveable { mutableStateOf(false) }
    val filtered = tracks.filter { "${it.title} ${it.artist} ${it.album}".contains(query, true) }
    val groups = when (filter) {
        "Álbuns" -> filtered.groupBy { it.album.ifBlank { "Sem álbum" } + " · " + it.artist }
        "Artistas" -> filtered.groupBy { it.artist }
        else -> emptyMap()
    }
    val entries = when (filter) {
        "Curtidas" -> filtered.filter { it.stashLikedAt != null }
        "Baixados" -> filtered.filter { it.isDownloaded }
        else -> filtered
    }
    val rows = when (filter) {
        "Playlists" -> buildList {
            val liked = tracks.filter { it.stashLikedAt != null }
            if (liked.isNotEmpty() && "Músicas Curtidas".contains(query, true)) add(VisualCard("liked", "Músicas Curtidas", "Playlist · ${liked.size} músicas", liked = true))
            addAll(library.playlists.filter { it.name.contains(query, true) }.map { VisualCard("playlist:${it.id}", it.name, "Playlist · ${it.trackIds.size} músicas", it.art) })
        }
        "Álbuns", "Artistas" -> groups.entries.map { (name, items) -> VisualCard("group:$name", name,
            "${items.size} músicas", items.first().albumArtUrl, round = filter == "Artistas") }
        else -> entries.map { it.visualCard() }
    }.let { if (reverse) it.reversed() else it }
    VisualScreen(VisualScreenState(VisualPage.Library,
        listOf("Playlists", "Artistas", "Álbuns", "Músicas", "Curtidas", "Baixados"), filter, rows = rows,
        query = query, showSearch = showSearch, status = if (rows.isEmpty()) VisualStatus.Empty else VisualStatus.Content,
        message = if (tracks.isEmpty() && library.playlists.isEmpty()) "Sua biblioteca ainda está vazia neste PC." else "Nenhum item neste filtro."),
        VisualActions(onSettings = onSettings, onFilter = { filter = it }, onQuery = { query = it },
            onSearch = { showSearch = !showSearch }, onCreate = onCreate, onImport = onAdd, onSort = { reverse = !reverse }, onEmpty = onSearch,
            onCard = { id ->
                when {
                    id == "liked" -> onDetail("Músicas Curtidas", tracks.filter { it.stashLikedAt != null }.map { it.id })
                    id.startsWith("playlist:") -> library.playlists.firstOrNull { "playlist:${it.id}" == id }?.let(onPlaylist)
                    id.startsWith("group:") -> groups[id.removePrefix("group:")]?.let { onDetail(id.removePrefix("group:"), it.map { t -> t.id }) }
                    else -> entries.firstOrNull { "track:${it.id}" == id }?.let { onPlay(entries, entries.indexOf(it)) }
                }
            }, onMore = { id ->
                when {
                    id == "liked" -> onDetail("Músicas Curtidas", tracks.filter { it.stashLikedAt != null }.map { it.id })
                    id.startsWith("group:") -> groups[id.removePrefix("group:")]?.let { onDetail(id.removePrefix("group:"), it.map { t -> t.id }) }
                    id.startsWith("playlist:") -> library.playlists.firstOrNull { "playlist:${it.id}" == id }?.let(onPlaylist)
                    else -> entries.firstOrNull { "track:${it.id}" == id }?.let(onMore)
                }
            }))
}

@Composable internal fun LiveExplore(controller: AppController, onSettings: () -> Unit,
    onAlbum: (AlbumSummary) -> Unit, onQuery: (String) -> Unit) {
    var albums by remember { mutableStateOf<List<AlbumSummary>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("Tudo") }
    LaunchedEffect(reload) {
        loading = true; error = null
        try { albums = controller.explore() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Catálogo indisponível." }
        finally { loading = false }
    }
    val categories = listOf("Indie", "Rock", "Eletrônica", "Hip Hop", "Pop", "Jazz", "Música Brasileira", "Clássica", "Lo-fi")
        .map { VisualCard("genre:$it", it, artwork = "icon:$it") }
    VisualScreen(VisualScreenState(VisualPage.Explore, listOf("Tudo", "Novidades", "Gêneros"), filter,
        sections = if (filter == "Gêneros" || albums.isEmpty()) emptyList() else listOf(VisualSection("releases", "Lançamentos para você",
            albums.map { VisualCard(it.id, it.title, it.artist, it.thumbnailUrl) }, plain = true)),
        categories = if (filter == "Novidades") emptyList() else categories,
        status = when { loading -> VisualStatus.Loading; error != null -> VisualStatus.Error; else -> VisualStatus.Content }, message = error.orEmpty()),
        VisualActions(onSettings = onSettings, onFilter = { filter = it }, onRetry = { reload++ },
            onSection = { onQuery("novos álbuns") }, onCard = { id ->
                if (id.startsWith("genre:")) onQuery(id.removePrefix("genre:")) else albums.firstOrNull { it.id == id }?.let(onAlbum)
            }))
}

internal val liveSearchCategories = listOf("Podcasts", "Audiolivros", "Playlists", "Artistas", "Álbuns", "Músicas", "Shows", "Para relaxar", "Para focar", "Para treinar", "Para dirigir", "Festas")
    .map { VisualCard("category:$it", it, artwork = "icon:$it") }

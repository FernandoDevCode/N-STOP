package com.stash.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.core.model.*
import com.stash.core.ui.components.*
import com.stash.core.ui.theme.*
import com.stash.core.ui.visual.*
import com.stash.feature.home.*
import com.stash.feature.settings.components.*
import com.stash.data.ytmusic.model.*
import kotlinx.coroutines.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

internal data class Page(val name: String, val title: String = "", val ids: List<Long> = emptyList(), val remoteId: String = "")
internal val LocalDesktopWindowVisible = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopApp(controller: AppController, initialPage: String = "home") {
    val library by controller.library.collectAsState()
    val player by controller.audio.state.collectAsState()
    val busy by controller.busy.collectAsState()
    val message by controller.message.collectAsState()
    val audioError by controller.audio.error.collectAsState()
    val stack = remember { mutableStateListOf(Page(initialPage)) }
    val page = stack.last()
    var selectedTab by remember { mutableIntStateOf(listOf("home", "explore", "radio", "library", "search").indexOf(initialPage).coerceAtLeast(0)) }
    val preferences by controller.preferences.collectAsState()
    val dark = preferences.dark
    val amoled = preferences.amoled
    val fontScale = preferences.fontScale
    val volume = preferences.volume
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val tracks = remember(library) { library.tracks.map { it.model() } }
    fun open(name: String, title: String = "", ids: List<Long> = emptyList(), remoteId: String = "") {
        stack.add(Page(name, title, ids, remoteId))
    }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun play(list: List<Track>, index: Int = 0) { if (list.isNotEmpty()) controller.audio.setQueue(list, index) }
    fun pickBackup() = pickFiles("Importar backup do NØNSTOP (.zip ou .db)", false) { files -> files.firstOrNull()?.let(controller::importBackup) }
    fun pickAudio() = pickFiles("Adicionar arquivos de áudio", true) { files -> if (files.isNotEmpty()) controller.importAudio(files) }
    fun searchFor(query: String = "") { selectedTab = 4; stack.clear(); stack.add(Page("search", query)) }

    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
    DesktopPresentationTheme(page.name == "player", dark, amoled) {
        Scaffold(
            modifier = Modifier.background(Brush.verticalGradient(if (dark && !amoled) listOf(Color(0xFF242532), VisualDesign.Background)
                else listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))),
            containerColor = if (page.name == "player") MaterialTheme.colorScheme.background else Color.Transparent,
            bottomBar = {
                if (page.name != "player") VisualBottomBar(
                    listOf(VisualPage.Home, VisualPage.Explore, VisualPage.Radio, VisualPage.Library, VisualPage.Search)[selectedTab],
                    player.currentTrack?.let { track -> VisualPlayer(
                        (tracks.firstOrNull { it.id == track.id } ?: track).visualCard(), player.playWhenReady,
                        if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f, player.isBuffering) },
                    VisualActions(onPage = { tab ->
                        val route = when (tab) { VisualPage.Home -> "home"; VisualPage.Explore -> "explore"; VisualPage.Radio -> "radio"; VisualPage.Search -> "search"; VisualPage.Library -> "library" }
                        selectedTab = listOf("home", "explore", "radio", "library", "search").indexOf(route)
                        stack.clear(); stack.add(Page(route))
                    }, onPlayer = { open("player") }, onToggle = controller.audio::toggle,
                        onLike = { player.currentTrack?.let(controller::like) }))
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(busy!!, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                }
                when (page.name) {
                    "home" -> LiveHome(library, tracks,
                        onSettings = { open("settings") }, onSearch = { searchFor() }, onPlay = ::play,
                        onPlaylist = { open("detail", it.name, it.trackIds) }, onMore = { actionTrack = it })
                    "library" -> LiveLibrary(library, tracks, onSettings = { open("settings") },
                        onAdd = ::pickAudio, onSearch = { searchFor() }, onCreate = { creatingPlaylist = true },
                        onPlaylist = { open("detail", it.name, it.trackIds) }, onDetail = { title, ids -> open("detail", title, ids) },
                        onPlay = ::play, onMore = { actionTrack = it })
                    "search" -> SearchPage(controller, tracks, player, ::play, { actionTrack = it },
                        onAlbum = { open("album", it.title, remoteId = it.id) },
                        onArtist = { open("artist", it.name, remoteId = it.id) }, onSettings = { open("settings") }, initialQuery = page.title)
                    "explore" -> LiveExplore(controller, onSettings = { open("settings") }, onAlbum = { open("album", it.title, remoteId = it.id) }, onQuery = ::searchFor)
                    "radio" -> PlaylistFeed("Radio", "Estações e mixes da sua biblioteca", library.playlists.filter {
                        it.type in setOf("STASH_MIX", "DAILY_MIX")
                    }, onOpen = { open("detail", it.name, it.trackIds) })
                    "detail" -> {
                        val entries = page.ids.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
                        TrackPage(page.title, entries, player, ::back, ::play, { actionTrack = it })
                    }
                    "album", "artist" -> CatalogDetail(controller, page, player, ::back, ::play,
                        { actionTrack = it }, { open("album", it.title, remoteId = it.id) })
                    "player" -> PlayerPage(controller, player, tracks, ::back, onQueue = { open("queue") },
                        onLike = controller::like, onMore = { actionTrack = it })
                    "queue" -> TrackPage("Fila", player.queue, player, ::back, ::play, { actionTrack = it })
                    "settings" -> SettingsPage(library, ::back, onOpen = { open(it) })
                    "appearance" -> ScrollPage("Aparência", ::back) {
                        SettingsGroupCard(rows = listOf(
                            { SettingsToggleRow("Tema escuro", dark, { controller.updatePreferences(preferences.copy(dark = it)) }) },
                            { SettingsToggleRow("Preto AMOLED", amoled, { controller.updatePreferences(preferences.copy(amoled = it)) }, enabled = dark) },
                            { SettingsSliderRow("Tamanho da fonte", "${(fontScale * 100).toInt()}%", fontScale,
                                { controller.updatePreferences(preferences.copy(fontScale = it), false) },
                                { controller.updatePreferences(controller.preferences.value) }, 1f..1.5f, 4,
                                onReset = { controller.updatePreferences(preferences.copy(fontScale = 1f)) }) },
                        ))
                    }
                    "playback", "quality" -> ScrollPage(if (page.name == "playback") "Reprodução" else "Áudio", ::back) {
                        SettingsGroupCard(rows = listOf({
                            SettingsSliderRow("Volume", "${(volume * 100).toInt()}%", volume,
                                { controller.updatePreferences(preferences.copy(volume = it), false) },
                                { controller.updatePreferences(controller.preferences.value) }, 0f..1f, 0)
                        }))
                        Text("Reprodução nativa por FFmpeg e saída de áudio do Windows. O formato da fonte é exibido no player.")
                        Text("Equalizador, crossfade, Cast e saída hi-res exclusiva do Android ainda não estão disponíveis neste alvo Desktop.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    "storage" -> ScrollPage("Biblioteca e armazenamento", ::back) {
                        Text("${tracks.size} faixas · ${library.playlists.size} playlists")
                        Button(onClick = ::pickBackup, enabled = busy == null) { Text("Importar backup do celular") }
                        OutlinedButton(onClick = ::pickAudio, enabled = busy == null) { Text("Adicionar arquivos de áudio") }
                        OutlinedButton(onClick = { open("downloads") }) { Text("Downloads") }
                        Text("O backup importa a biblioteca e suas playlists. Logins e arquivos de áudio do telefone não são transferidos pelo backup.", style = MaterialTheme.typography.bodySmall)
                    }
                    "downloads" -> TrackPage("Downloads neste PC", tracks.filter { it.isDownloaded }, player,
                        ::back, ::play, { actionTrack = it })
                    "accounts" -> DesktopAccountsPage(controller, ::back, onSync = { open("sync") })
                    "sync" -> DesktopSyncPage(controller, ::back, onAccounts = { open("accounts") })
                    "about" -> ScrollPage("Sobre", ::back) {
                        NonstopWordmark()
                        Text("Base Android 0.9.114 · Desktop em migração")
                        Text("Componentes, recursos gráficos, modelos e parsers são compartilhados com o app Android. As telas ainda têm diferenças e há integrações pendentes.")
                    }
                }
            }
        }
        if (message != null || audioError != null) AlertDialog(
            onDismissRequest = { controller.message.value = null; controller.audio.error.value = null },
            title = { Text("NØNSTOP") }, text = { Text(message ?: audioError.orEmpty()) },
            confirmButton = { TextButton(onClick = { controller.message.value = null; controller.audio.error.value = null }) { Text("OK") } },
        )
        actionTrack?.let { track ->
            AlertDialog(onDismissRequest = { actionTrack = null; showPlaylistPicker = false },
                title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showPlaylistPicker) {
                            Text("Adicionar à playlist")
                            library.playlists.filter { it.type == "CUSTOM" }.forEach { playlist ->
                                TextButton(onClick = { controller.addToPlaylist(track, playlist.id); actionTrack = null; showPlaylistPicker = false }) { Text(playlist.name) }
                            }
                            if (library.playlists.none { it.type == "CUSTOM" }) Text("Crie uma playlist na Biblioteca primeiro.")
                        } else {
                            TextButton(onClick = { play(listOf(track)); actionTrack = null }) { Text("Reproduzir") }
                            TextButton(onClick = { controller.like(track); actionTrack = null }) { Text("Curtir / descurtir na biblioteca local") }
                            TextButton(onClick = { showPlaylistPicker = true }) { Text("Adicionar à playlist") }
                            TextButton(onClick = { controller.download(track); actionTrack = null }, enabled = track.youtubeId != null && busy == null) { Text("Baixar áudio") }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { actionTrack = null; showPlaylistPicker = false }) { Text("Fechar") } })
        }
        if (creatingPlaylist) AlertDialog(onDismissRequest = { creatingPlaylist = false }, title = { Text("Nova playlist") },
            text = { OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Nome") }) },
            confirmButton = { TextButton(onClick = { controller.createPlaylist(playlistName); creatingPlaylist = false; playlistName = "" }, enabled = playlistName.isNotBlank()) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { creatingPlaylist = false }) { Text("Cancelar") } })
    }
    }
}

@Composable
private fun PlaylistFeed(title: String, subtitle: String, playlists: List<SavedPlaylist>, onOpen: (SavedPlaylist) -> Unit) {
    LazyColumn {
        item { Column(Modifier.padding(20.dp)) { Text(title, style = MaterialTheme.typography.headlineLarge); Text(subtitle) } }
        if (playlists.isEmpty()) item { Text("Nenhuma estação nesta biblioteca. A geração de rádios e mixes ainda não foi portada para o Desktop.", Modifier.padding(20.dp)) }
        items(playlists, key = { it.id }) { list -> ListItem(headlineContent = { Text(list.name) },
            supportingContent = { Text("${list.trackIds.size} faixas") }, leadingContent = { Artwork(list.art, Modifier.size(64.dp)) },
            modifier = Modifier.clickable { onOpen(list) }) }
    }
}
@Composable
private fun TrackPage(title: String, tracks: List<Track>, player: PlayerState, onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit, onMore: (Track) -> Unit) {
    LazyColumn {
        item { PageHeading(title, onBack) }
        item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${tracks.size} faixas", Modifier.weight(1f))
            FilledTonalButton(onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0) }, enabled = tracks.isNotEmpty()) { Text("Reproduzir") }
        } }
        itemsIndexed(tracks, key = { index, track -> "$index:${track.id}" }) { index, track ->
            TrackListItem(track, { onPlay(tracks, index) }, isPlaying = player.currentTrack?.id == track.id, onMoreClick = { onMore(track) })
        }
    }
}
@Composable
internal fun Artwork(url: String?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(StashElevatedSurface), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, tint = StashTextSecondary)
        if (!url.isNullOrBlank()) AsyncImage(url, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@Composable
internal fun PageHeading(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
internal fun ScrollPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeading(title, onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}
private fun pickFiles(title: String, multiple: Boolean, onSelected: (List<File>) -> Unit) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        try { dialog.isMultipleMode = multiple; dialog.isVisible = true; onSelected(dialog.files.toList()) }
        finally { dialog.dispose() }
    }
}

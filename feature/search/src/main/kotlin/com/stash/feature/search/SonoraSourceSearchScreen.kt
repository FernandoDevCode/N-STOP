package com.stash.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stash.core.ui.components.bottomChromePadding
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchResult
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge

/** Spotify opens first; YouTube means conventional WEB videos, NOT YT Music. */
@Composable
fun SonoraSourceSearchScreen(
    onNavigateToArtist: (String, String, String?) -> Unit,
    onNavigateToAlbum: (AlbumSummary) -> Unit,
    viewModel: SonoraSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val state by viewModel.results.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val moreError by viewModel.moreError.collectAsStateWithLifecycle()
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val selectedPhotos by viewModel.selectedPhotos.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    var sheetItem by remember { mutableStateOf<UniversalSearchResult?>(null) }

    LaunchedEffect(viewModel) {
        merge(viewModel.messages, viewModel.delegate.userMessages)
            .collect { snackbar.showSnackbar(it) }
    }

    if (browse.isNotEmpty()) {
        SpotifyCatalogBrowseContent(viewModel, browse.last(), snackbar)
        return
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = bottomChromePadding()))
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Buscar", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Músicas, artistas, álbuns ou vídeos") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar pesquisa")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.submit()
                    keyboard?.hide()
                }),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    CatalogSource.SPOTIFY to "Spotify",
                    CatalogSource.YOUTUBE to "YouTube",
                    CatalogSource.YOUTUBE_MUSIC to "YTM",
                ).forEach { (option, label) ->
                    FilterChip(
                        selected = source == option,
                        onClick = { viewModel.select(option) },
                        label = { Text(label) },
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                IconButton(onClick = viewModel::togglePin) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = if (pinned) "Desafixar fonte" else "Fixar fonte",
                        tint = if (pinned) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (query.trim().length < 2) {
                Text("Pesquisas recentes", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(recent, key = { "${it.type}:${it.text}:${it.subtitle}" }) { item ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.onRecent(item) }
                            .padding(vertical = 13.dp)) {
                            Text(item.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            TextButton(onClick = { viewModel.removeRecent(item) }) { Text("Remover") }
                        }
                    }
                    if (recent.isNotEmpty()) item {
                        TextButton(onClick = viewModel::clearRecent) { Text("Limpar histórico") }
                    }
                }
            } else {
                when (val sourceState = state.sourceStates[source]) {
                    is SourceSearchState.Loading -> Box(Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter) {
                        CircularProgressIndicator(Modifier.padding(top = 32.dp))
                    }
                    is SourceSearchState.Data -> {
                        val list = state.orderedResults
                        val scroll = rememberLazyListState()
                        LaunchedEffect(scroll, source, list.size) {
                            snapshotFlow {
                                scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            }.distinctUntilChanged().collect { last ->
                                if (last >= list.size - 4 && list.isNotEmpty()) viewModel.loadMore()
                            }
                        }
                        LazyColumn(
                            state = scroll,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(list, key = { it.stableKey }) { result ->
                                SourceSearchRow(
                                    item = result,
                                    resolving = resolving == result.stableKey,
                                    onTap = {
                                        if (result.supports(SearchResultAction.PLAY)) {
                                            viewModel.onAction(result, SearchResultAction.PLAY)
                                        } else if (result.supports(SearchResultAction.NAVIGATE) &&
                                            result.identity.source == CatalogSource.SPOTIFY
                                        ) {
                                            viewModel.openSpotifyBrowse(result)
                                        } else if (result.supports(SearchResultAction.NAVIGATE) &&
                                            result.identity.source == CatalogSource.YOUTUBE_MUSIC
                                        ) {
                                            when (result.type) {
                                                SearchResultType.ARTIST -> onNavigateToArtist(
                                                    result.identity.catalogId, result.title,
                                                    selectedPhotos[result.identity.catalogId]?.photoUrl
                                                        ?: result.artworkUrl,
                                                )
                                                SearchResultType.ALBUM -> onNavigateToAlbum(
                                                    AlbumSummary(
                                                        id = result.identity.catalogId,
                                                        title = result.title,
                                                        artist = result.subtitle.orEmpty(),
                                                        thumbnailUrl = result.artworkUrl,
                                                        year = null,
                                                    ),
                                                )
                                                else -> Unit
                                            }
                                        }
                                    },
                                    onLongPress = { sheetItem = result },
                                )
                            }
                            if (viewModel.hasMore() || loadingMore) item {
                                Box(Modifier.fillMaxWidth().padding(10.dp),
                                    contentAlignment = Alignment.Center) {
                                    if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                                    else TextButton(onClick = viewModel::loadMore) {
                                        Text("Carregar mais")
                                    }
                                }
                            }
                            if (moreError != null) item {
                                TextButton(onClick = viewModel::loadMore) {
                                    Text("Falha ao carregar mais · tentar novamente")
                                }
                            }
                        }
                    }
                    is SourceSearchState.Error -> {
                        Text(sourceState.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = viewModel::retry) { Text("Tentar novamente") }
                    }
                    is SourceSearchState.Empty -> Text(
                        "Nenhum resultado nesta fonte.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text("Pesquise nesta fonte para ver os resultados.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    sheetItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { sheetItem = null }) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp))
                listOf(
                    SearchResultAction.PLAY to "Reproduzir",
                    SearchResultAction.PLAY_NEXT to "Reproduzir a seguir",
                    SearchResultAction.ADD_TO_QUEUE to "Adicionar à fila",
                    SearchResultAction.DOWNLOAD to "Baixar",
                ).filter { item.supports(it.first) }.forEach { (action, label) ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            sheetItem = null
                            viewModel.onAction(item, action)
                        },
                    ) { Text(label) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceSearchRow(
    item: UniversalSearchResult,
    resolving: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isVideo = item.type == SearchResultType.VIDEO
    val width = if (isVideo) 90.dp else 54.dp
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(width).height(54.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            if (item.artworkUrl != null) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
            Text(
                buildString {
                    append(item.subtitle.orEmpty())
                    if (item.type == SearchResultType.ARTIST) append(" · Artista")
                    if (item.type == SearchResultType.ALBUM) append(" · Álbum")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (resolving) CircularProgressIndicator(Modifier.size(18.dp))
    }
}

/** In-app Spotify pages, keyed by real Spotify IDs; never enter the YTM route. */
@Composable
private fun SpotifyCatalogBrowseContent(
    viewModel: SonoraSearchViewModel,
    page: SpotifyBrowsePage,
    snackbar: SnackbarHostState,
) {
    BackHandler { viewModel.closeSpotifyBrowse() }
    var sheetItem by remember { mutableStateOf<UniversalSearchResult?>(null) }
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()
    val scroll = rememberLazyListState()
    LaunchedEffect(page.entry.stableKey) { scroll.scrollToItem(0) }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomChromePadding())) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = viewModel::closeSpotifyBrowse) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Column(Modifier.weight(1f)) {
                    Text(page.entry.title, style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (page.entry.type == SearchResultType.ARTIST)
                            "Spotify · Lançamentos" else "Spotify · Faixas do álbum",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (page.error != null) {
                Text(page.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = viewModel::retrySpotifyBrowse) { Text("Tentar novamente") }
            }
            if (page.loading && page.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.padding(top = 32.dp))
                }
            } else if (page.items.isEmpty() && page.error == null) {
                Text("Nenhum lançamento encontrado no catálogo Spotify.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LaunchedEffect(scroll, page.entry.stableKey, page.items.size) {
                    snapshotFlow {
                        scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    }.distinctUntilChanged().collect { last ->
                        if (last >= page.items.size - 4 && page.continuation != null &&
                            !page.loading) viewModel.loadSpotifyBrowse()
                    }
                }
                LazyColumn(
                    state = scroll,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(page.items, key = { it.stableKey }) { result ->
                        SourceSearchRow(
                            item = result,
                            resolving = resolving == result.stableKey,
                            onTap = {
                                when {
                                    result.type == SearchResultType.ALBUM ->
                                        viewModel.openSpotifyBrowse(result)
                                    result.type == SearchResultType.TRACK ->
                                        viewModel.onAction(result, SearchResultAction.PLAY)
                                }
                            },
                            onLongPress = { sheetItem = result },
                        )
                    }
                    if (page.continuation != null || page.loading) item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (page.loading) CircularProgressIndicator(Modifier.size(24.dp))
                            else TextButton(onClick = viewModel::loadSpotifyBrowse) {
                                Text("Carregar mais lançamentos")
                            }
                        }
                    }
                }
            }
        }
    }
    sheetItem?.let { result ->
        ModalBottomSheet(onDismissRequest = { sheetItem = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                listOf(
                    SearchResultAction.PLAY to "Reproduzir",
                    SearchResultAction.PLAY_NEXT to "Reproduzir a seguir",
                    SearchResultAction.ADD_TO_QUEUE to "Adicionar à fila",
                    SearchResultAction.DOWNLOAD to "Baixar",
                ).filter { result.supports(it.first) }.forEach { (action, label) ->
                    TextButton(onClick = {
                        sheetItem = null
                        viewModel.onAction(result, action)
                    }) { Text(label) }
                }
            }
        }
    }
}

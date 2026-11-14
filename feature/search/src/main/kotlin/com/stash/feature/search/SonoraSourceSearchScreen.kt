package com.stash.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonoraSourceSearchScreen(
    @Suppress("UNUSED_PARAMETER") onNavigateToArtist: (String, String, String?) -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigateToAlbum: (AlbumSummary) -> Unit,
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
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
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
            Text(
                "Buscar",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 27.sp,
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        RoundedCornerShape(15.dp),
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChanged,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.submit()
                            keyboard?.hide()
                        }),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text(
                                    "O que você quer ouvir?",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 16.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                inner()
                            }
                        },
                    )
                }
                if (query.isNotBlank()) IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = { viewModel.onQueryChanged("") },
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpar pesquisa",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    CatalogSource.SPOTIFY to "Spotify",
                    CatalogSource.YOUTUBE to "YouTube",
                ).forEach { (option, label) ->
                    val selected = source == option
                    Box(
                        modifier = Modifier.height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                            )
                            .clickable { viewModel.select(option) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = if (selected) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = viewModel::togglePin)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Default.PushPin,
                        contentDescription = if (pinned) "Desafixar fonte" else "Fixar fonte",
                        modifier = Modifier.size(18.dp),
                        tint = if (pinned) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (pinned) "Fixado" else "Fixar",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pinned) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (query.trim().length < 2) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recentes",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                        ))
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    if (recent.isNotEmpty()) TextButton(
                        onClick = viewModel::clearRecent,
                    ) { Text("Limpar histórico", style = MaterialTheme.typography.labelMedium) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomChromePadding()),
                ) {
                    items(recent, key = { "${it.type}:${it.text}:${it.subtitle}" }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { viewModel.onRecent(item) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(Icons.Default.History, contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.text, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.SansSerif, fontSize = 15.sp,
                                ), modifier = Modifier.weight(1f))
                            IconButton(
                                modifier = Modifier.size(40.dp),
                                onClick = { viewModel.removeRecent(item) },
                            ) {
                                Icon(Icons.Default.Clear,
                                    contentDescription = "Remover ${item.text}",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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
                        val groups = if (source == CatalogSource.SPOTIFY) listOf(
                            SearchResultType.TRACK to "Músicas",
                            SearchResultType.ARTIST to "Artistas",
                            SearchResultType.ALBUM to "Álbuns",
                        ) else listOf(SearchResultType.VIDEO to "Vídeos")
                        val sectionCount = groups.count { (kind, _) ->
                            list.any { it.type == kind }
                        }
                        val scroll = rememberLazyListState()
                        LaunchedEffect(source, query) { scroll.scrollToItem(0) }
                        LaunchedEffect(scroll, source, list.size) {
                            snapshotFlow {
                                scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            }.distinctUntilChanged().collect { last ->
                                if (last >= list.size + sectionCount - 4 && list.isNotEmpty()) {
                                    viewModel.loadMore()
                                }
                            }
                        }
                        LazyColumn(
                            state = scroll,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomChromePadding()),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            groups.forEach { (kind, header) ->
                                val sectionItems = list.filter { it.type == kind }
                                if (sectionItems.isNotEmpty()) {
                                    item(key = "section_${kind.name}") {
                                        Text(
                                            header,
                                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.SansSerif,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                        )
                                    }
                                    items(sectionItems, key = { it.stableKey }) { result ->
                                        SourceSearchRow(
                                            item = result,
                                            resolving = resolving == result.stableKey,
                                            onTap = {
                                                if (result.supports(SearchResultAction.PLAY)) {
                                                    viewModel.onAction(result, SearchResultAction.PLAY)
                                                } else if (
                                                    result.supports(SearchResultAction.NAVIGATE) &&
                                                    result.identity.source == CatalogSource.SPOTIFY
                                                ) {
                                                    viewModel.openSpotifyBrowse(result)
                                                }
                                            },
                                            onLongPress = { sheetItem = result },
                                        )
                                    }
                                }
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
                    SearchResultAction.NAVIGATE to "Abrir detalhes",
                ).filter { item.supports(it.first) }.forEach { (action, label) ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            sheetItem = null
                            if (action == SearchResultAction.NAVIGATE) {
                                if (item.identity.source == CatalogSource.SPOTIFY) {
                                    viewModel.openSpotifyBrowse(item)

                                }
                            } else viewModel.onAction(item, action)
                        },
                    ) { Text(label) }
                }
                if (item.identity.source == CatalogSource.SPOTIFY) {
                    TextButton(onClick = {
                        uriHandler.openUri("https://open.spotify.com/${item.type.name.lowercase()}/${item.identity.catalogId}")
                        sheetItem = null
                    }) { Text("Abrir no Spotify") }
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
                    contentScale = if (item.identity.source == CatalogSource.SPOTIFY)
                        ContentScale.Fit else ContentScale.Crop,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyCatalogBrowseContent(
    viewModel: SonoraSearchViewModel,
    page: SpotifyBrowsePage,
    snackbar: SnackbarHostState,
) {
    BackHandler { viewModel.closeSpotifyBrowse() }
    var sheetItem by remember { mutableStateOf<UniversalSearchResult?>(null) }
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
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
                    contentPadding = PaddingValues(bottom = bottomChromePadding()),
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
                    SearchResultAction.NAVIGATE to "Abrir detalhes",
                ).filter { result.supports(it.first) }.forEach { (action, label) ->
                    TextButton(onClick = {
                        sheetItem = null
                        if (action == SearchResultAction.NAVIGATE) {
                            viewModel.openSpotifyBrowse(result)
                        } else viewModel.onAction(result, action)
                    }) { Text(label) }
                }
                TextButton(onClick = {
                    uriHandler.openUri("https://open.spotify.com/${result.type.name.lowercase()}/${result.identity.catalogId}")
                    sheetItem = null
                }) { Text("Abrir no Spotify") }
            }
        }
    }
}

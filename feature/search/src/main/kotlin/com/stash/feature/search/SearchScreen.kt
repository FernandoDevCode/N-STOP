package com.stash.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.stash.core.data.artistphotos.SelectedArtistPhoto
import com.stash.core.ui.components.bottomChromePadding
import com.stash.core.ui.visual.VisualDesign
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.RecentSearchKind
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchActions
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.contract.UniversalSearchUiState
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateToArtist: (artistId: String, name: String, avatarUrl: String?) -> Unit,
    onNavigateToAlbum: (album: AlbumSummary) -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyboard = LocalSoftwareKeyboardController.current
    val currentArtist by rememberUpdatedState(onNavigateToArtist)
    val currentAlbum by rememberUpdatedState(onNavigateToAlbum)
    LaunchedEffect(viewModel, lifecycle, keyboard) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.destinations.collect { destination ->
                keyboard?.hide()
                when (destination) {
                    is SearchNavigation.Artist -> currentArtist(destination.id, destination.name, destination.artworkUrl)
                    is SearchNavigation.Album -> currentAlbum(destination.album)
                }
            }
        }
    }

    val activeSession = session
    if (activeSession == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    } else {
        val state by activeSession.state.collectAsStateWithLifecycle()
        val photos by viewModel.selectedArtistPhotos.collectAsStateWithLifecycle()
        UniversalSearchContent(state, activeSession, artistPhotos = photos)
    }
}

@Composable
internal fun UniversalSearchContent(
    state: UniversalSearchUiState,
    actions: UniversalSearchActions,
    autoFocus: Boolean = true,
    artistPhotos: Map<String, SelectedArtistPhoto> = emptyMap(),
) {
    val list = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val query by rememberUpdatedState(state.query)
    val resultKeys by rememberUpdatedState(remember(state.orderedResults) {
        state.orderedResults.mapTo(hashSetOf()) { it.stableKey }
    })
    LaunchedEffect(list, actions, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                snapshotFlow {
                    val layout = list.layoutInfo
                    val visibleEnd = layout.viewportEndOffset - layout.afterContentPadding
                    val visibleKeys = layout.visibleItemsInfo.mapNotNullTo(linkedSetOf()) { item ->
                        (item.key as? String)?.takeIf {
                            it in resultKeys && item.offset < visibleEnd &&
                                item.offset + item.size > layout.viewportStartOffset
                        }
                    }
                    query to visibleKeys
                }.collect { (visibleQuery, keys) -> actions.onVisibleResultsChanged(visibleQuery, keys) }
            } finally {
                actions.onVisibleResultsChanged(query, emptySet())
            }
        }
    }
    var listQuery by rememberSaveable { mutableStateOf(state.query) }
    LaunchedEffect(state.query) {
        if (listQuery != state.query) {
            listQuery = state.query
            list.scrollToItem(0)
        }
    }
    // Search consumes the IME insets. The existing app scaffold owns the atmosphere and chrome.
    Scaffold(Modifier.imePadding(), containerColor = Color.Transparent) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            SearchHeader(state, actions, autoFocus)
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("searchList"),
                state = list,
                contentPadding = PaddingValues(bottom = bottomChromePadding(12.dp)),
            ) {
                item("sources") { SourceFilters(state.selection, actions) }
                if (state.selection.enabled.isEmpty()) {
                    item("noSources") { SearchMessage(R.string.universal_search_select_source) }
                }
                if (state.query.isBlank()) {
                    if (state.recentSearches.isEmpty()) {
                        item("prompt") { SearchMessage(R.string.universal_search_prompt) }
                    } else {
                        item("recentHeader") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = VisualDesign.Gutter),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.nostop_search_recent),
                                    Modifier.weight(1f).semantics { heading() },
                                    style = VisualDesign.Heading,
                                )
                                TextButton(onClick = actions::onRecentCleared) {
                                    Text(stringResource(R.string.universal_search_clear_history))
                                }
                            }
                        }
                        items(state.recentSearches, key = { "recent:${it.id}" }) { item ->
                            RecentSearchRow(item, actions, artistPhotos)
                        }
                    }
                } else if (state.selection.enabled.isNotEmpty()) {
                    val sources = CatalogSource.entries.filter { it in state.selection.activeCatalogs }
                    val statuses = sources.map { state.sourceStates[it] ?: SourceSearchState.Idle }
                    val finished = statuses.none { it is SourceSearchState.Loading || it == SourceSearchState.Idle }
                    val noResults = state.orderedResults.isEmpty()
                    if (noResults && statuses.all { it is SourceSearchState.Empty }) {
                        item("empty") { SearchMessage(R.string.nostop_search_no_results) }
                    } else if (noResults && finished && statuses.any { it is SourceSearchState.Error }) {
                        item("error") { SearchMessage(R.string.nostop_search_failed) }
                    }

                    for (source in sources) {
                        when (val status = state.sourceStates[source]) {
                            null, SourceSearchState.Idle, is SourceSearchState.Data -> Unit
                            else -> item("status:${source.name}") { SourceStatus(source, status, actions) }
                        }
                    }

                    if (state.orderedResults.any { !it.supports(SearchResultAction.NAVIGATE) }) {
                        item("catalogHint") { SearchMessage(R.string.universal_search_catalog_hint, compact = true) }
                    }
                    items(state.orderedResults, key = UniversalSearchResult::stableKey) { result ->
                        ResultRow(result, actions, artistPhotos, state.artworkOverrides[result.stableKey])
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(state: UniversalSearchUiState, actions: UniversalSearchActions, autoFocus: Boolean) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.padding(horizontal = VisualDesign.Gutter)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.nostop_search_title),
                Modifier.weight(1f).semantics { heading() },
                style = VisualDesign.PageTitle,
            )
            val pinned = state.selection.isPinned
            val description = stringResource(
                if (pinned) R.string.universal_search_pinned else R.string.universal_search_temporary,
            )
            IconToggleButton(
                checked = pinned,
                onCheckedChange = { actions.onPinToggled() },
                modifier = Modifier.testTag("searchPin").semantics { stateDescription = description },
            ) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    stringResource(R.string.universal_search_pin_sources),
                )
            }
        }
        val queryLabel = stringResource(R.string.universal_search_query)
        OutlinedTextField(
            value = state.query,
            onValueChange = actions::onQueryChanged,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("searchQuery")
                .semantics { contentDescription = queryLabel },
            singleLine = true,
            textStyle = VisualDesign.Body,
            placeholder = {
                Text(
                    stringResource(R.string.nostop_search_placeholder),
                    style = VisualDesign.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                {
                    IconButton(onClick = actions::onClearQuery) {
                        Icon(Icons.Filled.Close, stringResource(R.string.nostop_search_clear))
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                actions.onSearchSubmitted()
            }),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = VisualDesign.Surface,
                unfocusedContainerColor = VisualDesign.Surface,
            ),
        )
    }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoFocus) {
        if (autoFocus && !focused) {
            focused = true
            if (state.query.isEmpty()) {
                withFrameNanos { }
                withFrameNanos { }
                focusRequester.requestFocus()
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SourceFilters(selection: SearchSourceSelection, actions: UniversalSearchActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = VisualDesign.Gutter, vertical = 6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.universal_search_sources),
                Modifier.align(Alignment.CenterVertically).semantics { heading() },
                style = VisualDesign.Body,
            )
            TextButton(
                onClick = actions::onRestoreDefaultSources,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(stringResource(R.string.universal_search_restore), style = VisualDesign.Body)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (source in CatalogSource.entries) {
                val selected = source in selection.enabled
                FilterChip(
                    selected = selected,
                    onClick = { actions.onSourceToggled(source) },
                    label = { Text(stringResource(source.nameRes), style = VisualDesign.Body) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(VisualDesign.Radius.Filter),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .15f),
                        selectedLabelColor = VisualDesign.Ink,
                        labelColor = VisualDesign.Muted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SourceStatus(source: CatalogSource, state: SourceSearchState, actions: UniversalSearchActions) {
    val sourceName = stringResource(source.nameRes)
    val statusRes = when (state) {
        is SourceSearchState.Loading -> R.string.universal_search_loading_source
        is SourceSearchState.Empty -> R.string.universal_search_empty_source
        is SourceSearchState.Error -> R.string.universal_search_error_source
        is SourceSearchState.Unavailable -> R.string.universal_search_unavailable_source
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = VisualDesign.Gutter).heightIn(min = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state is SourceSearchState.Loading) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        Text(
            stringResource(statusRes, sourceName),
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            color = VisualDesign.Muted,
            style = VisualDesign.Caption,
        )
        if (state is SourceSearchState.Error && state.canRetry) {
            SourceStatusAction(
                R.string.universal_search_retry_short,
                stringResource(R.string.universal_search_retry_source, sourceName),
            ) { actions.onRetrySource(source) }
        }
    }
}

@Composable
private fun SourceStatusAction(label: Int, description: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(
            stringResource(label),
            Modifier.clearAndSetSemantics { contentDescription = description },
            style = VisualDesign.Body,
        )
    }
}

@Composable
private fun SearchMessage(res: Int, compact: Boolean = false) {
    Text(
        stringResource(res),
        Modifier.padding(horizontal = VisualDesign.Gutter, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = VisualDesign.Muted,
        style = if (compact) VisualDesign.Caption else VisualDesign.Body,
    )
}

@Composable
private fun ResultRow(
    result: UniversalSearchResult,
    actions: UniversalSearchActions,
    artistPhotos: Map<String, SelectedArtistPhoto>,
    preferredArtwork: String?,
) {
    val destination = result.supports(SearchResultAction.NAVIGATE)
    var preferredLoaded by remember(preferredArtwork) { mutableStateOf(false) }
    val unavailable = stringResource(R.string.universal_search_catalog_only)
    val interaction = if (destination) {
        Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.universal_search_open_details)) {
            actions.onResultAction(result, SearchResultAction.NAVIGATE, preferredArtwork.takeIf { preferredLoaded })
        }
    } else {
        Modifier.semantics(mergeDescendants = true) { stateDescription = unavailable }
    }
    Row(
        Modifier.fillMaxWidth().testTag(result.stableKey).then(interaction)
            .heightIn(min = 64.dp).padding(horizontal = VisualDesign.Gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchArtwork(
            displayedSearchArtwork(result.identity, result.type, preferredArtwork ?: result.artworkUrl, artistPhotos),
            result.type.icon,
            result.type == SearchResultType.ARTIST,
            fallbackUrl = result.artworkUrl?.takeIf {
                preferredArtwork != null && it != preferredArtwork && !preferredLoaded
            },
            onLoaded = { if (preferredArtwork != null) preferredLoaded = true },
        )
        Column(Modifier.weight(1f)) {
            SearchRowTitle(result.title)
            val detail = listOfNotNull(result.subtitle, result.versionLabel).joinToString(" · ")
            if (detail.isNotEmpty()) SearchRowDetail(detail, maxLines = 1)
            val source = stringResource(result.identity.source.nameRes)
            val type = stringResource(result.type.labelRes)
            val duration = result.durationMs?.takeIf { it >= 1_000 }?.let { formatSearchDuration(it) }
            val explicit = if (result.isExplicit) stringResource(R.string.universal_search_explicit) else null
            SearchRowDetail(listOfNotNull(source, type, duration, explicit).joinToString(" · "))
        }
        if (destination) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = VisualDesign.Muted)
    }
}

@Composable
private fun RecentSearchRow(
    item: RecentSearchItem,
    actions: UniversalSearchActions,
    artistPhotos: Map<String, SelectedArtistPhoto>,
) {
    val canOpen = item.kind == RecentSearchKind.QUERY || item.destination() != null
    val detail = listOfNotNull(item.subtitle, item.resultIdentity?.let { stringResource(it.source.nameRes) })
        .joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .padding(start = VisualDesign.Gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val unavailable = stringResource(R.string.universal_search_catalog_only)
        val interaction = if (canOpen) Modifier.clickable(role = Role.Button) { actions.onRecentSelected(item) }
            else Modifier.semantics(mergeDescendants = true) { stateDescription = unavailable }
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp).then(interaction).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(
                displayedSearchArtwork(item.resultIdentity, item.resultType, item.artworkUrl, artistPhotos),
                item.resultType?.icon ?: Icons.Outlined.History,
                item.resultType == SearchResultType.ARTIST,
            )
            Column(Modifier.weight(1f)) {
                SearchRowTitle(item.label)
                if (detail.isNotEmpty()) SearchRowDetail(detail)
            }
        }
        IconButton(onClick = { actions.onRecentRemoved(item) }) {
            Icon(Icons.Filled.Close, stringResource(R.string.universal_search_remove_recent, item.label))
        }
    }
}

internal fun displayedSearchArtwork(
    identity: CatalogIdentity?,
    type: SearchResultType?,
    artworkUrl: String?,
    selections: Map<String, SelectedArtistPhoto>,
): String? =
    if (identity?.source == CatalogSource.YOUTUBE_MUSIC && type == SearchResultType.ARTIST) {
        selections[identity.catalogId]?.photoUrl ?: artworkUrl
    } else artworkUrl

@Composable
private fun SearchArtwork(
    url: String?,
    fallback: ImageVector,
    circle: Boolean,
    fallbackUrl: String? = null,
    onLoaded: () -> Unit = {},
) {
    Box(
        Modifier.size(52.dp)
            .clip(if (circle) CircleShape else RoundedCornerShape(VisualDesign.Radius.Card))
            .background(VisualDesign.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(fallback, null, tint = VisualDesign.Muted)
        if (fallbackUrl != null) {
            // Keep the original visible while the replacement loads, including on CDN failure.
            AsyncImage(fallbackUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { onLoaded() },
            )
        }
    }
}

@Composable
private fun SearchRowTitle(text: String) {
    Text(text, style = VisualDesign.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SearchRowDetail(text: String, maxLines: Int = 2) {
    Text(text, color = VisualDesign.Muted, style = VisualDesign.Caption, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis)
}

@Composable
private fun formatSearchDuration(ms: Long): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return remember(ms, locale) {
        val wholeSeconds = ms / 1000
        val hours = wholeSeconds / 3600
        val minutes = wholeSeconds / 60 % 60
        val seconds = wholeSeconds % 60
        val numbers = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
        val first = numbers.format(if (hours == 0L) minutes else hours)
        numbers.minimumIntegerDigits = 2
        if (hours == 0L) "$first:${numbers.format(seconds)}"
        else "$first:${numbers.format(minutes)}:${numbers.format(seconds)}"
    }
}

private val CatalogSource.nameRes: Int get() = when (this) {
    CatalogSource.SPOTIFY -> R.string.universal_search_spotify
    CatalogSource.YOUTUBE -> R.string.universal_search_youtube
    CatalogSource.YOUTUBE_MUSIC -> R.string.universal_search_youtube_music
}

private val SearchResultType.labelRes: Int get() = when (this) {
    SearchResultType.TRACK -> R.string.nostop_search_kind_song
    SearchResultType.VIDEO -> R.string.universal_search_video
    SearchResultType.ARTIST -> R.string.nostop_search_kind_artist
    SearchResultType.ALBUM -> R.string.universal_search_album
}

private val SearchResultType.icon: ImageVector get() = when (this) {
    SearchResultType.TRACK -> Icons.Outlined.MusicNote
    SearchResultType.VIDEO -> Icons.Outlined.VideoLibrary
    SearchResultType.ARTIST -> Icons.Outlined.Person
    SearchResultType.ALBUM -> Icons.Outlined.Album
}

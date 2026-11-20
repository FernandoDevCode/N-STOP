package com.stash.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.stash.core.data.artistphotos.SelectedArtistPhoto
import com.stash.core.ui.components.bottomChromePadding
import com.stash.core.ui.visual.LocalVisualAmoled
import com.stash.core.ui.visual.LocalVisualDark
import com.stash.core.ui.visual.VisualDesign
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.RecentSearchKind
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchActions
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.contract.UniversalSearchUiState
import com.stash.feature.search.engine.exactPlaybackVideoId
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

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
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel, lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                viewModel.playErrors.collectLatest { snackbar.showSnackbar(context.getString(it)) }
            } finally {
                viewModel.cancelPendingPlayback()
            }
        }
    }
    LaunchedEffect(viewModel, lifecycle, keyboard) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.destinations.collect { destination ->
                viewModel.cancelPendingPlayback()
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
        val playingRequest by viewModel.playingRequest.collectAsStateWithLifecycle()
        UniversalSearchContent(
            state, activeSession, artistPhotos = photos, preparingKey = playingRequest?.stableKey,
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomChromePadding(0.dp))) },
        )
    }
}

@Composable
fun searchWindowBackground(): Color = when {
    LocalVisualAmoled.current -> Color.Black
    LocalVisualDark.current -> Color(0xFF131414)
    else -> Color(0xFFF7F6F3)
}

@Composable
internal fun searchSurface(emphasis: Float = .055f): Color =
    VisualDesign.Ink.copy(alpha = emphasis).compositeOver(searchWindowBackground())

private enum class SearchCategory(val label: Int) {
    TOP(R.string.universal_search_top),
    ARTISTS(R.string.nostop_search_artists),
    ALBUMS(R.string.nostop_search_albums),
    YOUTUBE(R.string.universal_search_youtube),
    SONGS(R.string.nostop_search_songs),
}

private val UniversalSearchResult.category: SearchCategory
    get() = when {
        identity.source == CatalogSource.YOUTUBE -> SearchCategory.YOUTUBE
        type == SearchResultType.ARTIST -> SearchCategory.ARTISTS
        type == SearchResultType.ALBUM -> SearchCategory.ALBUMS
        else -> SearchCategory.SONGS
    }

@Composable
internal fun UniversalSearchContent(
    state: UniversalSearchUiState,
    actions: UniversalSearchActions,
    autoFocus: Boolean = true,
    artistPhotos: Map<String, SelectedArtistPhoto> = emptyMap(),
    preparingKey: String? = null,
    snackbarHost: @Composable () -> Unit = {},
) {
    val list = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = state.query.isNotBlank()
    var selectedCategory by rememberSaveable { mutableStateOf(SearchCategory.TOP) }
    val category = if (active) selectedCategory else SearchCategory.TOP
    var sourceSheetOpen by rememberSaveable { mutableStateOf(false) }
    var detailsKey by remember(state.query) { mutableStateOf<String?>(null) }
    var detailsArtwork by remember(state.query) { mutableStateOf<String?>(null) }
    val ranked = state.orderedResults
    val grouped = remember(ranked) { ranked.groupBy { it.category } }
    val featured = ranked.firstOrNull()
    val topSections = remember(grouped, featured) {
        grouped.mapValues { (_, results) ->
            val remaining = if (results.firstOrNull() == featured) results.drop(1) else results
            remaining.take(3)
        }.filterValues { it.isNotEmpty() }
    }
    val visibleResults = remember(ranked, category) {
        if (category == SearchCategory.TOP) ranked else grouped[category].orEmpty()
    }

    fun openSources() {
        keyboard?.hide()
        focus.clearFocus()
        detailsKey = null
        sourceSheetOpen = true
    }

    fun selectCategory(next: SearchCategory) {
        if (category == next) return
        selectedCategory = next
        keyboard?.hide()
        list.requestScrollToItem(0)
    }

    val query by rememberUpdatedState(state.query)
    val resultKeys by rememberUpdatedState(remember(visibleResults) {
        visibleResults.mapTo(hashSetOf()) { it.stableKey }
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
        if (!active || listQuery != state.query) selectedCategory = SearchCategory.TOP
        if (listQuery != state.query) {
            listQuery = state.query
            list.scrollToItem(0)
        }
    }

    // The window paints the neutral backdrop behind the system bars; Search owns its IME insets.
    Scaffold(Modifier.imePadding(), containerColor = Color.Transparent, snackbarHost = snackbarHost) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                SearchHeader(state, actions, autoFocus, ::openSources)
                if (active) SearchCategories(category, ::selectCategory)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("searchList"),
                    state = list,
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomChromePadding(12.dp)),
                ) {
                    if (!active) {
                        if (state.recentSearches.isEmpty()) {
                            item("prompt") { SearchMessage(R.string.universal_search_prompt) }
                        } else {
                            item("recentHeader") {
                                SearchSectionHeading(
                                    R.string.nostop_search_recent,
                                    actionLabel = R.string.universal_search_clear_history,
                                    onAction = actions::onRecentCleared,
                                )
                            }
                            items(state.recentSearches, key = { "recent:${it.id}" }) { item ->
                                RecentSearchRow(item, actions, artistPhotos)
                            }
                        }
                        return@LazyColumn
                    }
                    val enabled = state.selection.activeCatalogs
                    if (category == SearchCategory.YOUTUBE && CatalogSource.YOUTUBE !in enabled) {
                        item("youtubeOff") {
                            SearchMessage(R.string.universal_search_youtube_disabled)
                        }
                        return@LazyColumn
                    }
                    val sources = when (category) {
                        SearchCategory.TOP -> enabled
                        SearchCategory.YOUTUBE -> setOf(CatalogSource.YOUTUBE)
                        else -> enabled - CatalogSource.YOUTUBE
                    }
                    if (sources.isEmpty()) {
                        item("noSources") {
                            SearchMessage(if (enabled.isEmpty()) R.string.universal_search_select_source
                                else R.string.universal_search_music_disabled)
                        }
                        return@LazyColumn
                    }

                    val statuses = sources.map { state.sourceStates[it] ?: SourceSearchState.Idle }
                    val finished = statuses.none { it is SourceSearchState.Loading || it == SourceSearchState.Idle }
                    if (visibleResults.isEmpty() && finished) {
                        when {
                            statuses.any { it is SourceSearchState.Error } ->
                                item("error") { SearchMessage(R.string.nostop_search_failed) }
                            statuses.none { it is SourceSearchState.Unavailable } ->
                                item("empty") { SearchMessage(R.string.nostop_search_no_results) }
                        }
                    }
                    val showIndividualEmpty = category == SearchCategory.TOP && visibleResults.isEmpty() &&
                        statuses.any { it is SourceSearchState.Error || it is SourceSearchState.Unavailable }
                    for (source in sources) {
                        val status = state.sourceStates[source] ?: continue
                        if (status == SourceSearchState.Idle || status is SourceSearchState.Data) continue
                        if (status is SourceSearchState.Empty && !showIndividualEmpty) continue
                        item("status:${source.name}") { SourceStatus(source, status, actions) }
                    }

                    val row: @Composable (UniversalSearchResult, Boolean) -> Unit = { result, highlight ->
                        ResultRow(
                            result, actions, artistPhotos, state.artworkOverrides[result.stableKey],
                            preparing = preparingKey == result.stableKey, featured = highlight,
                            onDetails = { artwork ->
                                focus.clearFocus()
                                detailsArtwork = artwork
                                detailsKey = result.stableKey
                            },
                        )
                    }

                    if (category == SearchCategory.TOP && featured != null) {
                        item("featuredHeading") {
                            SearchSectionHeading(R.string.universal_search_best_match)
                        }
                        item(featured.stableKey, contentType = "featured") { row(featured, true) }
                        item("detailsHint") { SearchMessage(R.string.universal_search_details_hint, compact = true) }
                        for ((section, preview) in topSections) {
                            item("section:${section.name}") {
                                SearchSectionHeading(section.label, R.string.universal_search_see_all) {
                                    selectCategory(section)
                                }
                            }
                            items(preview, key = UniversalSearchResult::stableKey, contentType = { "result" }) {
                                row(it, false)
                            }
                        }
                    } else {
                        items(visibleResults, key = UniversalSearchResult::stableKey, contentType = { "result" }) {
                            row(it, false)
                        }
                    }
                }
            }
        }
    }

    val detail = detailsKey?.let { key -> ranked.firstOrNull { it.stableKey == key } }
    when {
        sourceSheetOpen -> SearchSourcesSheet(state.selection, actions) { sourceSheetOpen = false }
        detail != null -> SearchResultSheet(
            result = detail,
            shownArtwork = detailsArtwork,
            artistPhotos = artistPhotos,
            preparing = preparingKey == detail.stableKey,
            onDismiss = { detailsKey = null },
            onAction = { action ->
                detailsKey = null
                actions.onResultAction(detail, action, detailsArtwork)
            },
        )
    }
}

@Composable
private fun SearchSectionHeading(label: Int, actionLabel: Int? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(start = VisualDesign.Gutter, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title = stringResource(label)
        Text(title, Modifier.weight(1f).semantics { heading() }, style = VisualDesign.Heading)
        when (actionLabel) {
            R.string.universal_search_see_all -> SourceStatusAction(
                actionLabel, stringResource(R.string.universal_search_see_all_category, title), onAction,
            )
            null -> Unit
            else -> TextButton(onAction) { Text(stringResource(actionLabel)) }
        }
    }
}

@Composable
private fun SearchHeader(
    state: UniversalSearchUiState,
    actions: UniversalSearchActions,
    autoFocus: Boolean,
    onSources: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val queryLabel = stringResource(R.string.universal_search_query)
    val fieldShape = RoundedCornerShape(11.dp)
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldText = VisualDesign.Body.copy(fontSize = 15.sp, lineHeight = 20.sp)
    Column(
        Modifier.padding(start = VisualDesign.Gutter, end = VisualDesign.Gutter, top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.nostop_search_title),
            Modifier.semantics { heading() },
            style = VisualDesign.HeroTitle.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = actions::onQueryChanged,
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }.testTag("searchQuery")
                    .semantics { contentDescription = queryLabel },
                textStyle = fieldText.copy(color = VisualDesign.Ink),
                cursorBrush = SolidColor(VisualDesign.Ink),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    actions.onSearchSubmitted()
                }),
                decorationBox = { innerField ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(fieldShape)
                            .background(searchSurface(.085f))
                            .border(1.dp, if (fieldFocused) VisualDesign.Muted.copy(alpha = .45f) else Color.Transparent, fieldShape)
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = VisualDesign.Muted)
                        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            if (state.query.isEmpty()) {
                                Text(
                                    stringResource(R.string.nostop_search_placeholder),
                                    Modifier.padding(end = 8.dp),
                                    style = fieldText, color = VisualDesign.Muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerField()
                        }
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = actions::onClearQuery, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.nostop_search_clear), Modifier.size(18.dp))
                            }
                        }
                    }
                },
            )
            IconButton(
                onClick = onSources,
                modifier = Modifier.size(48.dp).clip(fieldShape).background(searchSurface())
                    .testTag("searchSourceSettings"),
            ) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.universal_search_source_settings), Modifier.size(21.dp))
            }
        }
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

@Composable
private fun SearchCategories(selected: SearchCategory, onSelect: (SearchCategory) -> Unit) {
    val ink = VisualDesign.Ink
    val base = searchWindowBackground()
    val idleFill = searchSurface(.074f)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().testTag("searchCategories")
            .padding(horizontal = VisualDesign.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in SearchCategory.entries) {
            val active = item == selected
            val bringIntoView = remember { BringIntoViewRequester() }
            LaunchedEffect(active) {
                if (active) {
                    withFrameNanos { }
                    bringIntoView.bringIntoView()
                }
            }
            Box(
                Modifier.bringIntoViewRequester(bringIntoView)
                    .clip(RoundedCornerShape(50)).selectable(active, role = Role.Tab, onClick = { onSelect(item) })
                    .testTag("searchCategory${item.name}").heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(item.label),
                    Modifier.clip(RoundedCornerShape(50)).background(if (active) ink else idleFill).padding(horizontal = 15.dp, vertical = 8.dp),
                    color = if (active) base else ink,
                    style = VisualDesign.Body.copy(fontWeight = FontWeight.SemiBold),
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

internal val UniversalSearchResult.primarySearchAction: SearchResultAction?
    get() = when {
        supports(SearchResultAction.NAVIGATE) -> SearchResultAction.NAVIGATE
        exactPlaybackVideoId() != null -> SearchResultAction.PLAY
        else -> null
    }

@Composable
private fun ResultRow(
    result: UniversalSearchResult,
    actions: UniversalSearchActions,
    artistPhotos: Map<String, SelectedArtistPhoto>,
    preferredArtwork: String?,
    preparing: Boolean,
    featured: Boolean,
    onDetails: (String?) -> Unit,
) {
    val action = result.primarySearchAction
    var preferredLoaded by remember(preferredArtwork) { mutableStateOf(false) }
    val source = stringResource(result.identity.source.nameRes)
    val unavailable = stringResource(R.string.universal_search_catalog_only)
    val loading = stringResource(R.string.universal_search_play_loading)
    val detailsLabel = stringResource(R.string.universal_search_result_options)
    val haptics = LocalHapticFeedback.current
    val openDetails by rememberUpdatedState(onDetails)
    fun showDetails() = openDetails(preferredArtwork.takeIf { preferredLoaded })
    val longPress by rememberUpdatedState({
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        showDetails()
    })

    val actionModifier = if (action != null) {
        Modifier.combinedClickable(
            enabled = !preparing,
            role = Role.Button,
            onClickLabel = stringResource(action.searchLabel),
            onClick = { actions.onResultAction(result, action, preferredArtwork.takeIf { preferredLoaded }) },
            onLongClickLabel = detailsLabel,
            onLongClick = ::showDetails,
        )
    } else {
        Modifier.focusable().pointerInput(result.stableKey) {
            detectTapGestures(onLongPress = { longPress() })
        }.semantics {
            onLongClick(detailsLabel) {
                showDetails()
                true
            }
        }
    }
    val surface = if (featured) {
        Modifier.padding(horizontal = VisualDesign.Gutter)
            .clip(RoundedCornerShape(16.dp)).background(searchSurface(.095f))
    } else Modifier
    val artworkSize = if (featured) 76.dp else 52.dp
    val wide = result.type == SearchResultType.VIDEO && result.identity.source == CatalogSource.YOUTUBE
    val duration = result.durationMs?.takeIf { it >= 1_000 }?.let { formatSearchDuration(it) }

    Row(
        Modifier.fillMaxWidth().then(surface).testTag(result.stableKey)
            .semantics(mergeDescendants = action == null) {
                stateDescription = when {
                    preparing -> loading
                    action == null -> "$source · $unavailable"
                    else -> source
                }
                if (!preparing) {
                    customActions = listOf(CustomAccessibilityAction(detailsLabel) {
                        showDetails()
                        true
                    })
                }
            }
            .onKeyEvent { event ->
                val open = !preparing && event.type == KeyEventType.KeyUp &&
                    event.key == Key.F10 && event.isShiftPressed
                if (open) showDetails()
                open
            }
            .then(actionModifier).heightIn(min = if (featured) 100.dp else 66.dp)
            .padding(horizontal = if (featured) 12.dp else VisualDesign.Gutter, vertical = 7.dp),
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
            width = if (wide) artworkSize + 14.dp else artworkSize,
            height = if (wide) artworkSize * .75f else artworkSize,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (featured) SearchRowDetail(result.featuredLabel(source), maxLines = 1)
            Text(
                result.title,
                style = if (featured) VisualDesign.Heading.copy(fontSize = 19.sp, lineHeight = 23.sp)
                    else VisualDesign.CardTitle.copy(fontSize = 14.sp, lineHeight = 19.sp),
                maxLines = if (featured) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = result.musicMetadata.joinToString(" · ")
            if (detail.isNotEmpty()) SearchRowDetail(detail, maxLines = 1)
        }
        if (preparing) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else if (duration != null) {
            SearchRowDetail(duration, maxLines = 1)
        } else if (action == SearchResultAction.NAVIGATE) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = VisualDesign.Muted)
        }
    }
}

@Composable
private fun UniversalSearchResult.featuredLabel(source: String): String =
    if (type == SearchResultType.ARTIST || type == SearchResultType.ALBUM) {
        stringResource(type.labelRes)
    } else source

internal val UniversalSearchResult.musicMetadata: List<String>
    get() = listOfNotNull(
        subtitle,
        versionLabel?.takeUnless { title.contains(it, ignoreCase = true) },
        release?.title?.takeIf { category == SearchCategory.SONGS },
        release?.year?.takeIf { type == SearchResultType.ALBUM },
    ).distinct()

internal val SearchResultAction.searchLabel: Int
    get() = if (this == SearchResultAction.NAVIGATE) R.string.universal_search_open_details
        else R.string.universal_search_play

@Composable
private fun RecentSearchRow(
    item: RecentSearchItem,
    actions: UniversalSearchActions,
    artistPhotos: Map<String, SelectedArtistPhoto>,
) {
    val canOpen = item.kind == RecentSearchKind.QUERY || item.destination() != null
    val source = item.resultIdentity?.let { stringResource(it.source.nameRes) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(start = VisualDesign.Gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val unavailable = stringResource(R.string.universal_search_catalog_only)
        val interaction = if (canOpen) Modifier.clickable(role = Role.Button) { actions.onRecentSelected(item) }
            else Modifier
        val description = if (canOpen) source else listOfNotNull(source, unavailable).joinToString(" · ")
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).then(interaction)
                .semantics(mergeDescendants = !canOpen) { description?.let { stateDescription = it } }
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(
                displayedSearchArtwork(item.resultIdentity, item.resultType, item.artworkUrl, artistPhotos),
                item.resultType?.icon ?: Icons.Outlined.History,
                item.resultType == SearchResultType.ARTIST,
                width = 40.dp,
                height = 40.dp,
            )
            Column(Modifier.weight(1f)) {
                SearchRowTitle(item.label)
                item.subtitle?.let { SearchRowDetail(it, maxLines = 1) }
            }
        }
        IconButton(onClick = { actions.onRecentRemoved(item) }) {
            Icon(
                Icons.Filled.Close, stringResource(R.string.universal_search_remove_recent, item.label),
                Modifier.size(15.dp), tint = VisualDesign.Muted,
            )
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
internal fun SearchArtwork(
    url: String?,
    fallback: ImageVector,
    circle: Boolean,
    fallbackUrl: String? = null,
    onLoaded: () -> Unit = {},
    width: Dp = 52.dp,
    height: Dp = width,
) {
    Box(
        Modifier.size(width, height)
            .clip(if (circle) CircleShape else RoundedCornerShape(VisualDesign.Radius.Card))
            .background(searchSurface(.085f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(fallback, null, Modifier.size(20.dp), tint = VisualDesign.Muted)
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
internal fun formatSearchDuration(ms: Long): String {
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

internal val CatalogSource.nameRes: Int get() = when (this) {
    CatalogSource.SPOTIFY -> R.string.universal_search_spotify
    CatalogSource.YOUTUBE -> R.string.universal_search_youtube
    CatalogSource.YOUTUBE_MUSIC -> R.string.universal_search_youtube_music
}

internal val SearchResultType.labelRes: Int get() = when (this) {
    SearchResultType.TRACK -> R.string.nostop_search_kind_song
    SearchResultType.VIDEO -> R.string.universal_search_video
    SearchResultType.ARTIST -> R.string.nostop_search_kind_artist
    SearchResultType.ALBUM -> R.string.universal_search_album
}

internal val SearchResultType.icon: ImageVector get() = when (this) {
    SearchResultType.TRACK -> Icons.Outlined.MusicNote
    SearchResultType.VIDEO -> Icons.Outlined.VideoLibrary
    SearchResultType.ARTIST -> Icons.Outlined.Person
    SearchResultType.ALBUM -> Icons.Outlined.Album
}

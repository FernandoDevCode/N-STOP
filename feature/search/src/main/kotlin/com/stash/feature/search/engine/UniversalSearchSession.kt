package com.stash.feature.search.engine

import android.util.Log
import androidx.annotation.MainThread
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.spotify.SpotifyApiException
import com.stash.data.spotify.SpotifyCatalogItem
import com.stash.data.spotify.SpotifyCatalogPage
import com.stash.data.spotify.SpotifyRateLimitException
import com.stash.data.ytmusic.SearchUnavailableException
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.YouTubeWebHttpException
import com.stash.data.ytmusic.YouTubeWebSearchClient
import com.stash.feature.search.SearchSourcePreferences
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.DefaultSearchSources
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.RecentSearchKind
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchActions
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.contract.UnavailableReason
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient

class UniversalSearchSessionFactory @Inject internal constructor(
    private val music: YTMusicApiClient,
    spotify: SpotifyApiClient,
    http: OkHttpClient,
    private val artwork: SearchArtworkLookup,
    private val preferences: SearchSourcePreferences,
    private val history: UniversalSearchHistory,
) {
    private val youtube = YouTubeWebSearchClient(http)
    private val spotifySearch: SpotifySearch = { query, kind, offset ->
        spotify.searchCatalogPage(query, kind = kind, offset = offset)
    }
    private val providers: Map<CatalogSource, CatalogSearch> = mapOf(
        CatalogSource.YOUTUBE to { query ->
            youtube.search(query).videos.map { it.toUniversalSearchResult() }
        },
        CatalogSource.YOUTUBE_MUSIC to { query ->
            rankMusicSearch(query, music.searchCatalog(query).map { it.toUniversalSearchResult() })
        },
    )

    internal suspend fun resolveSpotifyTarget(result: UniversalSearchResult) = withTimeoutOrNull(8_000) {
        val query = listOfNotNull(result.title, result.artistNames.firstOrNull() ?: result.subtitle)
            .joinToString(" ")
        spotifySearchTarget(result, music.searchCatalog(query))
    }

    /** The scope belongs to one Search back-stack entry and runs on the main dispatcher. */
    @MainThread
    suspend fun open(
        scope: CoroutineScope,
        onNavigate: (UniversalSearchResult) -> Unit,
        onOpenRecent: (RecentSearchItem) -> Unit,
        onPlay: (UniversalSearchResult, String?) -> Unit,
    ): UniversalSearchSession = UniversalSearchSession(
        scope, providers, preferences.read(), preferences::save, history,
        onNavigate, onOpenRecent, artwork = artwork::find, play = onPlay, spotifySearch = spotifySearch,
    )
}

internal typealias CatalogSearch = suspend (String) -> List<UniversalSearchResult>
internal typealias SpotifySearch = suspend (String, SpotifyCatalogItem.Kind?, Int) -> SpotifyCatalogPage

/** Keep this instance as long as the Search back-stack entry exists. */
@MainThread
class UniversalSearchSession internal constructor(
    private val scope: CoroutineScope,
    private val providers: Map<CatalogSource, CatalogSearch>,
    initialSelection: SearchSourceSelection,
    private val saveSelection: (SearchSourceSelection) -> Unit,
    private val history: UniversalSearchHistory,
    private val navigate: (UniversalSearchResult) -> Unit,
    private val openRecent: (RecentSearchItem) -> Unit,
    private val debounceMs: Long = 300,
    private val timeoutMs: Long = 15_000,
    private val artwork: suspend (UniversalSearchResult) -> String? = { null },
    private val play: (UniversalSearchResult, String?) -> Unit = { _, _ -> },
    private val spotifySearch: SpotifySearch = { _, _, _ -> throw SpotifyApiException(0, "", "No session") },
) : UniversalSearchActions {
    private val mutableState = MutableStateFlow(
        UniversalSearchEngine.aggregate("", initialSelection.normalizedVisibleFilters(), emptyMap()),
    )
    val state = mutableState.asStateFlow()

    private var pendingQuery: Job? = null
    private val jobs = mutableMapOf<CatalogSource, Job>()
    private val pageJobs = mutableMapOf<SearchResultType, Job>()
    private val musicTypes = SpotifyCatalogItem.Kind.entries.mapTo(mutableSetOf()) { it.resultType }
    private var fallbackTypes = emptySet<SearchResultType>()
    private var fallbackResults: List<UniversalSearchResult>? = null
    private var artworkJob: Job? = null
    private var visibleResultKeys = emptySet<String>()
    private val attemptedArtwork = mutableSetOf<String>()

    init {
        scope.launch {
            history.recent.catch { error ->
                if (error !is IOException) throw error
                Log.w("UniversalSearch", "Could not read recent searches", error)
            }.collect { recent ->
                mutableState.value = state.value.copy(recentSearches = recent)
            }
        }
    }

    override fun onQueryChanged(query: String) {
        val current = state.value
        if (query == current.query) return
        pendingQuery?.cancel()
        pendingQuery = null
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        resetMusicWork()
        resetArtworkWork()
        publish(query, current.selection, emptyMap())
        if (eligible(query)) {
            pendingQuery = scope.launch {
                delay(debounceMs)
                pendingQuery = null
                startQuery()
            }
        }
    }

    override fun onSearchSubmitted() {
        val query = state.value.query
        if (!eligible(query)) return
        writeHistory { record(queryHistoryItem(query)) }
        if (pendingQuery != null) {
            pendingQuery?.cancel()
            pendingQuery = null
            startQuery()
        }
    }

    private fun startQuery() {
        start(CatalogSource.YOUTUBE)
        if (CatalogSource.SPOTIFY in state.value.selection.activeCatalogs) start(CatalogSource.SPOTIFY)
    }

    override fun onClearQuery() = onQueryChanged("")

    override fun onSourceToggled(source: CatalogSource) {
        if (source != CatalogSource.SPOTIFY) return
        val selection = state.value.selection
        val enabled = selection.enabled
        select(selection.copy(enabled = if (source in enabled) enabled - source else enabled + source))
    }

    override fun onPinToggled() = select(state.value.selection.let { it.copy(isPinned = !it.isPinned) })

    override fun onRestoreDefaultSources() = select(state.value.selection.copy(enabled = DefaultSearchSources))

    private fun select(selection: SearchSourceSelection) {
        val current = state.value
        if (selection == current.selection) return
        if (selection.isPinned || current.selection.isPinned) saveSelection(selection)
        val removed = current.selection.activeCatalogs - selection.activeCatalogs
        removed.forEach { jobs.remove(it)?.cancel() }
        if (CatalogSource.SPOTIFY in removed) {
            resetMusicWork()
            resetArtworkWork()
        }
        publish(current.query, selection, current.sourceStates)
        if (pendingQuery == null && eligible(current.query) &&
            CatalogSource.SPOTIFY in selection.activeCatalogs && CatalogSource.SPOTIFY !in current.selection.activeCatalogs
        ) {
            start(CatalogSource.SPOTIFY)
        }
    }

    override fun onRetrySource(source: CatalogSource) {
        val failure = state.value.sourceStates[source] as? SourceSearchState.Error ?: return
        if (failure.canRetry) start(source, isRetry = true)
    }

    override fun onLoadMore(query: String, type: SearchResultType) {
        if (query != state.value.query || pageJobs[type]?.isActive == true) return
        val current = state.value.sourceStates[CatalogSource.SPOTIFY] as? SourceSearchState.Data ?: return
        val offset = current.nextOffsets[type] ?: return
        val kind = SpotifyCatalogItem.Kind.entries.first { it.resultType == type }
        update(CatalogSource.SPOTIFY, current.copy(
            loadingPages = current.loadingPages + type,
            pageErrors = current.pageErrors - type,
        ))
        pageJobs[type] = scope.launch {
            val page = try {
                withTimeoutOrNull(timeoutMs) { spotifySearch(query.trim(), kind, offset) }
                    ?.takeIf { kind in it.returnedKinds }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            ensureActive()
            val latest = state.value.sourceStates[CatalogSource.SPOTIFY] as? SourceSearchState.Data ?: return@launch
            val items = page?.items.orEmpty().filter { it.kind == kind }.map { it.toUniversalSearchResult() }
            val remaining = if (page == null) latest.nextOffsets else
                (latest.nextOffsets - type) + listOfNotNull(page.nextOffsets[kind]?.let { type to it })
            val combined = rankMusicSearch(query, (latest.items + items).distinctBy(UniversalSearchResult::stableKey))
            update(CatalogSource.SPOTIFY, if (combined.isEmpty() && remaining.isEmpty()) {
                SourceSearchState.Empty(query)
            } else {
                latest.copy(
                    items = combined,
                    nextOffsets = remaining,
                    loadingPages = latest.loadingPages - type,
                    pageErrors = if (page == null) latest.pageErrors + type else latest.pageErrors,
                )
            })
        }
    }

    override fun onResultAction(
        result: UniversalSearchResult,
        action: SearchResultAction,
        displayedArtworkUrl: String?,
    ) {
        val current = state.value
        if (!result.supports(action) || result !in current.orderedResults) return
        val artworkUrl = displayedArtworkUrl?.takeIf {
            current.artworkOverrides[result.stableKey] == it
        } ?: result.artworkUrl
        if (action == SearchResultAction.PLAY && result.canPlayFromSearch()) {
            play(result, artworkUrl)
        } else if (action == SearchResultAction.NAVIGATE) {
            val routeArtwork = if (result.type == SearchResultType.ALBUM) artworkUrl else result.artworkUrl
            val target = result.copy(artworkUrl = routeArtwork)
            if (result.identity.source != CatalogSource.SPOTIFY) recordNavigation(target)
            navigate(target)
        }
    }

    internal fun recordNavigation(result: UniversalSearchResult, browseIdentity: CatalogIdentity? = null) {
        val item = resultHistoryItem(
            result.title, result.subtitle, result.identity, result.type, result.artworkUrl, browseIdentity,
        )
        writeHistory { record(item) }
    }

    override fun onRecentSelected(item: RecentSearchItem) {
        if (item !in state.value.recentSearches) return
        writeHistory { record(item) }
        when (item.kind) {
            RecentSearchKind.QUERY -> onQueryChanged(item.label)
            RecentSearchKind.RESULT -> openRecent(item)
        }
    }

    override fun onRecentRemoved(item: RecentSearchItem) = writeHistory { remove(item) }
    override fun onRecentCleared() = writeHistory { clear() }

    private fun writeHistory(write: suspend UniversalSearchHistory.() -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // A recorded action must survive leaving this Search entry.
            try {
                withContext(NonCancellable) { history.write() }
            } catch (error: IOException) {
                Log.w("UniversalSearch", "Could not save recent searches", error)
            }
        }
    }

    private fun start(source: CatalogSource, isRetry: Boolean = false) {
        val query = state.value.query
        jobs[source]?.cancel()
        update(source, SourceSearchState.Loading(query, isRetry = isRetry))
        jobs[source] = scope.launch {
            if (source == CatalogSource.SPOTIFY) {
                searchSpotify(query)
                return@launch
            }
            val status = try {
                val search = providers.getValue(source)
                val items = withTimeoutOrNull(timeoutMs) {
                    search(query.trim()).distinctBy(UniversalSearchResult::stableKey)
                }
                when {
                    items == null -> SourceSearchState.Error(query, "Search timed out", canRetry = true)
                    items.any { it.identity.source != source } ->
                        SourceSearchState.Error(query, "Invalid catalog response", canRetry = false)
                    source == CatalogSource.YOUTUBE_MUSIC -> {
                        ensureActive()
                        fallbackResults = items
                        fallbackStatus(query, items)
                    }
                    items.isEmpty() -> SourceSearchState.Empty(query)
                    else -> SourceSearchState.Data(query, items)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure(query, error)
            }
            ensureActive()
            update(source, status)
        }
    }

    private suspend fun searchSpotify(query: String) {
        val page = try {
            withTimeoutOrNull(timeoutMs) { spotifySearch(query.trim(), null, 0) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            useMusicFallback(query, musicTypes, failure(query, error))
            return
        }
        currentCoroutineContext().ensureActive()
        if (page == null) {
            useMusicFallback(query, musicTypes, SourceSearchState.Error(query, "Search timed out", canRetry = true))
            return
        }
        val items = rankMusicSearch(query, page.items.map { it.toUniversalSearchResult() }
            .distinctBy(UniversalSearchResult::stableKey))
        val offsets = page.nextOffsets.mapKeys { it.key.resultType }
        val status = if (items.isEmpty() && offsets.isEmpty()) SourceSearchState.Empty(query)
            else SourceSearchState.Data(query, items, nextOffsets = offsets)
        // Publish the primary results and the reconciled fallback together when both already exist.
        useMusicFallback(query, musicTypes - page.returnedKinds.map { it.resultType }, status)
    }

    private fun useMusicFallback(
        query: String,
        types: Set<SearchResultType>,
        spotifyStatus: SourceSearchState,
    ) {
        val current = state.value
        fallbackTypes = types
        val musicStatus = when {
            types.isEmpty() -> {
                jobs.remove(CatalogSource.YOUTUBE_MUSIC)?.cancel()
                SourceSearchState.Idle
            }
            else -> fallbackResults?.let { fallbackStatus(query, it) }
                ?: current.sourceStates.getValue(CatalogSource.YOUTUBE_MUSIC)
        }
        publish(query, current.selection, current.sourceStates +
            (CatalogSource.YOUTUBE_MUSIC to musicStatus) + (CatalogSource.SPOTIFY to spotifyStatus))
        if (types.isNotEmpty() && musicStatus == SourceSearchState.Idle) start(CatalogSource.YOUTUBE_MUSIC)
    }

    private fun fallbackStatus(query: String, results: List<UniversalSearchResult>): SourceSearchState {
        val visible = results.filter { it.type in fallbackTypes }
        return if (visible.isEmpty()) SourceSearchState.Empty(query) else SourceSearchState.Data(query, visible)
    }

    private fun resetMusicWork() {
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        fallbackTypes = emptySet()
        fallbackResults = null
    }

    private fun resetArtworkWork() {
        artworkJob?.cancel()
        artworkJob = null
        visibleResultKeys = emptySet()
        attemptedArtwork.clear()
    }

    override fun onVisibleResultsChanged(query: String, stableKeys: Set<String>) {
        if (query != state.value.query || stableKeys == visibleResultKeys) return
        visibleResultKeys = stableKeys
        if (stableKeys.isEmpty() || artworkJob?.isActive == true) return

        artworkJob = scope.launch {
            // Keep one request in flight and take the next candidate from the latest viewport.
            while (true) {
                val result = state.value.orderedResults.firstOrNull {
                    it.stableKey in visibleResultKeys && it.stableKey !in attemptedArtwork && it.artworkKind() != null
                } ?: break
                val url = artwork(result)
                ensureActive()
                attemptedArtwork += result.stableKey
                val current = state.value
                if (url != null && result in current.orderedResults) {
                    mutableState.value = current.copy(artworkOverrides = current.artworkOverrides + (result.stableKey to url))
                }
            }
        }
    }

    private fun failure(query: String, error: Exception): SourceSearchState {
        if (error is SpotifyApiException && error.httpCode in setOf(0, 401)) {
            return SourceSearchState.Unavailable(query, UnavailableReason.AUTHENTICATION_REQUIRED)
        }
        val status = when (error) {
            is SpotifyApiException -> error.httpCode
            is SpotifyRateLimitException -> 429
            is YouTubeWebHttpException -> error.statusCode
            is SearchUnavailableException -> error.statusCode
            else -> null
        }
        val canRetry = if (status != null) status < 0 || status in 500..599 || status in setOf(408, 429)
            else error is IOException
        return SourceSearchState.Error(query, "Catalog search failed", canRetry)
    }

    private fun update(source: CatalogSource, status: SourceSearchState) {
        val current = state.value
        publish(current.query, current.selection, current.sourceStates + (source to status))
    }

    private fun publish(
        query: String,
        selection: SearchSourceSelection,
        sourceStates: Map<CatalogSource, SourceSearchState>,
    ) {
        val current = state.value
        val aggregated = UniversalSearchEngine.aggregate(query, selection, sourceStates, current.recentSearches)
        mutableState.value = if (query == current.query && current.artworkOverrides.isNotEmpty()) {
            val visibleKeys = aggregated.orderedResults.mapTo(hashSetOf()) { it.stableKey }
            aggregated.copy(artworkOverrides = current.artworkOverrides.filterKeys { it in visibleKeys })
        } else aggregated
    }

    private fun eligible(query: String): Boolean = query.isNotBlank()
}

package com.stash.feature.search.engine

import android.util.Log
import androidx.annotation.MainThread
import com.stash.data.ytmusic.SearchUnavailableException
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.YouTubeWebHttpException
import com.stash.data.ytmusic.YouTubeWebSearchClient
import com.stash.feature.search.SearchSourcePreferences
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
import okhttp3.OkHttpClient

class UniversalSearchSessionFactory @Inject internal constructor(
    music: YTMusicApiClient,
    http: OkHttpClient,
    private val artwork: SearchArtworkLookup,
    private val preferences: SearchSourcePreferences,
    private val history: UniversalSearchHistory,
) {
    private val youtube = YouTubeWebSearchClient(http)
    private val providers: Map<CatalogSource, CatalogSearch> = mapOf(
        CatalogSource.YOUTUBE to { query ->
            youtube.search(query).videos.map { it.toUniversalSearchResult() }
        },
        CatalogSource.YOUTUBE_MUSIC to { query ->
            music.searchCatalog(query).map { it.toUniversalSearchResult() }
        },
    )

    /** The scope belongs to one Search back-stack entry and runs on the main dispatcher. */
    @MainThread
    suspend fun open(
        scope: CoroutineScope,
        onNavigate: (UniversalSearchResult) -> Unit,
        onOpenRecent: (RecentSearchItem) -> Unit,
    ): UniversalSearchSession = UniversalSearchSession(
        scope, providers, preferences.read(), preferences::save, history,
        onNavigate, onOpenRecent, artwork = artwork::find,
    )
}

internal typealias CatalogSearch = suspend (String) -> List<UniversalSearchResult>

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
) : UniversalSearchActions {
    private val mutableState = MutableStateFlow(UniversalSearchEngine.aggregate("", initialSelection, emptyMap()))
    val state = mutableState.asStateFlow()

    private var pendingQuery: Job? = null
    private val jobs = mutableMapOf<CatalogSource, Job>()
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
        resetArtworkWork()
        publish(query, current.selection, emptyMap())
        if (eligible(query) && current.selection.activeCatalogs.isNotEmpty()) {
            pendingQuery = scope.launch {
                delay(debounceMs)
                pendingQuery = null
                state.value.selection.activeCatalogs.forEach { start(it) }
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
            state.value.selection.activeCatalogs.forEach { start(it) }
        }
    }

    override fun onClearQuery() = onQueryChanged("")

    override fun onSourceToggled(source: CatalogSource) {
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
        if (CatalogSource.YOUTUBE_MUSIC in removed) resetArtworkWork()
        publish(current.query, selection, current.sourceStates)
        if (pendingQuery == null && eligible(current.query)) {
            (selection.activeCatalogs - current.selection.activeCatalogs).forEach { start(it) }
        }
    }

    override fun onRetrySource(source: CatalogSource) {
        val failure = state.value.sourceStates[source] as? SourceSearchState.Error ?: return
        if (failure.canRetry) start(source, isRetry = true)
    }

    override fun onResultAction(
        result: UniversalSearchResult,
        action: SearchResultAction,
        displayedArtworkUrl: String?,
    ) {
        val current = state.value
        if (!result.supports(action) || result !in current.orderedResults) return
        if (action == SearchResultAction.NAVIGATE) {
            val artworkUrl = displayedArtworkUrl?.takeIf {
                result.type == SearchResultType.ALBUM && current.artworkOverrides[result.stableKey] == it
            } ?: result.artworkUrl
            val item = resultHistoryItem(
                result.title, result.subtitle, result.identity, result.type, artworkUrl,
            )
            writeHistory { record(item) }
            navigate(result.copy(artworkUrl = artworkUrl))
        }
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
            val status = try {
                val search = providers.getValue(source)
                val items = withTimeoutOrNull(timeoutMs) {
                    search(query.trim()).distinctBy(UniversalSearchResult::stableKey)
                }
                when {
                    items == null -> SourceSearchState.Error(query, "Search timed out", canRetry = true)
                    items.isEmpty() -> SourceSearchState.Empty(query)
                    items.any { it.identity.source != source } ->
                        SourceSearchState.Error(query, "Invalid catalog response", canRetry = false)
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
        val status = when (error) {
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

    private fun eligible(query: String): Boolean = query.trim().length >= 2
}

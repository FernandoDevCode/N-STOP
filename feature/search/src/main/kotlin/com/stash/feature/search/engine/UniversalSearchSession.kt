package com.stash.feature.search.engine

import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import com.stash.core.auth.TokenManager
import com.stash.core.auth.model.AuthState
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.spotify.SpotifyApiException
import com.stash.data.spotify.SpotifyRateLimitException
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
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UnavailableReason
import com.stash.feature.search.contract.UniversalSearchActions
import com.stash.feature.search.contract.UniversalSearchResult
import java.io.IOException
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

class UniversalSearchSessionFactory @Inject internal constructor(
    spotify: SpotifyApiClient,
    music: YTMusicApiClient,
    http: OkHttpClient,
    private val tokens: TokenManager,
    private val preferences: SearchSourcePreferences,
    private val history: UniversalSearchHistory,
) {
    private val youtube = YouTubeWebSearchClient(http)
    private val providers: Map<CatalogSource, CatalogSearch> = mapOf(
        CatalogSource.SPOTIFY to { query ->
            spotify.searchCatalog(query).map { it.toUniversalSearchResult() }
        },
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
        onConnectSpotify: () -> Unit,
        onNavigate: (UniversalSearchResult) -> Unit,
        onOpenRecent: (RecentSearchItem) -> Unit,
    ): UniversalSearchSession {
        val session = UniversalSearchSession(
            scope, providers, preferences.read(), preferences::save, history,
            onConnectSpotify, onNavigate, onOpenRecent,
        )
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            tokens.spotifyAuthState.map { it is AuthState.Connected }.distinctUntilChanged().drop(1)
                .collect { connected -> if (connected) session.onSpotifyConnected() }
        }
        return session
    }
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
    private val connectSpotify: () -> Unit,
    private val navigate: (UniversalSearchResult) -> Unit,
    private val openRecent: (RecentSearchItem) -> Unit,
    private val debounceMs: Long = 300,
    private val timeoutMs: Long = 15_000,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) : UniversalSearchActions {
    private val mutableState = MutableStateFlow(UniversalSearchEngine.aggregate("", initialSelection, emptyMap()))
    val state = mutableState.asStateFlow()

    private var pendingQuery: Job? = null
    private val jobs = mutableMapOf<CatalogSource, Job>()
    private var spotifyRetryAt = 0L

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
        publish(query, current.selection, emptyMap())
        if (eligible(query) && current.selection.enabled.isNotEmpty()) {
            pendingQuery = scope.launch {
                delay(debounceMs)
                pendingQuery = null
                state.value.selection.enabled.forEach { start(it) }
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
            state.value.selection.enabled.forEach { start(it) }
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
        (current.selection.enabled - selection.enabled).forEach { jobs.remove(it)?.cancel() }
        publish(current.query, selection, current.sourceStates)
        if (pendingQuery == null && eligible(current.query)) {
            (selection.enabled - current.selection.enabled).forEach { start(it) }
        }
    }

    override fun onRetrySource(source: CatalogSource) {
        val failure = state.value.sourceStates[source] as? SourceSearchState.Error ?: return
        if (failure.canRetry) start(source, isRetry = true)
    }

    override fun onConnectSource(source: CatalogSource) {
        val status = state.value.sourceStates[source] as? SourceSearchState.Unavailable
        if (source == CatalogSource.SPOTIFY && status?.reason == UnavailableReason.AUTHENTICATION_REQUIRED) {
            connectSpotify()
        }
    }

    internal fun onSpotifyConnected() {
        val status = state.value.sourceStates[CatalogSource.SPOTIFY] as? SourceSearchState.Unavailable
        if (status?.reason == UnavailableReason.AUTHENTICATION_REQUIRED) start(CatalogSource.SPOTIFY)
    }

    override fun onResultAction(result: UniversalSearchResult, action: SearchResultAction) {
        if (!result.supports(action) || result !in state.value.orderedResults) return
        if (action == SearchResultAction.NAVIGATE) {
            val item = resultHistoryItem(result.title, result.subtitle, result.identity)
            writeHistory { record(item) }
            navigate(result)
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
                val retryWait = if (source == CatalogSource.SPOTIFY) spotifyRetryAt - nowMs() else 0
                if (retryWait > 0) {
                    update(source, rateLimitState(query, canRetry = false))
                    delay(retryWait)
                    update(source, rateLimitState(query, canRetry = true))
                    return@launch
                }
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
            } catch (error: SpotifyRateLimitException) {
                ensureActive()
                val now = nowMs()
                val wait = TimeUnit.SECONDS.toMillis(error.retryAfterSeconds?.coerceAtLeast(0) ?: 0)
                    .coerceAtMost(Long.MAX_VALUE - now)
                spotifyRetryAt = now + wait
                if (wait > 0) {
                    update(source, rateLimitState(query, canRetry = false))
                    delay(wait)
                }
                rateLimitState(query, canRetry = true)
            } catch (error: Exception) {
                failure(query, error)
            }
            ensureActive()
            update(source, status)
        }
    }

    private fun rateLimitState(query: String, canRetry: Boolean) =
        SourceSearchState.Error(query, "Spotify search rate limit reached", canRetry)

    private fun failure(query: String, error: Exception): SourceSearchState {
        if (error is SpotifyApiException && error.httpCode in setOf(0, 401)) {
            return SourceSearchState.Unavailable(query, UnavailableReason.AUTHENTICATION_REQUIRED)
        }
        val status = when (error) {
            is SpotifyApiException -> error.httpCode
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
        mutableState.value = UniversalSearchEngine.aggregate(query, selection, sourceStates, state.value.recentSearches)
    }

    private fun eligible(query: String): Boolean = query.trim().length >= 2
}

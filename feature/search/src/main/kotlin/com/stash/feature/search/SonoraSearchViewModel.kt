package com.stash.feature.search

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.artistphotos.SelectedArtistPhotoRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.StreamRoutingResult
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.model.TrackItem
import com.stash.data.ytmusic.YouTubeWebSearchClient
import com.stash.data.ytmusic.YouTubeWebVideo
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.contract.UniversalSearchUiState
import com.stash.feature.search.engine.LiveSearchSources
import com.stash.feature.search.engine.UniversalSearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpotifyBrowsePage(
    val entry: UniversalSearchResult,
    val items: List<UniversalSearchResult> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** Spotify or YouTube WEB, one catalog at a time; never search YTM. */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SonoraSearchViewModel @Inject constructor(
    private val sources: LiveSearchSources,
    private val youtubeWeb: YouTubeWebSearchClient,
    private val preferences: SearchSourcePreferences,
    private val recentStore: RecentSearchesStore,
    private val player: PlayerRepository,
    val delegate: TrackActionsDelegate,
    val selectedArtistPhotos: SelectedArtistPhotoRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    companion object {
        private const val QUERY_KEY = "sonora_source_query"
        private const val LIMIT_PAGES = 25
        private const val MATCH_PREFETCH_COUNT = 2
        private const val MATCH_CACHE_MAX_ITEMS = 32
        private const val PLAY_LOG_TAG = "SonoraSearchPlayback"
    }

    private val typedQuery = MutableStateFlow(savedState.get<String>(QUERY_KEY).orEmpty())
    val query = typedQuery.asStateFlow()
    private val chosenSource = MutableStateFlow(CatalogSource.SPOTIFY)
    val source = chosenSource.asStateFlow()
    private val _pinned = MutableStateFlow(false)
    val pinned = _pinned.asStateFlow()
    private val ready = MutableStateFlow(false)
    private val states = mutableMapOf<CatalogSource, SourceSearchState>()
    private val next = mutableMapOf<CatalogSource, String?>()
    private val ranks = mutableMapOf<CatalogSource, Int>()
    private val pages = mutableMapOf<CatalogSource, Int>()
    private val _results = MutableStateFlow(UniversalSearchUiState())
    val results = _results.asStateFlow()
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()
    private val _moreError = MutableStateFlow<String?>(null)
    val moreError = _moreError.asStateFlow()
    private val _browse = MutableStateFlow<List<SpotifyBrowsePage>>(emptyList())
    val browse = _browse.asStateFlow()
    // A Spotify search result is catalog metadata; matching its exact audio
    // on YouTube is network I/O. Share one in-flight lookup between speculative
    // preparation and the user's tap, keyed to the immutable Spotify identity +
    // metadata. Never use this cache for conventional YouTube video selections.
    // All map access is on the ViewModel's Main dispatcher.
    private val spotifyMatchTasks =
        object : LinkedHashMap<String, Deferred<YouTubeWebVideo?>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Deferred<YouTubeWebVideo?>>,
            ): Boolean = size > MATCH_CACHE_MAX_ITEMS
        }

    private fun matchKey(item: UniversalSearchResult): String =
        listOf(item.stableKey, item.title, item.subtitle.orEmpty(),
            item.durationMs?.toString().orEmpty()).joinToString("|")

    private fun matchTask(item: UniversalSearchResult): Deferred<YouTubeWebVideo?> {
        val key = matchKey(item)
        spotifyMatchTasks[key]?.let { return it }
        val artist = item.subtitle?.substringBefore(',')?.trim().orEmpty()
        val job = viewModelScope.async(Dispatchers.IO) {
            findConservativeSpotifyAudio(item, artist)
        }
        spotifyMatchTasks[key] = job
        // Don't cache a transient miss or HTTP error forever. Successful
        // matches stay in this bounded ViewModel-lifetime lookup cache.
        viewModelScope.launch {
            try {
                if (job.await() == null && spotifyMatchTasks[key] === job) {
                    spotifyMatchTasks.remove(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (spotifyMatchTasks[key] === job) spotifyMatchTasks.remove(key)
            }
        }
        return job
    }

    private fun prepareSpotifyMatches(items: List<UniversalSearchResult>) {
        items.asSequence()
            .filter { it.identity.source == CatalogSource.SPOTIFY &&
                it.type == SearchResultType.TRACK &&
                !it.subtitle.isNullOrBlank() &&
                (it.durationMs ?: 0L) > 0L }
            .take(MATCH_PREFETCH_COUNT)
            .forEach { matchTask(it) }
    }

    private val _resolving = MutableStateFlow<String?>(null)
    val resolving = _resolving.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    val recent = recentStore.recent.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )
    val selectedPhotos = selectedArtistPhotos.photos

    init {
        delegate.bindToScope(viewModelScope)
        viewModelScope.launch {
            val stored = preferences.selection.first()
            _pinned.value = stored.pinned
            chosenSource.value = if (stored.pinned) stored.source else CatalogSource.SPOTIFY
            ready.value = true
            publish()
        }
        viewModelScope.launch {
            combine(
                typedQuery.debounce(300),
                chosenSource,
                ready,
            ) { query, source, isReady -> Triple(query.trim(), source, isReady) }
                .filter { it.third }
                .distinctUntilChanged()
                .collectLatest { (query, source, _) ->
                    if (query.length >= 2) fetchFirst(query, source)
                }
        }
    }

    fun onQueryChanged(value: String) {
        if (typedQuery.value == value) return
        typedQuery.value = value
        savedState[QUERY_KEY] = value
        states.clear()
        next.clear()
        ranks.clear()
        pages.clear()
        _moreError.value = null
        publish()
    }

    fun select(source: CatalogSource) {
        if (source != CatalogSource.SPOTIFY && source != CatalogSource.YOUTUBE) return
        if (chosenSource.value == source) return
        chosenSource.value = source
        _moreError.value = null
        publish()
        if (_pinned.value) viewModelScope.launch { preferences.save(source, true) }
    }

    fun togglePin() {
        val pinnedNow = !_pinned.value
        _pinned.value = pinnedNow
        publish()
        viewModelScope.launch { preferences.save(chosenSource.value, pinnedNow) }
    }

    fun restoreDefault() {
        chosenSource.value = CatalogSource.SPOTIFY
        publish()
        if (_pinned.value) viewModelScope.launch {
            preferences.save(CatalogSource.SPOTIFY, true)
        }
    }

    fun submit() {
        val q = typedQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            recentStore.record(RecentSearch(RecentSearch.Type.QUERY, q))
        }
        if (states[chosenSource.value] is SourceSearchState.Error) retry()
    }

    fun retry() {
        val q = typedQuery.value.trim()
        if (q.length < 2) return
        viewModelScope.launch { fetchFirst(q, chosenSource.value, force = true) }
    }

    private fun publish() {
        _results.value = UniversalSearchEngine.aggregate(
            query = typedQuery.value.trim(),
            selection = SearchSourceSelection(setOf(chosenSource.value), _pinned.value),
            sourceStates = states.toMap(),
        )
    }

    private suspend fun fetchFirst(q: String, src: CatalogSource, force: Boolean = false) {
        val existing = states[src]
        if (!force && existing is SourceSearchState.Data && existing.query == q) return
        states[src] = SourceSearchState.Loading(q)
        next[src] = null
        ranks[src] = 0
        pages[src] = 0
        publish()
        try {
            val page = sources.search(q, src)
            if (typedQuery.value.trim() != q) return
            val items = page.items.distinctBy { it.stableKey }
            states[src] = if (items.isEmpty()) SourceSearchState.Empty(q)
                else SourceSearchState.Data(q, items)
            next[src] = page.continuation
            ranks[src] = page.nextRank
            pages[src] = 1
            publish()
            if (src == CatalogSource.SPOTIFY && typedQuery.value.trim() == q) {
                prepareSpotifyMatches(items)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (typedQuery.value.trim() != q) return
            states[src] = SourceSearchState.Error(q, describe(src, e), canRetry = true)
            publish()
        }
    }

    fun loadMore() {
        val q = typedQuery.value.trim()
        val src = chosenSource.value
        val continuation = next[src] ?: return
        val current = states[src] as? SourceSearchState.Data ?: return
        if (current.query != q || _loadingMore.value || (pages[src] ?: 0) >= LIMIT_PAGES) return
        _loadingMore.value = true
        _moreError.value = null
        viewModelScope.launch {
            try {
                val page = sources.search(q, src, continuation, ranks[src] ?: 0)
                if (typedQuery.value.trim() == q) {
                    val previous = (states[src] as? SourceSearchState.Data)
                        ?.takeIf { it.query == q }?.items.orEmpty()
                    val combined = (previous + page.items).distinctBy { it.stableKey }
                    states[src] = SourceSearchState.Data(q, combined)
                    // A provider that ignores offset or repeats a continuation
                    // must not issue up to 25 identical pages as the list scrolls.
                    next[src] = if (combined.size > previous.size)
                        page.continuation?.takeUnless { it == continuation }
                    else null
                    ranks[src] = page.nextRank
                    pages[src] = (pages[src] ?: 0) + 1
                    publish()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _moreError.value = describe(src, e)
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun hasMore(): Boolean =
        next[chosenSource.value] != null &&
        (pages[chosenSource.value] ?: 0) < LIMIT_PAGES

    /** Spotify detail stack keeps actual Spotify identities throughout artist -> album. */
    fun openSpotifyBrowse(item: UniversalSearchResult) {
        if (item.identity.source != CatalogSource.SPOTIFY ||
            item.type !in setOf(SearchResultType.ARTIST, SearchResultType.ALBUM)) return
        _browse.value = _browse.value + SpotifyBrowsePage(entry = item)
        loadSpotifyBrowse()
    }

    fun closeSpotifyBrowse() {
        if (_browse.value.isNotEmpty()) _browse.value = _browse.value.dropLast(1)
    }

    fun retrySpotifyBrowse() {
        loadSpotifyBrowse()
    }

    fun loadSpotifyBrowse() {
        val top = _browse.value.lastOrNull() ?: return
        // Never request another page while one is in flight, unless the stack
        // frame was just created with its initial loading placeholder.
        if (top.loading && top.items.isNotEmpty()) return
        val entryKey = top.entry.stableKey
        _browse.value = _browse.value.dropLast(1) + top.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val page = sources.browseSpotify(top.entry, top.continuation)
                val now = _browse.value.lastOrNull()
                if (now?.entry?.stableKey != entryKey) return@launch
                val combined = (now.items + page.items).distinctBy { it.stableKey }
                _browse.value = _browse.value.dropLast(1) + now.copy(
                    items = combined,
                    continuation = page.continuation?.takeUnless { it == top.continuation },
                    loading = false,
                )
                prepareSpotifyMatches(page.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val now = _browse.value.lastOrNull()
                if (now?.entry?.stableKey == entryKey) {
                    _browse.value = _browse.value.dropLast(1) + now.copy(
                        loading = false, error = describe(CatalogSource.SPOTIFY, e),
                    )
                }
            }
        }
    }

    fun onRecent(entry: RecentSearch) {
        viewModelScope.launch { recentStore.record(entry) }
        onQueryChanged(entry.searchText)
    }

    fun removeRecent(entry: RecentSearch) {
        viewModelScope.launch { recentStore.remove(entry) }
    }

    fun clearRecent() {
        viewModelScope.launch { recentStore.clear() }
    }

    /**
     * Conventional YouTube: play exactly its catalog video ID. Spotify: only
     * play after finding a conservative metadata match; never feed Spotify IDs
     * to the YouTube resolver, and never silently substitute a different edit.
     */
    fun onAction(item: UniversalSearchResult, action: SearchResultAction) {
        if (!item.supports(action)) return
        if (item.type != SearchResultType.TRACK && item.type != SearchResultType.VIDEO) return
        viewModelScope.launch {
            val tapStartMs = SystemClock.elapsedRealtime()
            try {
                val track = when (item.identity.source) {
                    CatalogSource.SPOTIFY -> {
                        if (action != SearchResultAction.PLAY) return@launch
                        _resolving.value = item.stableKey
                        val video = matchTask(item).await()
                        Log.d(PLAY_LOG_TAG,
                            "Spotify match ms=" + (SystemClock.elapsedRealtime() - tapStartMs))
                        if (video == null) {
                            _messages.emit("Não foi possível confirmar um áudio equivalente para esta faixa.")
                            return@launch
                        }
                        val artist = item.subtitle?.substringBefore(',')?.trim().orEmpty()
                        TrackItem(video.videoId, item.title, artist,
                            (item.durationMs ?: video.durationMs ?: 0L) / 1000.0,
                            item.artworkUrl)
                    }
                    CatalogSource.YOUTUBE -> {
                        val audioId = item.audioIdentity?.audioId ?: return@launch
                        TrackItem(audioId, item.title, item.subtitle.orEmpty(),
                            (item.durationMs ?: 0L) / 1000.0, item.artworkUrl)
                    }
                    CatalogSource.YOUTUBE_MUSIC -> return@launch
                }
                if (action == SearchResultAction.PLAY) {
                    _resolving.value = item.stableKey
                    val streamStartMs = SystemClock.elapsedRealtime()
                    val routing = player.playFromStream(track)
                    Log.d(PLAY_LOG_TAG, "source=${item.identity.source} streamResolveMs=" +
                        (SystemClock.elapsedRealtime() - streamStartMs) +
                        " tapToPlayerMs=" + (SystemClock.elapsedRealtime() - tapStartMs))
                    when (routing) {
                        is StreamRoutingResult.Item, StreamRoutingResult.Deduped -> Unit
                        StreamRoutingResult.NotAvailable -> _messages.emit("Áudio indisponível.")
                        StreamRoutingResult.CellularRefused -> _messages.emit("Streaming por dados móveis desativado.")
                        StreamRoutingResult.NoConnectivity -> _messages.emit("Sem conexão para reproduzir.")
                    }
                    recentStore.record(RecentSearch(
                        type = RecentSearch.Type.TRACK,
                        text = item.title, subtitle = item.subtitle, thumbnailUrl = item.artworkUrl,
                    ))
                } else when (action) {
                    SearchResultAction.PLAY_NEXT -> delegate.playNext(track)
                    SearchResultAction.ADD_TO_QUEUE -> delegate.addToQueue(track)
                    SearchResultAction.DOWNLOAD -> delegate.downloadTrack(track)
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.emit("Não foi possível executar esta ação.")
            } finally {
                _resolving.value = null
            }
        }
    }

    private suspend fun findConservativeSpotifyAudio(
        item: UniversalSearchResult, artist: String,
    ): YouTubeWebVideo? {
        if (artist.isBlank() || item.durationMs == null || item.durationMs <= 0) return null
        val videos = youtubeWeb.search("$artist ${item.title} official audio").videos
        return videos.firstOrNull { video ->
            val text = searchable(video.title)
            val title = searchable(item.title)
            val artistName = searchable(artist)
            val credited = text.contains(artistName) || searchable(video.channel.orEmpty()).contains(artistName)
            val sameTitle = text.contains(title) && title.isNotBlank()
            val duration = video.durationMs
            val sameDuration = duration != null && abs(duration - item.durationMs) <= 4000L
            val unwanted = listOf("slowed", "reverb", "nightcore", "cover", "remix", "live")
                .any { word -> word in text && word !in title }
            credited && sameTitle && sameDuration && !unwanted
        }
    }

    private fun searchable(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        .replace(Regex("\\s+"), " ")

    private fun describe(source: CatalogSource, error: Exception): String = when {
        source == CatalogSource.SPOTIFY &&
            error.message?.contains("não conectado", true) == true ->
            "Conecte sua conta Spotify para pesquisar o catálogo."
        error.message?.contains("429") == true || error.javaClass.simpleName.contains("RateLimit") ->
            "Serviço temporariamente limitado. Tente novamente mais tarde."
        else -> "Falha ao consultar ${source.name.replace('_', ' ')}. Tente novamente."
    }

    override fun onCleared() {
        super.onCleared()
        delegate.onOwnerCleared()
    }
}

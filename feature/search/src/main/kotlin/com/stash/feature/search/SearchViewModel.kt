package com.stash.feature.search

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.artistphotos.SelectedArtistPhotoRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.StreamRoutingResult
import com.stash.core.model.TrackItem
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.engine.UniversalSearchSession
import com.stash.feature.search.engine.UniversalSearchSessionFactory
import com.stash.feature.search.engine.exactPlaybackVideoId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    factory: UniversalSearchSessionFactory,
    savedStateHandle: SavedStateHandle,
    artistPhotos: SelectedArtistPhotoRepository,
    private val player: PlayerRepository,
) : ViewModel() {
    internal val selectedArtistPhotos = artistPhotos.photos
    private val openedSession = MutableStateFlow<UniversalSearchSession?>(null)
    val session = openedSession.asStateFlow()

    private val navigation = MutableSharedFlow<SearchNavigation>(extraBufferCapacity = 1)
    internal val destinations = navigation.asSharedFlow()
    private val playbackErrors = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    internal val playErrors = playbackErrors.asSharedFlow()
    private val pendingPlayback = MutableStateFlow<UniversalSearchResult?>(null)
    internal val playingRequest = pendingPlayback.asStateFlow()
    private var playbackJob: Job? = null

    init {
        viewModelScope.launch {
            val session = factory.open(
                scope = viewModelScope,
                onNavigate = { result ->
                    searchDestination(result.identity, result.type, result.title, result.subtitle, result.artworkUrl)
                        ?.let { navigation.tryEmit(it) }
                },
                onOpenRecent = { item -> item.destination()?.let { navigation.tryEmit(it) } },
                onPlay = ::play,
            )
            savedStateHandle.get<String>(KEY_QUERY)?.let(session::onQueryChanged)
            openedSession.value = session
            session.state.map { it.query }.distinctUntilChanged().collect { savedStateHandle[KEY_QUERY] = it }
        }
    }

    private fun play(result: UniversalSearchResult, artworkUrl: String?) {
        val videoId = result.exactPlaybackVideoId() ?: return
        if (pendingPlayback.value?.audioIdentity == result.audioIdentity) return
        playbackJob?.cancel()
        pendingPlayback.value = result
        playbackJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = player.playExactSearchResult(
                    TrackItem(
                        videoId = videoId,
                        title = result.title,
                        artist = result.subtitle.orEmpty(),
                        durationSeconds = (result.durationMs ?: 0L) / 1000.0,
                        thumbnailUrl = artworkUrl,
                        album = result.release?.title,
                    ),
                )
                ensureActive()
                val error = when (outcome) {
                    StreamRoutingResult.NotAvailable -> R.string.universal_search_play_unavailable
                    StreamRoutingResult.NoConnectivity -> R.string.universal_search_play_offline
                    StreamRoutingResult.CellularRefused -> R.string.universal_search_play_cellular
                    StreamRoutingResult.Deduped, is StreamRoutingResult.Item -> null
                }
                error?.let { playbackErrors.tryEmit(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                Log.w("SearchViewModel", "Could not start exact Search playback", error)
                playbackErrors.tryEmit(R.string.universal_search_play_failed)
            } finally {
                if (playbackJob === coroutineContext[Job]) {
                    playbackJob = null
                    pendingPlayback.value = null
                }
            }
        }
        playbackJob?.start()
    }

    internal fun cancelPendingPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        pendingPlayback.value = null
    }

    internal companion object {
        const val KEY_QUERY = "search_query"
    }
}

internal sealed interface SearchNavigation {
    data class Artist(val id: String, val name: String, val artworkUrl: String?) : SearchNavigation
    data class Album(val album: AlbumSummary) : SearchNavigation
}

internal fun RecentSearchItem.destination(): SearchNavigation? =
    resultIdentity?.let { searchDestination(it, resultType, label, subtitle, artworkUrl) }

internal fun searchDestination(
    identity: CatalogIdentity,
    type: SearchResultType?,
    title: String,
    subtitle: String?,
    artworkUrl: String?,
): SearchNavigation? {
    if (identity.source != CatalogSource.YOUTUBE_MUSIC) return null
    return when (type) {
        SearchResultType.ARTIST -> SearchNavigation.Artist(identity.catalogId, title, artworkUrl)
        SearchResultType.ALBUM -> SearchNavigation.Album(
            AlbumSummary(identity.catalogId, title, subtitle.orEmpty(), artworkUrl, year = null),
        )
        else -> null
    }
}

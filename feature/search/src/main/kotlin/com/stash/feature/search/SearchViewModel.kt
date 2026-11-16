package com.stash.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.artistphotos.SelectedArtistPhotoRepository
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.engine.UniversalSearchSession
import com.stash.feature.search.engine.UniversalSearchSessionFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
) : ViewModel() {
    internal val selectedArtistPhotos = artistPhotos.photos
    private val openedSession = MutableStateFlow<UniversalSearchSession?>(null)
    val session = openedSession.asStateFlow()

    private val navigation = MutableSharedFlow<SearchNavigation>(extraBufferCapacity = 1)
    internal val destinations = navigation.asSharedFlow()

    init {
        viewModelScope.launch {
            val session = factory.open(
                scope = viewModelScope,
                onConnectSpotify = { navigation.tryEmit(SearchNavigation.ConnectSpotify) },
                onNavigate = { result ->
                    searchDestination(result.identity, result.type, result.title, result.subtitle, result.artworkUrl)
                        ?.let { navigation.tryEmit(it) }
                },
                onOpenRecent = { item -> item.destination()?.let { navigation.tryEmit(it) } },
            )
            savedStateHandle.get<String>(KEY_QUERY)?.let(session::onQueryChanged)
            openedSession.value = session
            session.state.map { it.query }.distinctUntilChanged().collect { savedStateHandle[KEY_QUERY] = it }
        }
    }

    internal companion object {
        const val KEY_QUERY = "search_query"
    }
}

internal sealed interface SearchNavigation {
    data object ConnectSpotify : SearchNavigation
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
    // The existing playback entry points share a track-ID URL cache and a resolver
    // that can match recordings by metadata. Catalog routes preserve the selected ID.
    if (identity.source != CatalogSource.YOUTUBE_MUSIC) return null
    return when (type) {
        SearchResultType.ARTIST -> SearchNavigation.Artist(identity.catalogId, title, artworkUrl)
        SearchResultType.ALBUM -> SearchNavigation.Album(
            AlbumSummary(identity.catalogId, title, subtitle.orEmpty(), artworkUrl, year = null),
        )
        else -> null
    }
}

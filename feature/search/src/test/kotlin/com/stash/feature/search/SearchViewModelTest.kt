package com.stash.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.CatalogSource.YOUTUBE
import com.stash.feature.search.contract.CatalogSource.YOUTUBE_MUSIC
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.SearchReleaseMetadata
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.engine.UniversalSearchHistory
import com.stash.feature.search.engine.UniversalSearchSession
import com.stash.feature.search.engine.UniversalSearchSessionFactory
import com.stash.feature.search.engine.resultHistoryItem
import com.stash.core.data.artistphotos.SelectedArtistPhotoRepository
import com.stash.core.data.artistphotos.SelectedArtistPhoto
import com.stash.core.media.PlayerRepository
import com.stash.core.media.StreamRoutingResult
import com.stash.core.model.TrackItem
import com.stash.data.ytmusic.YouTubeWebVideo
import com.stash.data.ytmusic.YTMusicCatalogItem
import com.stash.feature.search.engine.toUniversalSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `same back stack store retains session and new entry starts at defaults`() = runTest {
        val handle = SavedStateHandle(mapOf(SearchViewModel.KEY_QUERY to "restored"))
        val entry = Entry()
        val newEntry = Entry()
        var sessionsOpened = 0
        var youtubeStarted = false
        var youtubeCanceled = false
        val factory = factory { scope, navigate, open, _ ->
            sessionsOpened++
            UniversalSearchSession(
                scope,
                CatalogSource.entries.associateWith { source ->
                    {
                        if (source == YOUTUBE) {
                            youtubeStarted = true
                            try {
                                awaitCancellation()
                            } finally {
                                youtubeCanceled = true
                            }
                        }
                        emptyList()
                    }
                },
                SearchSourceSelection.Default, {}, history(), navigate, open,
            )
        }
        val first = entry.viewModel(factory, handle)
        runCurrent()
        val session = checkNotNull(first.session.value)
        assertEquals("restored", session.state.value.query)
        session.onSourceToggled(YOUTUBE_MUSIC)
        session.onQueryChanged("live demo")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(youtubeStarted)
        assertEquals("live demo", handle.get<String>(SearchViewModel.KEY_QUERY))

        val returned = entry.viewModel(factory)
        assertSame(first, returned)
        assertSame(session, returned.session.value)
        assertTrue(YOUTUBE_MUSIC in session.state.value.selection.enabled)
        assertEquals(1, sessionsOpened)

        entry.viewModelStore.clear()
        runCurrent()
        assertTrue(youtubeCanceled)

        val fresh = newEntry.viewModel(factory)
        runCurrent()
        assertNotSame(session, fresh.session.value)
        assertEquals(SearchSourceSelection.Default, fresh.session.value?.state?.value?.selection)
        assertEquals(2, sessionsOpened)
        newEntry.viewModelStore.clear()
    }

    @Test
    fun `album route and history use the loaded cover without changing catalogue identity`() = runTest {
        val entry = Entry()
        val spotifyCover = "https://i.scdn.co/image/verified"
        val album = UniversalSearchResult(
            identity = CatalogIdentity(YOUTUBE_MUSIC, "album-id-special"),
            type = SearchResultType.ALBUM,
            title = "Original (Deluxe)",
            subtitle = "Original artist",
            artworkUrl = "https://example.test/original-image.jpg",
            providerRank = 0,
            rankingScore = 1.0,
            supportedActions = setOf(SearchResultAction.NAVIGATE),
            release = SearchReleaseMetadata("Original (Deluxe)", "2020", "album"),
        )
        val artist = resultHistoryItem(
            "Original artist", null, CatalogIdentity(YOUTUBE_MUSIC, "MPLAUCoriginal"),
            SearchResultType.ARTIST, "https://example.test/artist.jpg",
        )
        val history = history(listOf(artist))
        val factory = factory { scope, navigate, open, _ ->
            UniversalSearchSession(
                scope,
                CatalogSource.entries.associateWith { source ->
                    { if (source == YOUTUBE_MUSIC) listOf(album) else emptyList() }
                },
                SearchSourceSelection(setOf(YOUTUBE_MUSIC)), {}, history, navigate, open,
                artwork = { spotifyCover },
            )
        }
        val model = entry.viewModel(factory)
        val received = mutableListOf<SearchNavigation>()
        backgroundScope.launch { model.destinations.collect { received += it } }
        runCurrent()
        val session = checkNotNull(model.session.value)
        session.onQueryChanged("original")
        advanceTimeBy(300)
        runCurrent()
        session.onResultAction(album, SearchResultAction.PLAY)
        runCurrent()
        assertTrue(received.isEmpty())

        session.onVisibleResultsChanged("original", setOf(album.stableKey))
        runCurrent()
        assertEquals(spotifyCover, session.state.value.artworkOverrides[album.stableKey])
        assertEquals(listOf(album), session.state.value.orderedResults)

        // A validated URL can still be loading or have failed at the time of the tap.
        session.onResultAction(album, SearchResultAction.NAVIGATE)
        runCurrent()
        session.onResultAction(album, SearchResultAction.NAVIGATE, displayedArtworkUrl = spotifyCover)
        runCurrent()
        session.onRecentSelected(artist)
        runCurrent()
        val fallback = (received[0] as SearchNavigation.Album).album
        assertEquals(album.artworkUrl, fallback.thumbnailUrl)
        val opened = (received[1] as SearchNavigation.Album).album
        assertEquals(album.identity.catalogId, opened.id)
        assertEquals(album.title, opened.title)
        assertEquals(album.subtitle, opened.artist)
        assertEquals(spotifyCover, opened.thumbnailUrl)
        verify(history).record(resultHistoryItem(album.title, album.subtitle, album.identity, album.type, album.artworkUrl))
        verify(history).record(resultHistoryItem(album.title, album.subtitle, album.identity, album.type, spotifyCover))
        assertEquals(SearchNavigation.Artist("MPLAUCoriginal", artist.label, artist.artworkUrl), received[2])
        assertNull(searchDestination(CatalogIdentity(YOUTUBE, "same-id"), SearchResultType.ALBUM, "Title", null, null))
        entry.viewModelStore.clear()
    }

    @Test
    fun `symbolic Spotify routes real music results and each supported row plays only its catalog id`() = runTest {
        val entry = Entry()
        val player = mock<PlayerRepository>()
        whenever(player.playExactSearchResult(any())).thenReturn(StreamRoutingResult.Deduped)
        val music = listOf(
            catalogItem(YTMusicCatalogItem.Kind.TRACK, "ABCDEFGHIJK", "Track"),
            catalogItem(YTMusicCatalogItem.Kind.VIDEO, "ABCDEFGHIJL", "Track (Live)"),
            catalogItem(YTMusicCatalogItem.Kind.TRACK, "invalid", "Unavailable"),
        )
        val web = YouTubeWebVideo("ABCDEFGHIJM", "Track (Cover)", "Artist", null, null, 0).toUniversalSearchResult()
        val factory = factory { scope, navigate, open, play ->
            UniversalSearchSession(
                scope, mapOf(YOUTUBE to { listOf(web) }, YOUTUBE_MUSIC to { music }),
                SearchSourceSelection.Default, {}, history(), navigate, open, play = play,
            )
        }
        val model = entry.viewModel(factory, player = player)
        runCurrent()
        val session = checkNotNull(model.session.value)
        session.onQueryChanged("track")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(setOf(YOUTUBE_MUSIC, YOUTUBE), session.state.value.orderedResults.map { it.identity.source }.toSet())
        val tracks = (music + web).filter { it.supports(SearchResultAction.PLAY) }
        assertEquals(listOf("ABCDEFGHIJK", "ABCDEFGHIJL", "ABCDEFGHIJM"), tracks.map { it.audioIdentity?.audioId })
        for (result in music + web) {
            session.onResultAction(result, SearchResultAction.PLAY)
            runCurrent()
        }
        val argument = org.mockito.kotlin.argumentCaptor<com.stash.core.model.TrackItem>()
        verify(player, org.mockito.kotlin.times(3)).playExactSearchResult(argument.capture())
        assertEquals(tracks.map { it.identity.catalogId }, argument.allValues.map { it.videoId })
        assertEquals(tracks.map { it.title }, argument.allValues.map { it.title })
        entry.viewModelStore.clear()
    }

    @Test
    fun `duplicate taps are ignored and cancellation clears the old spinner without error`() = runTest {
        val entry = Entry()
        val first = catalogItem(YTMusicCatalogItem.Kind.TRACK, "ABCDEFGHIJK", "Track")
        val second = catalogItem(YTMusicCatalogItem.Kind.VIDEO, "ABCDEFGHIJL", "Track (Remix)")
        val selections = mutableListOf<String>()
        val player = object : PlayerRepository by mock<PlayerRepository>() {
            override suspend fun playExactSearchResult(item: TrackItem): StreamRoutingResult {
                selections += item.videoId
                if (item.videoId == first.identity.catalogId) awaitCancellation()
                return StreamRoutingResult.CellularRefused
            }
        }
        val factory = factory { scope, navigate, open, play ->
            UniversalSearchSession(
                scope, mapOf(YOUTUBE_MUSIC to { listOf(first, second) }),
                SearchSourceSelection(setOf(YOUTUBE_MUSIC)), {}, history(), navigate, open, play = play,
            )
        }
        val model = entry.viewModel(factory, player = player)
        val errors = mutableListOf<Int>()
        backgroundScope.launch { model.playErrors.collect { errors += it } }
        runCurrent()
        val session = checkNotNull(model.session.value)
        session.onQueryChanged("track")
        advanceTimeBy(300)
        runCurrent()
        session.onResultAction(first, SearchResultAction.PLAY)
        runCurrent()
        assertEquals(first, model.playingRequest.value)
        session.onResultAction(first, SearchResultAction.PLAY)
        runCurrent()
        assertEquals(listOf(first.identity.catalogId), selections)
        session.onResultAction(second, SearchResultAction.PLAY)
        assertEquals(second, model.playingRequest.value)
        runCurrent()
        assertNull(model.playingRequest.value)
        assertEquals(listOf(first.identity.catalogId, second.identity.catalogId), selections)
        assertEquals(listOf(R.string.universal_search_play_cellular), errors)
        entry.viewModelStore.clear()
    }

    private fun catalogItem(kind: YTMusicCatalogItem.Kind, id: String, title: String) =
        YTMusicCatalogItem(kind, id, title, "Artist", null, 120_000, false, 0).toUniversalSearchResult()

    private fun history(items: List<RecentSearchItem> = emptyList()) = mock<UniversalSearchHistory>().apply {
        whenever(recent).thenReturn(flowOf(items))
    }

    private suspend fun factory(
        open: (
            CoroutineScope,
            (UniversalSearchResult) -> Unit,
            (RecentSearchItem) -> Unit,
            (UniversalSearchResult, String?) -> Unit,
        ) -> UniversalSearchSession,
    ): UniversalSearchSessionFactory {
        val factory = mock<UniversalSearchSessionFactory>()
        whenever(factory.open(any(), any(), any(), any())).thenAnswer { invocation ->
            open(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3))
        }
        return factory
    }

    private class Entry : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
        private val photoRepository = mock<SelectedArtistPhotoRepository>().apply {
            whenever(photos).thenReturn(MutableStateFlow<Map<String, SelectedArtistPhoto>>(emptyMap()))
        }

        fun viewModel(
            factory: UniversalSearchSessionFactory,
            handle: SavedStateHandle = SavedStateHandle(),
            player: PlayerRepository = mock(),
        ) =
            ViewModelProvider(this, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    requireNotNull(modelClass.cast(SearchViewModel(factory, handle, photoRepository, player)))
            })[SearchViewModel::class.java]
    }
}

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
        val factory = factory { scope, navigate, open ->
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
    fun `result and recent route preserve original album and artist identities`() = runTest {
        val entry = Entry()
        val album = UniversalSearchResult(
            identity = CatalogIdentity(YOUTUBE_MUSIC, "album-id-special"),
            type = SearchResultType.ALBUM,
            title = "Original (Deluxe)",
            subtitle = "Original artist",
            artworkUrl = "https://example.test/original-image.jpg",
            providerRank = 0,
            rankingScore = 1.0,
            supportedActions = setOf(SearchResultAction.NAVIGATE),
        )
        val artist = resultHistoryItem(
            "Original artist", null, CatalogIdentity(YOUTUBE_MUSIC, "MPLAUCoriginal"),
            SearchResultType.ARTIST, "https://example.test/artist.jpg",
        )
        val factory = factory { scope, navigate, open ->
            UniversalSearchSession(
                scope,
                CatalogSource.entries.associateWith { source ->
                    { if (source == YOUTUBE_MUSIC) listOf(album) else emptyList() }
                },
                SearchSourceSelection(setOf(YOUTUBE_MUSIC)), {}, history(listOf(artist)), navigate, open,
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
        session.onResultAction(album, SearchResultAction.NAVIGATE)
        runCurrent()
        session.onRecentSelected(artist)
        runCurrent()
        val opened = (received[0] as SearchNavigation.Album).album
        assertEquals(album.identity.catalogId, opened.id)
        assertEquals(album.title, opened.title)
        assertEquals(album.subtitle, opened.artist)
        assertEquals(album.artworkUrl, opened.thumbnailUrl)
        assertEquals(SearchNavigation.Artist("MPLAUCoriginal", artist.label, artist.artworkUrl), received[1])
        assertNull(searchDestination(CatalogIdentity(YOUTUBE, "same-id"), SearchResultType.ALBUM, "Title", null, null))
        entry.viewModelStore.clear()
    }

    private fun history(items: List<RecentSearchItem> = emptyList()) = mock<UniversalSearchHistory>().apply {
        whenever(recent).thenReturn(flowOf(items))
    }

    private suspend fun factory(
        open: (CoroutineScope, (UniversalSearchResult) -> Unit, (RecentSearchItem) -> Unit) -> UniversalSearchSession,
    ): UniversalSearchSessionFactory {
        val factory = mock<UniversalSearchSessionFactory>()
        whenever(factory.open(any(), any(), any())).thenAnswer { invocation ->
            open(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))
        }
        return factory
    }

    private class Entry : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
        private val photoRepository = mock<SelectedArtistPhotoRepository>().apply {
            whenever(photos).thenReturn(MutableStateFlow<Map<String, SelectedArtistPhoto>>(emptyMap()))
        }

        fun viewModel(factory: UniversalSearchSessionFactory, handle: SavedStateHandle = SavedStateHandle()) =
            ViewModelProvider(this, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    requireNotNull(modelClass.cast(SearchViewModel(factory, handle, photoRepository)))
            })[SearchViewModel::class.java]
    }
}

package com.stash.feature.search

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.cache.AlbumCache
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.PreviewState
import com.stash.core.model.Playlist
import com.stash.data.download.lossless.qbdlx.QbdlxApiClient
import com.stash.data.download.lossless.qbdlx.QobuzAlbumFetcherImpl
import com.stash.data.ytmusic.InnerTubeClient
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.AlbumSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Live wiring below the ViewModel for approved YouTube Music album navigation:
 * real YTMusicApiClient parser + real AlbumCache and an InnerTube fixture.
 * Retired Qobuz identities fail closed rather than opening or fetching from
 * an unsupported music provider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumRegionFallbackWiringTest {
    private val dispatcher = StandardTestDispatcher()
    private val retiredQobuzClient = mock<QbdlxApiClient>()
    private val innerTube = mock<InnerTubeClient>()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tear() { Dispatchers.resetMain() }

    private fun vm(source: AlbumSource = AlbumSource.YOUTUBE): AlbumDiscoveryViewModel {
        val yt = YTMusicApiClient(innerTube)
        val id = if (source == AlbumSource.YOUTUBE) "MPREb_lithic" else "legacy_album_id"
        return AlbumDiscoveryViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "browseId" to id,
                    "title" to "Lithic",
                    "artist" to "Laura Misch",
                    "thumbnailUrl" to null,
                    "year" to "2026",
                    "source" to source,
                ),
            ),
            albumCache = AlbumCache(yt, QobuzAlbumFetcherImpl(retiredQobuzClient)),
            ytMusicApiClient = yt,
            prefetcher = mock(),
            playerRepository = mock(),
            musicRepository = mock(),
            streamingPreference = mock(),
            delegate = stubDelegate(),
            losslessPrefetcher = mock(),
        )
    }

    private fun stubDelegate(): TrackActionsDelegate = mock {
        on { previewState } doReturn MutableStateFlow(PreviewState.Idle as PreviewState).asStateFlow()
        on { userMessages } doReturn MutableSharedFlow<String>().asSharedFlow()
        on { downloadingIds } doReturn MutableStateFlow(emptySet<String>()).asStateFlow()
        on { downloadedIds } doReturn MutableStateFlow(emptySet<String>()).asStateFlow()
        on { previewLoadingId } doReturn MutableStateFlow<String?>(null).asStateFlow()
        on { userPlaylists } doReturn MutableStateFlow(emptyList<Playlist>())
    }

    private val couldNotLoad =
        AlbumDiscoveryStatus.Error("Check your connection and try again.", canRetry = true)
    private val retiredSource =
        AlbumDiscoveryStatus.Error(AlbumDiscoveryViewModel.RETIRED_SOURCE, canRetry = false)

    private val lithicPage = Json.parseToJsonElement(
        """
        {"contents":{"twoColumnBrowseResultsRenderer":{
          "tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"musicResponsiveHeaderRenderer":{
              "title":{"runs":[{"text":"Lithic"}]},
              "straplineTextOne":{"runs":[{"text":"Laura Misch",
                "navigationEndpoint":{"browseEndpoint":{"browseId":"UCant3y1RMmQNUV3MPIpXPVg"}}}]},
              "subtitle":{"runs":[{"text":"Album"},{"text":" • "},{"text":"2026"}]}}}
          ]}}}}],
          "secondaryContents":{"sectionListRenderer":{"contents":[
            {"musicShelfRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"lithic01"},
                "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Hide To Seek"}]}}}],
                "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:41"}]}}}]}},
              {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"lithic02"},
                "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Portals"}]}}}],
                "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"4:02"}]}}}]}}
            ]}}
          ]}}
        }}}
        """.trimIndent(),
    ).jsonObject


    @Test fun `YouTube Music browse loads the selected album without a source switch`() = runTest {
        whenever(innerTube.browse(eq("MPREb_lithic"), anyOrNull())).thenReturn(lithicPage)

        val vm = vm()
        advanceUntilIdle()

        assertEquals(AlbumDiscoveryStatus.Fresh, vm.uiState.value.status)
        assertEquals("Lithic", vm.uiState.value.hero.title)
        assertEquals(listOf("lithic01", "lithic02"), vm.uiState.value.tracks.map { it.videoId })
        verify(innerTube, times(1)).browse(eq("MPREb_lithic"), anyOrNull())
        verify(innerTube, never()).searchWithStatus(any(), anyOrNull())
        verify(retiredQobuzClient, never()).getAlbum(any())
    }

    @Test fun `temporary YouTube Music browse failure supports retry and does not poison cache`() = runTest {
        whenever(innerTube.browse(eq("MPREb_lithic"), anyOrNull())).thenReturn(null, lithicPage)

        val vm = vm()
        advanceUntilIdle()
        assertEquals(couldNotLoad, vm.uiState.value.status)

        vm.retry()
        advanceUntilIdle()

        assertEquals(AlbumDiscoveryStatus.Fresh, vm.uiState.value.status)
        assertEquals(listOf("lithic01", "lithic02"), vm.uiState.value.tracks.map { it.videoId })
        verify(innerTube, times(2)).browse(eq("MPREb_lithic"), anyOrNull())
        verify(innerTube, never()).searchWithStatus(any(), anyOrNull())
    }

    @Test fun `retired Qobuz album is rejected without fetching from either provider`() = runTest {
        val vm = vm(AlbumSource.QOBUZ)
        advanceUntilIdle()

        assertEquals(retiredSource, vm.uiState.value.status)
        verify(retiredQobuzClient, never()).getAlbum(any())
        verify(innerTube, never()).browse(any(), anyOrNull())
        verify(innerTube, never()).searchWithStatus(any(), anyOrNull())
        vm.retry() // A permanently unsupported identity must not expose retry.
        advanceUntilIdle()
        assertEquals(retiredSource, vm.uiState.value.status)
        verify(innerTube, never()).browse(any(), anyOrNull())
    }

    @Test fun `retired Qobuz playlist is rejected without network access`() = runTest {
        val vm = vm(AlbumSource.QOBUZ_PLAYLIST)
        advanceUntilIdle()

        assertEquals(retiredSource, vm.uiState.value.status)
        verify(retiredQobuzClient, never()).getAlbum(any())
        verify(innerTube, never()).browse(any(), anyOrNull())
    }
}

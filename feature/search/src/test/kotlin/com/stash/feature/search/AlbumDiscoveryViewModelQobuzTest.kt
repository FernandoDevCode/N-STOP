package com.stash.feature.search

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.cache.AlbumCache
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.PreviewState
import com.stash.core.model.Playlist
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.AlbumDetail
import com.stash.data.ytmusic.model.AlbumSource
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.TrackSummary
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies that retired album catalog identities fail closed without
 * querying obsolete sources, while approved YouTube album playback retains
 * its existing prefetch behavior. Pure album identity matching tests remain
 * below to protect conservative distinction of editions/versions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDiscoveryViewModelQobuzTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tear() { Dispatchers.resetMain() }

    private fun ytDetail() = AlbumDetail(
        id = "MPRE1", title = "YT Album", artist = "A", artistId = null,
        thumbnailUrl = null, year = "2005",
        tracks = listOf(
            TrackSummary("vid1", "T1", "A", "YT Album", 180.0, null),
        ),
        moreByArtist = emptyList(),
    )

    private fun vm(
        source: AlbumSource,
        cache: AlbumCache,
        prefetcher: PreviewPrefetcher = mock(),
        player: PlayerRepository = mock(),
        musicRepo: MusicRepository = mock(),
        delegate: TrackActionsDelegate = stubDelegate(),
        yt: YTMusicApiClient = mock(),
        title: String = "T",
        artist: String = "A",
        year: String? = null,
        qobuzTrackCount: Int? = null,
    ) = AlbumDiscoveryViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                "browseId" to (if (source == AlbumSource.QOBUZ) "123" else "MPRE1"),
                "title" to title,
                "artist" to artist,
                "thumbnailUrl" to null,
                "year" to year,
                "source" to source,
                "qobuzTrackCount" to qobuzTrackCount,
            ),
        ),
        albumCache = cache,
        ytMusicApiClient = yt,
        prefetcher = prefetcher,
        playerRepository = player,
        musicRepository = musicRepo,
        streamingPreference = mock(),
        delegate = delegate,
        losslessPrefetcher = mock(),
    )

    private fun stubDelegate(
        userPlaylists: List<Playlist> = emptyList(),
    ): TrackActionsDelegate = mock {
        on { previewState } doReturn
            MutableStateFlow(PreviewState.Idle as PreviewState).asStateFlow()
        on { userMessages } doReturn MutableSharedFlow<String>().asSharedFlow()
        on { downloadingIds } doReturn MutableStateFlow(emptySet<String>()).asStateFlow()
        on { downloadedIds } doReturn MutableStateFlow(emptySet<String>()).asStateFlow()
        on { previewLoadingId } doReturn MutableStateFlow<String?>(null).asStateFlow()
        on { this.userPlaylists } doReturn MutableStateFlow(userPlaylists)
    }


    @Test fun `retired album does not hit its old catalog or expose Retry`() = runTest {
        val cache = mock<AlbumCache>()
        val album = vm(AlbumSource.QOBUZ, cache)
        advanceUntilIdle()
        val unavailable = AlbumDiscoveryStatus.Error(
            AlbumDiscoveryViewModel.RETIRED_SOURCE, canRetry = false,
        )
        assertEquals(unavailable, album.uiState.value.status)
        verify(cache, never()).get(any(), any())

        album.retry() // Permanent source removal is not a network failure.
        advanceUntilIdle()
        assertEquals(unavailable, album.uiState.value.status)
        verify(cache, never()).get(any(), any())
    }

    @Test fun `retired playlist cannot fetch metadata or enqueue fake tracks`() = runTest {
        val cache = mock<AlbumCache>()
        val player = mock<PlayerRepository>()
        val musicRepo = mock<MusicRepository>()
        val album = vm(AlbumSource.QOBUZ_PLAYLIST, cache, player = player, musicRepo = musicRepo)
        advanceUntilIdle()

        assertEquals(
            AlbumDiscoveryStatus.Error(AlbumDiscoveryViewModel.RETIRED_SOURCE, canRetry = false),
            album.uiState.value.status,
        )
        assertTrue(album.uiState.value.tracks.isEmpty())
        album.playAlbum(0)
        advanceUntilIdle()

        verify(cache, never()).get(any(), any())
        verify(player, never()).setQueue(any(), any(), any())
        verify(musicRepo, never()).ensureTrackPersisted(any())
    }

    @Test fun `retired album does not prefetch using missing video ids`() = runTest {
        val cache = mock<AlbumCache>()
        val prefetcher = mock<PreviewPrefetcher>()
        val delegate = stubDelegate()
        vm(AlbumSource.QOBUZ, cache, prefetcher = prefetcher, delegate = delegate)
        advanceUntilIdle()

        verify(cache, never()).get(any(), any())
        verify(prefetcher, never()).prefetch(any())
        verify(delegate, never()).refreshDownloadedIds(any())
    }

    @Test fun `youtube album still runs prefetch (regression)`() = runTest {
        val cache = mock<AlbumCache>()
        whenever(cache.get(any(), any())).thenReturn(ytDetail())
        val prefetcher = mock<PreviewPrefetcher>()
        vm(AlbumSource.YOUTUBE, cache, prefetcher = prefetcher); advanceUntilIdle()
        verify(prefetcher).prefetch(any())
    }

    // Kept for conservative catalog identity matching compatibility tests.
    private fun ytCopy(
        id: String = "MPREb_yt",
        title: String = "Loveless",
        artist: String = "MBV",
        year: String? = "1991",
        releaseType: String? = "Album",
    ) = AlbumSummary(
        id = id, title = title, artist = artist, thumbnailUrl = "yt-art", year = year, releaseType = releaseType,
    )

    // ── isSameAlbum: what counts as "the same album" ────────────────────────

    @Test fun `the match ignores case, punctuation, accents and curly quotes`() {
        val apostrophe = AlbumSummary("MPREb_y", "Don't Stop", "JAY-Z", null, null)
        assertTrue(isSameAlbum(apostrophe, title = "Dont Stop", artist = "Jay Z"))
        // Qobuz and YouTube don't agree on apostrophes or accents.
        val curly = AlbumSummary("MPREb_q", "Don\u2019t Stop", "Sade", null, null)
        assertTrue(isSameAlbum(curly, title = "Don't Stop", artist = "Sade"))
        val accent = AlbumSummary("MPREb_z", "Frequency Of Love", "Victoria Monét", null, null)
        assertTrue(isSameAlbum(accent, title = "Frequency of Love", artist = "Victoria Monet"))
    }

    @Test fun `an edition or a featured credit is still the same album`() {
        val same = listOf(
            "Rubber Soul (Super Deluxe)" to "Rubber Soul",
            "Rubber Soul (Remastered 2009)" to "Rubber Soul",
            "Taking The Long Way (20th Anniversary Edition)" to "Taking The Long Way",
            "Lithic [Deluxe Edition]" to "Lithic",
            "My Aim Is True - 49th Anniversary Edition (2026 Remaster)" to "My Aim Is True - 49th Anniversary Edition",
            "Utility Modern (feat. Bill Frisell, Rashaan Carter & Marcus Gilmore)" to "Utility Modern",
            "Cancel Me (I'm Tired)" to "Cancel Me (I'm Tired)",
        )
        for ((youTube, qobuz) in same) {
            assertTrue("'$youTube' should be '$qobuz'", isSameAlbum(ytCopy(title = youTube, year = null), title = qobuz, artist = "MBV"))
        }
    }

    /** These change what's on the record, so they are other albums ("X (Live)" never opens studio "X"). */
    @Test fun `a live, remix, instrumental, acoustic, sped-up or demo release is a different album`() {
        val different = listOf(
            "Loveless (Live)", "Loveless (Remixes)", "Loveless (Instrumental)", "Loveless (Instrumentals)",
            "Loveless (Acoustic)", "Loveless (Sped Up)", "Loveless (Demos)", "Loveless - Live at the Barbican",
            "Loveless (Vol. 2)",
        )
        for (youTube in different) {
            assertFalse("'$youTube' is not 'Loveless'", isSameAlbum(ytCopy(title = youTube), title = "Loveless", artist = "MBV"))
        }
        // ...and the other way round: a live Qobuz album doesn't open the studio one.
        assertFalse(isSameAlbum(ytCopy(title = "Loveless"), title = "Loveless (Live)", artist = "MBV"))
    }

    /** A featured credit is set aside up to the next bracket or " - ", never what follows it. */
    @Test fun `a featured credit is set aside, but not what follows it`() {
        assertTrue(isSameAlbum(ytCopy(title = "Loveless feat. Kevin Shields"), title = "Loveless", artist = "MBV"))
        val live = listOf(
            "Loveless (feat. Kevin Shields) [Live]", "Loveless feat. Kevin Shields (Live)", "Loveless feat. Kevin Shields - Live",
        )
        for (youTube in live) {
            assertFalse("'$youTube' is not 'Loveless'", isSameAlbum(ytCopy(title = youTube), title = "Loveless", artist = "MBV"))
        }
    }

    /** A subtitle in brackets is part of the title: "Cancel Me" isn't "Cancel Me (I'm Tired)". */
    @Test fun `a subtitle in brackets is part of the title`() {
        assertFalse(isSameAlbum(ytCopy(title = "Cancel Me"), title = "Cancel Me (I'm Tired)", artist = "MBV"))
        assertFalse(isSameAlbum(ytCopy(title = "Cancel Me (I'm Tired)"), title = "Cancel Me", artist = "MBV"))
    }

    @Test fun `years more than one apart are different albums`() {
        assertTrue(isSameAlbum(ytCopy(year = "1991"), title = "Loveless", artist = "MBV", year = "1991"))
        assertTrue(isSameAlbum(ytCopy(year = "1992"), title = "Loveless", artist = "MBV", year = "1991"))
        assertTrue(isSameAlbum(ytCopy(year = "1991"), title = "Loveless", artist = "MBV", year = "1991-11-04"))
        assertFalse(isSameAlbum(ytCopy(year = "1993"), title = "Loveless", artist = "MBV", year = "1991"))
        // Unknown on either side: nothing to compare.
        assertTrue(isSameAlbum(ytCopy(year = null), title = "Loveless", artist = "MBV", year = "1991"))
        assertTrue(isSameAlbum(ytCopy(year = "2021"), title = "Loveless", artist = "MBV", year = null))
    }

    /**
     * The two catalogs date a remaster differently: YouTube's "Abbey Road (Remastered 2009)"
     * says 1969 where Qobuz says 2009, and Qobuz dates "Rumours (2004 Remaster)" 1977 where
     * YouTube says 2004. Once an edition was set aside on either side the years aren't
     * compared; a plain same-title album still has to agree.
     */
    @Test fun `an edition the two catalogs date differently is still the same album`() {
        val abbeyRoad = ytCopy(title = "Abbey Road (Remastered 2009)", year = "1969")
        assertTrue(isSameAlbum(abbeyRoad, title = "Abbey Road (Remastered 2009)", artist = "MBV", year = "2009-09-09"))
        assertTrue(isSameAlbum(ytCopy(title = "Rumours", year = "2004"), title = "Rumours (2004 Remaster)", artist = "MBV", year = "1977"))
        assertTrue(isSameAlbum(ytCopy(title = "Rubber Soul (Super Deluxe)", year = "2026"), title = "Rubber Soul", artist = "MBV", year = "1965"))
        // A featured credit isn't an edition: the years still count.
        assertFalse(isSameAlbum(ytCopy(title = "Loveless (feat. Kevin Shields)", year = "2021"), title = "Loveless", artist = "MBV", year = "1991"))
    }

    /**
     * Qobuz names a collaboration "Jay Z and Kanye West" (live 2026-10-03: "Watch The
     * Throne"), where YouTube's search results name only the first artist. And one writes
     * "Echo And The Bunnymen" where the other writes "Echo & the Bunnymen".
     */
    @Test fun `a collaboration matches on its first credited artist, and "and" counts as "&"`() {
        val throne = AlbumSummary("MPREb_t", "Watch The Throne", "JAY-Z", null, "2011")
        assertTrue(isSameAlbum(throne, title = "Watch The Throne", artist = "Jay Z and Kanye West", year = "2011"))
        assertTrue(isSameAlbum(throne, title = "Watch The Throne", artist = "Jay Z & Kanye West", year = "2011"))
        assertTrue(isSameAlbum(throne, title = "Watch The Throne", artist = "Jay Z, Kanye West", year = "2011"))
        val oceanRain = AlbumSummary("MPREb_e", "Ocean Rain", "Echo & the Bunnymen", null, "1984")
        assertTrue(isSameAlbum(oceanRain, title = "Ocean Rain", artist = "Echo And The Bunnymen", year = "1984"))
        // Someone else is still someone else.
        assertFalse(isSameAlbum(throne.copy(artist = "Jay Electronica"), title = "Watch The Throne", artist = "Jay Z and Kanye West"))
    }

    @Test fun `a single is not the album, an EP or an unlabelled release can be`() {
        assertFalse(isSameAlbum(ytCopy(releaseType = "Single"), title = "Loveless", artist = "MBV"))
        assertTrue(isSameAlbum(ytCopy(releaseType = "EP"), title = "Loveless", artist = "MBV"))
        assertTrue(isSameAlbum(ytCopy(releaseType = null), title = "Loveless", artist = "MBV"))
    }

    @Test fun `a single matches only a Qobuz release known to be a single`() {
        val single = ytCopy(releaseType = "Single")
        assertTrue(isSameAlbum(single, title = "Loveless", artist = "MBV", qobuzTrackCount = 1))
        assertTrue(isSameAlbum(single, title = "Loveless", artist = "MBV", qobuzTrackCount = 3))
        assertFalse(isSameAlbum(single, title = "Loveless", artist = "MBV", qobuzTrackCount = 4))
        assertFalse(isSameAlbum(single, title = "Loveless", artist = "MBV", qobuzTrackCount = 0))
        assertFalse(isSameAlbum(single, title = "Loveless", artist = "MBV", qobuzTrackCount = null))
    }

    // ── pickYouTubeCopy: which match comes first ────────────────────────────

    @Test fun `a Qobuz album takes the Album before a same-titled EP listed first`() {
        val ep = ytCopy(id = "MPREb_ep", releaseType = "EP")
        val album = ytCopy(id = "MPREb_album", releaseType = "Album")
        assertEquals("MPREb_album", pickYouTubeCopy(listOf(ep, album), "Loveless", "MBV", "1991", qobuzTrackCount = 11)?.id)
        // Length unknown: YouTube's order stands.
        assertEquals("MPREb_ep", pickYouTubeCopy(listOf(ep, album), "Loveless", "MBV", "1991", qobuzTrackCount = null)?.id)
    }

    @Test fun `a Qobuz single takes an explicit Single before an unlabelled release`() {
        val unlabelled = ytCopy(id = "MPREb_unlabelled", releaseType = null)
        val single = ytCopy(id = "MPREb_single", releaseType = "Single")
        assertEquals("MPREb_single", pickYouTubeCopy(listOf(unlabelled, single), "Loveless", "MBV", "1991", qobuzTrackCount = 1)?.id)
    }

    @Test fun `the match needs a title and an artist on both sides`() {
        val copy = AlbumSummary("MPREb_x", "Loveless", "MBV", null, null)
        assertFalse(isSameAlbum(copy, title = "Loveless", artist = ""))
        assertFalse(isSameAlbum(copy, title = "", artist = "MBV"))
        assertFalse(isSameAlbum(copy.copy(artist = ""), title = "Loveless", artist = ""))
    }
}

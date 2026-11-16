package com.stash.core.media

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.sync.TrackIdentityEvents
import com.stash.core.media.service.StashPlaybackService.Companion.EXTRA_TRACK_ID
import com.stash.core.media.service.StashPlaybackService.Companion.EXTRA_TRACK_YOUTUBE_ID
import com.stash.core.media.streaming.ConnectivityMonitor
import com.stash.core.media.streaming.StreamSourceRegistry
import com.stash.core.media.streaming.StreamUrl
import com.stash.core.media.streaming.StreamUrlCache
import com.stash.core.media.streaming.StreamingMediaSourceFactory
import com.stash.core.model.TrackItem
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A track played from the Search tab used to be queued under a hash of its
 * video id — an identity that lived only in memory. The persisted queue then
 * held an id no library row answers to, so no resume path (the Resume
 * shortcut, the idle-resume play, the cold-start ghost of #462) could rebuild
 * a Search-started queue, and none of the per-row bookkeeping (art upgrades,
 * quality stamps, "now playing" markers) could find it either.
 *
 * Queue additions already solve this with [MusicRepository.ensureTrackPersisted]
 * (dedup by video id / Spotify URI / canonical identity, else insert). A Search
 * play must take the same road.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerRepositorySearchPlayIdentityTest {

    private val playbackStateStore: PlaybackStateStore = mockk(relaxed = true)
    private val musicRepository: MusicRepository = mockk {
        every { trackDeletions } returns MutableSharedFlow()
    }
    private val streamingPreference: StreamingPreference = mockk(relaxed = true)
    private val streamResolver: StreamSourceRegistry = mockk()
    private val streamUrlCache: StreamUrlCache = mockk(relaxUnitFun = true)
    private val connectivity: ConnectivityMonitor = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val controller: MediaController = mockk(relaxed = true)
    private val playbackResumer: PlaybackResumer = mockk(relaxed = true)
    private val trackIdentityEvents: TrackIdentityEvents = mockk {
        every { changes } returns MutableSharedFlow()
    }

    private lateinit var repo: PlayerRepositoryImpl

    @Before
    fun setUp() {
        repo = PlayerRepositoryImpl(
            context = ApplicationProvider.getApplicationContext(),
            playbackStateStore = playbackStateStore,
            musicRepository = musicRepository,
            streamingPreference = streamingPreference,
            streamResolver = streamResolver,
            streamUrlCache = streamUrlCache,
            connectivity = connectivity,
            trackDao = trackDao,
            playbackResumer = playbackResumer,
            radioGenerator = mockk(relaxed = true),
            trackIdentityEvents = trackIdentityEvents,
            playbackSessionBus = PlaybackSessionBus(),
        )
        every { controller.isConnected } returns true
        every { controller.mediaItemCount } returns 0
        every { streamUrlCache.get(any()) } returns null
        repo.controllerDeferred = controller
        shadowOf(Looper.getMainLooper()).idle()
        repo.controllerDeferred = controller // the bus's initial "not alive" released the seam
    }

    @Test
    fun `a search-tab play is queued under its persisted library id`() = runTest {
        coEvery { musicRepository.ensureTrackPersisted(any()) } returns 531L
        coEvery { streamingPreference.current() } returns true
        every { streamingPreference.streamOnCellular } returns flowOf(true)
        every { connectivity.isConnected() } returns true
        every { connectivity.isCellular() } returns false
        coEvery { streamResolver.resolve(any(), any(), any()) } returns StreamUrl(
            url = "https://cdn.example/reckoner?etsp=42",
            expiresAtMs = 42_000L,
        )
        val queued = slot<MediaItem>()
        every { controller.setMediaItem(capture(queued)) } just Runs
        val item = TrackItem(
            videoId = "_uofQD-N6UI", title = "Reckoner", artist = "Radiohead",
            durationSeconds = 290.0, thumbnailUrl = null, album = "In Rainbows",
        )

        val result = repo.playFromStream(item)

        assertThat(result).isInstanceOf(StreamRoutingResult.Item::class.java)
        assertThat(queued.captured.mediaId).isEqualTo("531")
        assertThat(queued.captured.mediaMetadata.extras?.getLong(EXTRA_TRACK_ID)).isEqualTo(531L)
        coVerify(exactly = 1) {
            musicRepository.ensureTrackPersisted(match { it.youtubeId == "_uofQD-N6UI" && it.title == "Reckoner" && it.id == 0L })
        }
    }

    @Test
    fun `each selected version reaches the controller with the exact id and bypasses shared URLs`() = runTest {
        val versions = listOf(
            "ABCDEFGHIJK" to "Track",
            "ABCDEFGHIJL" to "Track (Live)",
            "ABCDEFGHIJM" to "Track (Remix)",
            "ABCDEFGHIJN" to "Track (Cover)",
        )
        val queued = mutableListOf<MediaItem>()
        every { controller.setMediaItem(capture(queued)) } just Runs
        for ((index, version) in versions.withIndex()) {
            val (videoId, title) = version
            val id = index + 100L
            allowExact(item(videoId, title), id)
            every { streamUrlCache.get(id) } returns stream("WRONG-video")
            coEvery { streamResolver.resolveYouTubeRefresh(match { it.youtubeId == videoId }) } returns stream(videoId)

            assertThat(repo.playExactSearchResult(item(videoId, title))).isInstanceOf(StreamRoutingResult.Item::class.java)

            val media = queued.last()
            assertThat(media.mediaId).isEqualTo(id.toString())
            assertThat(media.localConfiguration?.uri.toString()).isEqualTo(stream(videoId).url)
            assertThat(media.mediaMetadata.extras?.getString(EXTRA_TRACK_YOUTUBE_ID)).isEqualTo(videoId)
            assertThat(media.mediaMetadata.extras?.getString(StreamingMediaSourceFactory.EXTRA_EXACT_YOUTUBE_ID))
                .isEqualTo(videoId)
            verify(exactly = 1) { streamUrlCache.invalidate(id) }
        }
        assertThat(queued.map { it.mediaMetadata.title }).containsExactlyElementsIn(versions.map { it.second }).inOrder()
        verify(exactly = 0) { streamUrlCache.get(any()) }
        coVerify(exactly = 0) { streamResolver.resolve(any(), any(), any()) }
        coVerify(exactly = 0) { musicRepository.ensureTrackPersisted(any()) }
    }

    @Test
    fun `wrong room identity stops playback before resolving`() = runTest {
        val selected = item("ABCDEFGHIJK")
        allowExact(selected, 100)
        coEvery { trackDao.getById(100) } returns TrackEntity(
            id = 100, title = selected.title, artist = selected.artist, youtubeId = "ABCDEFGHIJL",
        )

        assertThat(repo.playExactSearchResult(selected)).isEqualTo(StreamRoutingResult.NotAvailable)
        assertThat(repo.playExactSearchResult(selected.copy(videoId = "bad-id"))).isEqualTo(StreamRoutingResult.NotAvailable)
        coVerify(exactly = 0) { streamResolver.resolveYouTubeRefresh(any()) }
        verify(exactly = 0) { controller.setMediaItem(any<MediaItem>()) }
    }

    @Test
    fun `an unavailable or blocked exact stream never switches recordings`() = runTest {
        val selected = item("ABCDEFGHIJK")
        allowExact(selected, 100)
        coEvery { streamResolver.resolveYouTubeRefresh(any()) } returns null
        assertThat(repo.playExactSearchResult(selected)).isEqualTo(StreamRoutingResult.NotAvailable)
        every { connectivity.isCellular() } returns true
        every { streamingPreference.streamOnCellular } returns flowOf(false)
        assertThat(repo.playExactSearchResult(selected)).isEqualTo(StreamRoutingResult.CellularRefused)
        every { connectivity.isConnected() } returns false
        assertThat(repo.playExactSearchResult(selected)).isEqualTo(StreamRoutingResult.NoConnectivity)
        coVerify(exactly = 1) { streamResolver.resolveYouTubeRefresh(any()) }
        coVerify(exactly = 0) { streamResolver.resolve(any(), any(), any()) }
        verify(exactly = 0) { controller.setMediaItem(any<MediaItem>()) }
    }

    @Test
    fun `rapid duplicate is ignored and a late response cannot replace the latest choice`() = runTest {
        val original = item("ABCDEFGHIJK")
        val latest = item("ABCDEFGHIJL", "Track (Live)")
        allowExact(original, 100)
        allowExact(latest, 101)
        val resolving = CompletableDeferred<Unit>()
        val firstStream = CompletableDeferred<StreamUrl>()
        coEvery { streamResolver.resolveYouTubeRefresh(match { it.youtubeId == original.videoId }) } coAnswers {
            resolving.complete(Unit)
            firstStream.await()
        }
        coEvery { streamResolver.resolveYouTubeRefresh(match { it.youtubeId == latest.videoId }) } returns stream(latest.videoId)
        val queued = slot<MediaItem>()
        every { controller.setMediaItem(capture(queued)) } just Runs

        val pending = async { repo.playExactSearchResult(original) }
        resolving.await()
        assertThat(repo.playExactSearchResult(original)).isEqualTo(StreamRoutingResult.Deduped)
        assertThat(repo.playExactSearchResult(latest)).isInstanceOf(StreamRoutingResult.Item::class.java)
        firstStream.complete(stream(original.videoId))
        assertThat(pending.await()).isEqualTo(StreamRoutingResult.Deduped)

        verify(exactly = 1) { controller.setMediaItem(any<MediaItem>()) }
        coVerify(exactly = 1) { musicRepository.ensureExactTrackPersisted(match { it.youtubeId == original.videoId }) }
        assertThat(queued.captured.mediaMetadata.extras?.getString(EXTRA_TRACK_YOUTUBE_ID)).isEqualTo(latest.videoId)
    }

    private fun allowExact(item: TrackItem, id: Long) {
        every { connectivity.isConnected() } returns true
        every { connectivity.isCellular() } returns false
        coEvery {
            musicRepository.ensureExactTrackPersisted(match {
                it.youtubeId == item.videoId && it.title == item.title && it.spotifyId == null && it.isrc == null
            })
        } returns id
        coEvery { trackDao.getById(id) } returns TrackEntity(
            id = id, title = item.title, artist = item.artist, youtubeId = item.videoId,
        )
    }

    private fun item(videoId: String, title: String = "Track") = TrackItem(videoId, title, "Artist", 120.0, null)

    private fun stream(videoId: String) = StreamUrl(
        url = "https://cdn.example/$videoId",
        expiresAtMs = 42_000L,
        origin = "youtube",
    )
}

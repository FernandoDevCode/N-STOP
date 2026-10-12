package com.stash.data.download.search

import android.content.Context
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import com.stash.core.data.audio.AudioMetadata
import com.stash.core.data.audio.LoudnessMeasurer
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.files.LocalFileOps
import com.stash.core.data.prefs.QualityPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.model.MusicSource
import com.stash.core.model.QualityTier
import com.stash.core.model.TrackItem
import com.stash.data.download.DownloadExecutor
import com.stash.data.download.DownloadResult
import com.stash.data.download.files.FileOrganizer.CommittedTrack
import com.stash.data.download.jiosaavn.JioSaavnResolver
import com.stash.data.download.lossless.LosslessSourcePreferences
import com.stash.data.download.lossless.LosslessSourceRegistry
import com.stash.data.download.lossless.LosslessUrlDownloader
import com.stash.data.download.lyrics.LyricsFetchTrigger
import com.stash.data.download.shared.TrackFinalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Which library row a Search download is recorded on, now that the canonical words are the loose
 * rule ("Song (Live)" and "Song" are both "song"). The same video always reuses its row; a row found
 * by its words is taken only under SameSongRule: no conflicting id, the same version, and no file
 * of its own. Everything else gets a new row, so a different version or a different video of a
 * song the library has is never recorded on that song's row.
 */
class SearchDownloadCoordinatorReuseTest {

    private val registry: LosslessSourceRegistry = mockk(relaxed = true)
    private val downloadExecutor: DownloadExecutor = mockk()
    private val trackFinalizer: TrackFinalizer = mockk()
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val losslessPrefs: LosslessSourcePreferences = mockk(relaxed = true)
    private val localFileOps: LocalFileOps = mockk()
    private val qualityPrefs: QualityPreference = mockk { every { qualityTier } returns flowOf(QualityTier.MAX) }

    private val tmpCacheDir = File(
        System.getProperty("java.io.tmpdir"),
        "stash-search-reuse-test-${System.nanoTime()}",
    ).also { it.mkdirs() }

    private fun newSubject() = SearchDownloadCoordinator(
        registry = registry,
        previewCache = mockk<SimpleCache>(relaxed = true),
        httpDataSourceFactory = mockk<HttpDataSource.Factory>(relaxed = true),
        cacheKeyFactory = mockk<CacheKeyFactory>(relaxed = true),
        downloadExecutor = downloadExecutor,
        trackFinalizer = trackFinalizer,
        trackDao = trackDao,
        musicRepository = mockk<MusicRepository>(relaxed = true),
        blocklistGuard = mockk<BlocklistGuard>(relaxed = true),
        context = context,
        losslessPrefs = losslessPrefs,
        jioSaavnResolver = mockk<JioSaavnResolver> { coEvery { resolve(any(), any()) } returns null },
        losslessUrlDownloader = mockk<LosslessUrlDownloader>(),
        audioDurationExtractor = mockk(),
        downloadQueueDao = mockk<DownloadQueueDao>(relaxed = true),
        localFileOps = localFileOps,
        loudnessMeasurer = mockk<LoudnessMeasurer>(relaxed = true),
        lyricsFetchTrigger = mockk<LyricsFetchTrigger>(relaxed = true),
        qualityPrefs = qualityPrefs,
    )

    @Before
    fun setUp() {
        every { context.cacheDir } returns tmpCacheDir
        every { localFileOps.acceptDownloadOrDelete(any()) } returns true
        coEvery { losslessPrefs.enabledNow() } returns false
        val tempFile = File.createTempFile("search_reuse", ".opus").apply { deleteOnExit() }
        coEvery { downloadExecutor.download(any(), any(), any(), any(), any()) } returns DownloadResult.Success(tempFile)
        coEvery { trackFinalizer.finalizeFile(any(), any(), any(), any()) } returns
            TrackFinalizer.FinalizeResult.Success(
                committed = CommittedTrack(filePath = "/library/Artist/Song.opus", sizeBytes = 4096L),
                meta = AudioMetadata(durationMs = 200_000L, bitrateKbps = 128, format = "opus", sampleRateHz = 48_000, bitsPerSample = 16),
            )
        coEvery { trackDao.findByYoutubeId(any()) } returns null
        coEvery { trackDao.insert(any()) } returns NEW_ROW
        coEvery { trackDao.markAsDownloaded(any(), any(), any(), any(), any(), any()) } returns 1
    }

    private fun item(title: String, videoId: String = VIDEO) = TrackItem(
        videoId = videoId, title = title, artist = "Artist", durationSeconds = 200.0, thumbnailUrl = null,
    )

    /** Rows sharing the loose words "song" / "artist". */
    private fun rowsByWords(vararg rows: TrackEntity) {
        coEvery { trackDao.findAllByCanonicalIdentity("song", "artist") } returns rows.toList()
    }

    private fun row(id: Long, title: String, youtubeId: String? = null, spotifyUri: String? = null, file: String? = null) =
        TrackEntity(
            id = id, title = title, artist = "Artist", youtubeId = youtubeId, spotifyUri = spotifyUri,
            filePath = file, isDownloaded = file != null, canonicalTitle = "song", canonicalArtist = "artist",
            source = if (spotifyUri != null) MusicSource.SPOTIFY else MusicSource.YOUTUBE,
        )

    private suspend fun download(item: TrackItem) {
        val statuses = newSubject().download(item).toList()
        assertTrue("got $statuses", statuses.last() is SearchDownloadStatus.Completed)
    }

    private fun recordedOn(id: Long) =
        coVerify(exactly = 1) { trackDao.markAsDownloaded(id, any(), any(), any(), any(), any()) }

    @Test
    fun `the same video reuses its row`() = runTest {
        coEvery { trackDao.findByYoutubeId(VIDEO) } returns row(5L, "Song", youtubeId = VIDEO, file = "/old.opus")

        download(item("Song"))

        coVerify(exactly = 0) { trackDao.insert(any()) }
        recordedOn(5L)
    }

    @Test
    fun `a live version gets its own row next to the library's Song`() = runTest {
        rowsByWords(row(3L, "Song", spotifyUri = "spotify:track:studio"))

        download(item("Song (Live)"))

        coVerify(exactly = 1) {
            trackDao.insert(match { it.title == "Song (Live)" && it.youtubeId == VIDEO && it.canonicalTitle == "song" })
        }
        recordedOn(NEW_ROW)
        coVerify(exactly = 0) { trackDao.markAsDownloaded(3L, any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { trackDao.updateYoutubeIdIfUnclaimed(3L, any()) }
    }

    @Test
    fun `a synced stub of the same song is filled and takes the video id`() = runTest {
        // The Spotify sync's row: different words in brackets (a credit), no video id, no file yet.
        rowsByWords(row(3L, "Song (feat. X)", spotifyUri = "spotify:track:abc"))

        download(item("Song"))

        coVerify(exactly = 0) { trackDao.insert(any()) }
        coVerify(exactly = 1) { trackDao.updateYoutubeIdIfUnclaimed(3L, VIDEO) }
        recordedOn(3L)
    }

    @Test
    fun `another video of the song gets its own row`() = runTest {
        rowsByWords(row(6L, "Song", youtubeId = "otherVideo1"))

        download(item("Song"))

        recordedOn(NEW_ROW)
        coVerify(exactly = 0) { trackDao.markAsDownloaded(6L, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a row that already holds a file is never overwritten by a words match`() = runTest {
        // A lossless download of the song: no video id to conflict, but its own file.
        rowsByWords(row(4L, "Song", spotifyUri = "spotify:track:abc", file = "/library/Artist/Song.flac"))

        download(item("Song"))

        recordedOn(NEW_ROW)
        coVerify(exactly = 0) { trackDao.markAsDownloaded(4L, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the first row the rule allows is taken, not the first by words`() = runTest {
        rowsByWords(
            row(2L, "Song (Live)"),
            row(3L, "Song", youtubeId = "otherVideo1"),
            row(8L, "Song [Official Video]"),
        )

        download(item("Song"))

        coVerify(exactly = 0) { trackDao.insert(any()) }
        recordedOn(8L)
    }

    private companion object {
        const val VIDEO = "dQw4w9WgXcQ"
        const val NEW_ROW = 7L
    }
}

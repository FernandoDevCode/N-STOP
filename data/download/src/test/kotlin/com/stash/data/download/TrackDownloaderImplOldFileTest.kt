package com.stash.data.download

import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.files.LocalFileOps
import com.stash.core.data.sync.TrackDownloadOutcome
import com.stash.core.model.Track
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A re-download that lands on a new path deletes the track's old file, but
 * only once the new file has passed validation, and never a file another row
 * still records (an import that took a downloaded song's file before imports
 * picked a name of their own). The YouTube rung used to delete it before
 * validation, so a junk new body left the track with neither file.
 */
class TrackDownloaderImplOldFileTest {

    @get:Rule val tmp = TemporaryFolder()

    private val downloadManager: DownloadManager = mockk()
    private val downloadQueueDao: DownloadQueueDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)

    // Real LocalFileOps over a mock Context: plain paths never touch the Context.
    private fun newSubject() = TrackDownloaderImpl(
        downloadManager = downloadManager,
        downloadQueueDao = downloadQueueDao,
        localFileOps = LocalFileOps(mockk(relaxed = true)),
        trackDao = trackDao,
    )

    private fun downloads(bytes: Int): String {
        val new = tmp.newFile("lacrymosa.flac").apply { writeBytes(ByteArray(bytes)) }
        coEvery { downloadManager.downloadTrack(any(), any()) } returns TrackDownloadResult.Success(new.absolutePath)
        return new.absolutePath
    }

    @Test
    fun `a validated re-download deletes an old file only it records`() = runTest {
        val old = tmp.newFile("lacrymosa-old.opus")
        downloads(REAL_SIZE)
        coEvery { trackDao.countOtherTracksWithFilePath(old.absolutePath, 7L) } returns 0

        val outcome = newSubject().downloadTrack(track(old.absolutePath))

        assertTrue("got $outcome", outcome is TrackDownloadOutcome.Success)
        assertFalse(old.exists())
    }

    @Test
    fun `a re-download keeps an old file another song still plays from`() = runTest {
        val old = tmp.newFile("lacrymosa-old.opus")
        downloads(REAL_SIZE)
        coEvery { trackDao.countOtherTracksWithFilePath(old.absolutePath, 7L) } returns 1

        val outcome = newSubject().downloadTrack(track(old.absolutePath))

        assertTrue("got $outcome", outcome is TrackDownloadOutcome.Success)
        assertTrue(old.exists())
    }

    @Test
    fun `a junk re-download keeps the old file`() = runTest {
        val old = tmp.newFile("lacrymosa-old.opus")
        downloads(274)

        val outcome = newSubject().downloadTrack(track(old.absolutePath))

        assertTrue("got $outcome", outcome is TrackDownloadOutcome.Failed)
        assertTrue(old.exists())
    }

    @Test
    fun `a re-download at the same path keeps the new file`() = runTest {
        val path = downloads(REAL_SIZE)

        val outcome = newSubject().downloadTrack(track(path))

        assertTrue("got $outcome", outcome is TrackDownloadOutcome.Success)
        assertTrue(java.io.File(path).exists())
    }

    private fun track(oldPath: String) = Track(
        id = 7L,
        title = "Lacrymosa",
        artist = "Evanescence",
        album = "Synthesis",
        filePath = oldPath,
    )

    private companion object {
        const val REAL_SIZE = 20_000 // above the 16 KiB floor
    }
}

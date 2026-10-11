package com.stash.data.download

import android.util.Log
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.fileUsedByAnotherTrack
import com.stash.core.data.files.LocalFileOps
import com.stash.core.data.sync.TrackDownloadOutcome
import com.stash.core.data.sync.TrackDownloader
import com.stash.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [TrackDownloader] that delegates to [DownloadManager].
 *
 * Bound into the Hilt graph via [di.DownloadModule] so that any component
 * depending on the [TrackDownloader] interface (e.g. TrackDownloadWorker in
 * `:core:data`) receives this implementation without a circular module dependency.
 */
@Singleton
class TrackDownloaderImpl @Inject constructor(
    private val downloadManager: DownloadManager,
    private val downloadQueueDao: DownloadQueueDao,
    private val localFileOps: LocalFileOps,
    private val trackDao: TrackDao,
) : TrackDownloader {

    override suspend fun downloadTrack(track: Track, preResolvedUrl: String?): TrackDownloadOutcome {
        return when (val result = downloadManager.downloadTrack(track, preResolvedUrl)) {
            is TrackDownloadResult.Success -> {
                // Validate the committed file before it can be marked downloaded.
                // A "successful" download can still be junk — e.g. yt-dlp writing
                // a ~274-byte error body into a .webm. If that were marked
                // isDownloaded it would later fail playback with
                // ERROR_CODE_PARSING_CONTAINER_MALFORMED and skip-storm the queue.
                // acceptDownloadOrDelete deletes the junk + returns false, so the
                // track stays not-downloaded (and therefore streamable); the
                // worker routes Failed through its normal bounded-retry handling.
                if (localFileOps.acceptDownloadOrDelete(result.filePath)) {
                    deleteReplacedFile(track, result.filePath)
                    TrackDownloadOutcome.Success(result.filePath)
                } else {
                    Log.w(TAG, "discarded too-small download for '${track.artist} - ${track.title}': ${result.filePath}")
                    TrackDownloadOutcome.Failed("Downloaded file too small — discarded as a failed download")
                }
            }
            is TrackDownloadResult.Unmatched -> TrackDownloadOutcome.Unmatched(result.rejectedVideoId)
            is TrackDownloadResult.Failed -> TrackDownloadOutcome.Failed(result.error)
            // v0.9.17: Deferred is the strict-FLAC stay-in-queue signal.
            // Translate to a DownloadStatus.WAITING_FOR_LOSSLESS DAO write so
            // the row persists in the queue (out of the retryable-FAILED set)
            // until the LosslessRetryScheduler re-resolves it. The benign
            // [TrackDownloadOutcome.Deferred] outcome tells [TrackDownloadWorker]
            // not to increment retries, not to mark FAILED, and not to fail
            // the worker chain.
            TrackDownloadResult.Deferred -> {
                val queueEntry = downloadQueueDao.getByTrackId(track.id)
                queueEntry?.let {
                    downloadQueueDao.deferIfInProgress(it.id)
                }
                TrackDownloadOutcome.Deferred
            }
        }
    }

    /**
     * Deletes the file [track] had before this download, now that the new one
     * at [newPath] has passed validation: a re-download whose name changed
     * (the canonicalizer retitled the track, or the library layout changed)
     * or that came from another source (a FLAC replacing an opus). The YouTube
     * path used to delete it itself, before validation, so a junk new body
     * left the track with neither file; with `File.delete`, which never
     * removed an SD-card (`content://`) document; and lossless or JioSaavn
     * re-downloads never cleaned up at all. A file another track still plays
     * from stays, and so does the new file itself (a same-name re-download).
     */
    private suspend fun deleteReplacedFile(track: Track, newPath: String) {
        val oldPath = track.filePath?.takeIf { it.isNotBlank() } ?: return
        if (localFileOps.isSameFile(oldPath, newPath)) return
        if (trackDao.fileUsedByAnotherTrack(oldPath, track.id)) return
        val deleted = localFileOps.delete(oldPath)
        Log.d(TAG, "deleted the replaced file $oldPath for trackId=${track.id}: $deleted")
    }

    private companion object {
        const val TAG = "TrackDownloader"
    }
}

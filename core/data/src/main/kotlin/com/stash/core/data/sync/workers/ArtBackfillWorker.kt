package com.stash.core.data.sync.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stash.core.data.audio.AudioDurationExtractor
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.model.YouTubeVideoId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fills missing artwork using an existing validated YouTube video ID
 * and repairs downloaded audio duration from the local file container.
 */
@HiltWorker
class ArtBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val trackDao: TrackDao,
    private val audioDurationExtractor: AudioDurationExtractor,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ArtBackfill"
        private const val WORK_NAME = "stash_art_backfill"
        private const val MIX_REFRESH_WORK_NAME = "stash_art_backfill_mix_refresh"
        private const val BATCH_SIZE = 500

        /**
         * Schedule the one-shot backfill. `KEEP` policy: if a previous run
         * is queued or running, leave it; if it already succeeded this
         * install, don't re-enqueue. Re-launches become no-ops.
         */
        fun enqueueOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = OneTimeWorkRequestBuilder<ArtBackfillWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                work,
            )
        }

        /**
         * v0.9.38 — re-fire the backfill after each
         * [StashMixRefreshWorker] cycle so freshly-created stream-only
         * stubs gain art + duration within one refresh. Distinct work
         * name so the install-time one-shot ([enqueueOneTime]) keeps its
         * own semantics and the two never compete. Both pass paths are
         * idempotent — every UPDATE guards on "still missing" — so
         * concurrent runs would no-op cleanly anyway, but separate names
         * keep the WorkManager log clear.
         *
         * `KEEP`, not `REPLACE`: a mix refresh runs on every cold start and
         * after every discovery drain, and a pass over a full batch is
         * in-flight pass each time and it could never finish. A refresh
         * that lands while a pass is running leaves it be; its new stubs
         * are the newest rows, which the next pass serves first
         * ([TrackDao.findStreamableMetadataBackfillCandidates]).
         */
        fun enqueueFromMixRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = OneTimeWorkRequestBuilder<ArtBackfillWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                MIX_REFRESH_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                work,
            )
        }
    }

    override suspend fun doWork(): Result {
        runArtPass()
        runDurationPass()
        return Result.success()
    }

    /**
     * candidates match the query.
     */
    private suspend fun runArtPass() {
        val candidates = trackDao.findArtBackfillCandidates(BATCH_SIZE)
        if (candidates.isEmpty()) {
            Log.d(TAG, "no tracks need art backfill")
            return
        }
        Log.i(TAG, "backfilling art for ${candidates.size} tracks")

        var filled = 0
        var skipped = 0
        for (row in candidates) {
            val artUrl = resolveArt(row)
            if (artUrl != null) {
                runCatching { trackDao.fillMissingAlbumArtUrl(row.id, artUrl) }
                    .onSuccess { filled++ }
                    .onFailure { e ->
                        Log.w(TAG, "fillMissingAlbumArtUrl failed for trackId=${row.id}", e)
                    }
            } else {
                skipped++
            }
        }
        Log.i(TAG, "art backfill done: filled=$filled skipped=$skipped (no art found)")
    }

    /**
     * Pass 2: repair missing `duration_ms`. Purely local — reads each
     * file's container metadata via `MediaMetadataRetriever`. No rate-
     * limit needed; each extraction takes single-digit milliseconds and
     * the candidate set is typically small (dozens, not thousands).
     */
    private suspend fun runDurationPass() {
        val candidates = trackDao.findDurationBackfillCandidates(BATCH_SIZE)
        if (candidates.isEmpty()) {
            Log.d(TAG, "no tracks need duration backfill")
            return
        }
        Log.i(TAG, "backfilling duration for ${candidates.size} tracks")

        var filled = 0
        var skipped = 0
        for (row in candidates) {
            val ms = audioDurationExtractor.extractMs(row.filePath)
            if (ms != null && ms > 0) {
                runCatching { trackDao.fillMissingDuration(row.id, ms) }
                    .onSuccess { filled++ }
                    .onFailure { e ->
                        Log.w(TAG, "fillMissingDuration failed for trackId=${row.id}", e)
                    }
            } else {
                skipped++
            }
        }
        Log.i(TAG, "duration backfill done: filled=$filled skipped=$skipped (unreadable or missing)")
    }

    /** Use the YouTube thumbnail only when the video ID passes validation. */
    private fun resolveArt(row: com.stash.core.data.db.dao.ArtBackfillRow): String? =
        row.youtubeId.takeIf(YouTubeVideoId::isValid)?.let { vid ->
            "https://i.ytimg.com/vi/$vid/maxresdefault.jpg"
        }
}

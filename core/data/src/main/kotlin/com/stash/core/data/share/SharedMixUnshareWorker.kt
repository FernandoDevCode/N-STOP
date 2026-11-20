package com.stash.core.data.share

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Takes a shared mix's link down after its owned playlist was deleted. The local row (and its
 * edit key) is gone by the time this runs, so both travel in the input data; queued work
 * survives being offline and app restarts.
 */
@HiltWorker
class SharedMixUnshareWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: ShareApiClient,
) : CoroutineWorker(appContext, params) {

    /** Retained for existing WorkManager records, but must never call the old server. */
    override suspend fun doWork(): Result = Result.success()

    companion object {
        private const val KEY_SHARE_ID = "share_id"
        private const val KEY_EDIT_KEY = "edit_key"

        fun enqueue(context: Context, shareId: String, editKey: String) {
            val work = OneTimeWorkRequestBuilder<SharedMixUnshareWorker>()
                .setInputData(workDataOf(KEY_SHARE_ID to shareId, KEY_EDIT_KEY to editKey))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("stash_shared_mix_unshare_$shareId", ExistingWorkPolicy.KEEP, work)
        }
    }
}

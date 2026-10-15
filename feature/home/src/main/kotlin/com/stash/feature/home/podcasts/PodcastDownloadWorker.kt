package com.stash.feature.home.podcasts

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/** Permanent podcast downloads live apart from music and from the evictable streaming cache. */
internal object PodcastFiles {
    private const val PREFS = "nonstop_podcasts"
    private val publicationLock = Any()
    fun workName(id: String) = "podcast_download_$id"
    private val acceptedExt = setOf("mp3", "m4a", "mp4", "ogg", "opus", "aac", "wav", "flac")

    fun extension(url: String): String {
        val ext = Uri.parse(url).path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext.takeIf { it in acceptedExt } ?: "mp3"
    }

    fun downloaded(context: Context, id: String): File? {
        if (!id.matches(Regex("[a-f0-9]{32}"))) return null
        val ext = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("download_$id", null) ?: return null
        if (ext !in acceptedExt) return null
        return File(context.filesDir, "podcasts/$id.$ext").takeIf { it.isFile && it.length() > 0 }
    }

    fun markDownloaded(context: Context, id: String, ext: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("download_$id", ext).apply()
    }

    fun remove(context: Context, id: String) = synchronized(publicationLock) {
        downloaded(context, id)?.delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("download_$id").commit()
    }

    fun commitIfRunning(
        context: Context, id: String, ext: String, part: File,
        canCommit: () -> Boolean,
    ): Boolean = synchronized(publicationLock) {
        if (!canCommit()) return@synchronized false
        val target = File(context.filesDir, "podcasts/$id.$ext")
        if (target.exists() && target.length() > 0L) {
            part.delete()
        } else if (!part.renameTo(target)) {
            return@synchronized false
        }
        markDownloaded(context, id, ext)
        true
    }

    fun enqueue(context: Context, episode: PodcastEpisode) {
        require(validPodcastUrl(episode.audioUrl))
        require(episode.id.matches(Regex("[a-f0-9]{32}")))
        val request = OneTimeWorkRequestBuilder<PodcastDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(
                PodcastDownloadWorker.KEY_ID to episode.id,
                PodcastDownloadWorker.KEY_URL to episode.audioUrl,
            ))
            .addTag("podcast_download")
            .addTag("podcast_id_${episode.id}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(episode.id), ExistingWorkPolicy.KEEP, request,
        )
    }
}

/**
 * Standalone WorkManager worker: doesn't touch the music queue or download DB.
 * Writes to a unique partial file and publishes it only after a complete transfer.
 */
class PodcastDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_ID = "episode_id"
        const val KEY_URL = "audio_url"
        private const val MAX_BYTES = 1024L * 1024 * 1024 // 1 GiB safety ceiling
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID).orEmpty()
        val url = inputData.getString(KEY_URL).orEmpty()
        if (!id.matches(Regex("[a-f0-9]{32}")) || !validPodcastUrl(url)) {
            return@withContext Result.failure()
        }
        if (PodcastFiles.downloaded(applicationContext, id) != null) return@withContext Result.success()
        val folder = File(applicationContext.filesDir, "podcasts")
        if (!folder.exists() && !folder.mkdirs()) return@withContext Result.failure()
        val ext = PodcastFiles.extension(url)
        val partial = File(folder, "$id.${UUID.randomUUID()}.part")
        val client = PodcastHttp.client
        try {
            client.newCall(Request.Builder().url(url)
                .header("User-Agent", "N0NStop-Podcasts/1.0")
                .build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure()
                val body = response.body ?: return@withContext Result.failure()
                val type = body.contentType()?.toString().orEmpty().lowercase()
                if (type.startsWith("text/") || type.contains("json") || type.contains("xml") ||
                    type.startsWith("video/")) return@withContext Result.failure()
                val total = body.contentLength()
                if (total > MAX_BYTES) return@withContext Result.failure()
                var bytes = 0L
                var lastPercentage = -1
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > MAX_BYTES) return@withContext Result.failure()
                            output.write(buffer, 0, count)
                            if (total > 0) {
                                val percent = (100L * bytes / total).toInt().coerceIn(0, 100)
                                if (percent >= lastPercentage + 5) {
                                    setProgress(workDataOf("percent" to percent))
                                    lastPercentage = percent
                                }
                            }
                        }
                    }
                }
                if (bytes < 1024 || (total >= 0 && total != bytes)) return@withContext Result.failure()
            }
            if (!PodcastFiles.commitIfRunning(applicationContext, id, ext, partial) { !isStopped }) {
                return@withContext Result.failure()
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            if (runAttemptCount < 3 && !isStopped) Result.retry() else Result.failure()
        } catch (_: Exception) {
            Result.failure()
        } finally {
            partial.delete()
        }
    }
}

package com.stash.feature.home.podcasts

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Separate media session: podcast positions and queues never enter the music
 * TrackDao, listening history, recommendations, or playback queue.
 *
 * ExoPlayer remains available with an Android media notification when the Home
 * screen is dismissed. The 300 MiB LRU is disposable; explicit downloads live
 * under filesDir/podcasts and are never evicted by this cache.
 */
@OptIn(UnstableApi::class)
class PodcastPlaybackService : MediaSessionService() {
    private lateinit var audio: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var cache: SimpleCache
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        cache = SimpleCache(
            File(cacheDir, "podcast_stream").also { it.mkdirs() },
            LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
            StandaloneDatabaseProvider(this),
        )
        val http = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(PodcastHttp.client)
            .setUserAgent("N0NStop-Podcasts/1.0")
        val sourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        audio = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sourceFactory))
            .build()
        audio.setAudioAttributes(AudioAttributes.DEFAULT, true)
        session = MediaSession.Builder(this, audio).build()
        scope.launch {
            while (isActive) {
                delay(3_000)
                savePosition()
            }
        }
    }

    private fun savePosition() {
        if (!::audio.isInitialized) return
        val id = audio.currentMediaItem?.mediaId?.takeIf { it.matches(Regex("[a-f0-9]{32}")) }
            ?: return
        if (audio.currentPosition <= 0 || audio.playbackState == Player.STATE_IDLE) return
        getSharedPreferences("nonstop_podcasts", Context.MODE_PRIVATE).edit()
            .putLong("position_$id", audio.currentPosition)
            .apply()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        savePosition()
        scope.cancel()
        session.release()
        audio.release()
        cache.release()
        super.onDestroy()
    }
}

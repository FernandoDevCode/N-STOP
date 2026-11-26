package com.stash.feature.library

import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.media.streaming.ConnectivityMonitor
import com.stash.core.model.Track
import com.stash.core.model.isUnavailableForDisplay
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Library membership is independent of playback eligibility. Sample the network
 * once per user action, never mutate a saved collection, and keep its order.
 * The player remains responsible for checking the actual file when opening it.
 */
class LibraryPlaybackPolicy @Inject constructor(
    private val connectivity: ConnectivityMonitor,
    private val streamingPreference: StreamingPreference,
) {
    suspend fun eligible(tracks: List<Track>): List<Track> {
        val canStream = connectivity.isConnected() &&
            (!connectivity.isCellular() || streamingPreference.streamOnCellular.first())
        return eligibleCollectionTracks(tracks, canStream)
    }
}

internal fun eligibleCollectionTracks(tracks: List<Track>, canStream: Boolean): List<Track> =
    tracks.filter { track ->
        (track.isDownloaded && !track.filePath.isNullOrBlank()) ||
            (canStream && !track.isUnavailableForDisplay)
    }

package com.stash.core.data.artists

import com.stash.data.ytmusic.model.AlbumSummary

/**
 * Recent, dated catalog releases from artists explicitly followed on this device.
 * An empty list is a legitimate safe fallback when identity or release date
 * cannot be verified. It must never be filled with invented recommendations.
 */
interface FollowedArtistReleasesRepository {
    suspend fun recentReleases(artists: List<FollowedArtist>): List<AlbumSummary>
}

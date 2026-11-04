package com.stash.feature.home

import com.stash.core.data.repository.MusicRepository
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.ArtistProfile
import com.stash.data.ytmusic.model.ArtistSummary
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HomeFeaturedReleaseLookupTest {
    @Test
    fun `when no recent release exists choose a real related artist album`() = runTest {
        val spotify = mock<SpotifyApiClient>()
        val youtube = mock<YTMusicApiClient>()
        val music = mock<MusicRepository>()
        whenever(spotify.getFollowedArtistNames(12)).thenReturn(listOf("Known Artist"))
        whenever(spotify.searchRecentArtistAlbums(any(), any())).thenReturn(emptyList())
        whenever(music.getMostPlayed(limit = 40)).thenReturn(flowOf(emptyList()))
        whenever(youtube.resolveArtist("Known Artist")).thenReturn(
            ArtistSummary("known-id", "Known Artist", null),
        )
        whenever(youtube.getArtist("known-id")).thenReturn(
            profile("known-id", "Known Artist", related = listOf(
                ArtistSummary("related-id", "Related Artist", null),
            )),
        )
        whenever(youtube.getArtist("related-id")).thenReturn(
            profile("related-id", "Related Artist", albums = listOf(
                AlbumSummary("album-id", "Real album", "Related Artist", "https://image.example/cover", "2024"),
            )),
        )

        val result = HomeFeaturedReleaseLookup(spotify, youtube, music).find()

        assertNotNull(result)
        assertEquals("Real album", result?.album?.title)
        assertEquals("Related Artist", result?.album?.artist)
        assertEquals(HomeFeaturedRelease.Source.DISCOVERY_YOUTUBE_MUSIC, result?.source)
        assertEquals(false, result?.isFollowedArtist)
    }

    private fun profile(
        id: String,
        name: String,
        related: List<ArtistSummary> = emptyList(),
        albums: List<AlbumSummary> = emptyList(),
    ) = ArtistProfile(
        id = id,
        name = name,
        avatarUrl = null,
        subscribersText = null,
        popular = emptyList(),
        albums = albums,
        singles = emptyList(),
        related = related,
    )
}

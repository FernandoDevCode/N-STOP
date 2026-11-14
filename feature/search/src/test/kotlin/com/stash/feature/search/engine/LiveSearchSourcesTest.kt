package com.stash.feature.search.engine

import com.stash.feature.search.contract.CatalogSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.ytmusic.YouTubeWebSearchClient

class LiveSearchSourcesTest {
    @Test fun `YTM catalog is not queried by Spotify and YouTube search`() = runTest {
        val sources = LiveSearchSources(
            spotify = mock<SpotifyApiClient>(),
            youtube = mock<YouTubeWebSearchClient>(),
        )
        try {
            sources.search("daydream", CatalogSource.YOUTUBE_MUSIC)
            fail("YTM must not be a selectable Search provider")
        } catch (expected: IllegalArgumentException) {
            assertEquals("YouTube Music is not a Search tab", expected.message)
        }
    }
}

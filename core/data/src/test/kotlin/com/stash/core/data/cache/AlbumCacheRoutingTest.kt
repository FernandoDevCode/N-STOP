package com.stash.core.data.cache

import com.stash.core.data.discography.QobuzAlbumFetcher
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.AlbumDetail
import com.stash.data.ytmusic.model.AlbumSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

/** Only YouTube album identities can fetch remotely in Sonora. */
class AlbumCacheRoutingTest {
    private fun detail(id: String, title: String) = AlbumDetail(
        id = id, title = title, artist = "A", artistId = null,
        thumbnailUrl = null, year = null, tracks = emptyList(), moreByArtist = emptyList(),
    )

    @Test
    fun `YouTube albums use the YouTube API and never touch the legacy fetcher`() = runTest {
        val api = mock<YTMusicApiClient>()
        val fetcher = mock<QobuzAlbumFetcher>()
        whenever(api.getAlbum(eq("MPRE1"))).thenReturn(detail("MPRE1", "YT Album"))
        val result = AlbumCache(api, fetcher).get("MPRE1", AlbumSource.YOUTUBE)
        assertEquals("YT Album", result.title)
        verify(api).getAlbum(eq("MPRE1"))
        verifyNoInteractions(fetcher)
    }

    @Test
    fun `old Qobuz album identifiers cannot trigger external music requests`() = runTest {
        val api = mock<YTMusicApiClient>()
        val fetcher = mock<QobuzAlbumFetcher>()
        val result = runCatching { AlbumCache(api, fetcher).get("123", AlbumSource.QOBUZ) }
        assertTrue(result.exceptionOrNull() is IOException)
        verifyNoInteractions(fetcher)
        verifyNoInteractions(api)
    }

    @Test
    fun `old Qobuz playlist identifiers cannot trigger external music requests`() = runTest {
        val api = mock<YTMusicApiClient>()
        val fetcher = mock<QobuzAlbumFetcher>()
        val result = runCatching { AlbumCache(api, fetcher).get("123", AlbumSource.QOBUZ_PLAYLIST) }
        assertTrue(result.exceptionOrNull() is IOException)
        verifyNoInteractions(fetcher)
        verifyNoInteractions(api)
    }

    @Test
    fun `failed legacy album does not poison a YouTube album with the same id`() = runTest {
        val api = mock<YTMusicApiClient>()
        val fetcher = mock<QobuzAlbumFetcher>()
        whenever(api.getAlbum(eq("42"))).thenReturn(detail("42", "YT album"))
        val cache = AlbumCache(api, fetcher)
        assertTrue(runCatching { cache.get("42", AlbumSource.QOBUZ) }.exceptionOrNull() is IOException)
        assertEquals("YT album", cache.get("42", AlbumSource.YOUTUBE).title)
        verify(api).getAlbum(eq("42"))
        verifyNoInteractions(fetcher)
    }
}

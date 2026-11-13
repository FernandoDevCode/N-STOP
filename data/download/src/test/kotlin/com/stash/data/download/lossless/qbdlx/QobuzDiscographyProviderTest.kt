package com.stash.data.download.lossless.qbdlx

import com.google.common.truth.Truth.assertThat
import com.stash.data.ytmusic.model.AlbumSource
import com.stash.data.ytmusic.model.AlbumSummary
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Qobuz catalog augmentation is disabled. Historical identifiers remain
 * stored, but no API calls or Qobuz-only albums can be introduced. */
class QobuzDiscographyProviderTest {
    private val apiClient: QbdlxApiClient = mockk()
    private val provider = QobuzDiscographyProvider(apiClient)

    private fun ytAlbum(title: String) =
        AlbumSummary("yt_$title", title, "Artist", null, "2026", AlbumSource.YOUTUBE)

    private fun assertNoQobuzRequests() {
        coVerify(exactly = 0) { apiClient.searchArtists(any(), any()) }
        coVerify(exactly = 0) { apiClient.getArtistAlbums(any(), any()) }
    }

    @Test
    fun `existing YouTube albums and singles retain their original order`() = runTest {
        val albums = listOf(ytAlbum("Album B"), ytAlbum("Album A"))
        val singles = listOf(ytAlbum("Single"))
        val merged = provider.mergeInto("Artist", albums, singles)
        assertThat(merged.albums).containsExactlyElementsIn(albums).inOrder()
        assertThat(merged.singles).containsExactlyElementsIn(singles).inOrder()
        assertNoQobuzRequests()
    }

    @Test
    fun `empty YouTube catalog never triggers Qobuz augmentation`() = runTest {
        val merged = provider.mergeInto("Artist", emptyList(), emptyList())
        assertThat(merged.albums).isEmpty()
        assertThat(merged.singles).isEmpty()
        assertNoQobuzRequests()
    }

    @Test
    fun `artist homonyms and blank names never bypass source restriction`() = runTest {
        val albums = listOf(ytAlbum("Loveless"))
        for (artist in listOf("", "Various Artists", "My Bloody Valentine")) {
            val merged = provider.mergeInto(artist, albums, emptyList())
            assertThat(merged.albums).containsExactlyElementsIn(albums)
        }
        assertNoQobuzRequests()
    }
}

package com.stash.feature.search.engine

import com.stash.data.spotify.SpotifyCatalogItem
import com.stash.data.ytmusic.YTMusicCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifySearchTargetTest {
    @Test
    fun `a track resolves to one supported recording with matching credits and edition`() {
        val spotify = item(SpotifyCatalogItem.Kind.TRACK).toUniversalSearchResult()
        val recording = candidate(YTMusicCatalogItem.Kind.TRACK)
        val alternatives = listOf(
            recording.copy(id = "live", title = "A Song (Live)"),
            recording.copy(id = "cover", subtitle = "Cover Artist"),
            recording.copy(id = "video", kind = YTMusicCatalogItem.Kind.VIDEO),
            recording.copy(id = "edit", durationMs = 246_000),
            recording.copy(id = "unverified", releaseTitle = null),
            recording.copy(id = "clean", isExplicit = false),
        )
        assertEquals(recording, spotifySearchTarget(spotify, alternatives + recording + recording))
        assertNull(spotifySearchTarget(spotify, alternatives))
        assertNull(spotifySearchTarget(spotify, listOf(recording, recording.copy(id = "other"))))
    }

    @Test
    fun `album navigation distinguishes a same-title single and release year`() {
        val spotify = item(SpotifyCatalogItem.Kind.ALBUM)
            .copy(title = "Album", albumTitle = "Album", releaseType = "single").toUniversalSearchResult()
        val single = candidate(YTMusicCatalogItem.Kind.ALBUM).copy(title = "Album", releaseType = "Single")
        val album = single.copy(id = "different-edition", releaseType = "Album")
        val reissue = single.copy(id = "reissue", releaseYear = "2020")
        assertEquals(single, spotifySearchTarget(spotify, listOf(album, reissue, single)))
        assertNull(spotifySearchTarget(spotify, listOf(album, reissue)))
    }

    @Test
    fun `artist resolution rejects exact-name homonyms and similar names`() {
        val artist = item(SpotifyCatalogItem.Kind.ARTIST).copy(title = "João").toUniversalSearchResult()
        val exact = candidate(YTMusicCatalogItem.Kind.ARTIST).copy(title = "JOAO")
        val similar = exact.copy(id = "similar", title = "João Gilberto")
        assertEquals(exact, spotifySearchTarget(artist, listOf(similar, exact)))
        assertNull(spotifySearchTarget(artist, listOf(similar, exact, exact.copy(id = "homonym"))))
    }

    private fun item(kind: SpotifyCatalogItem.Kind) = SpotifyCatalogItem(
        kind, "spotify-id", "spotify:track:spotify-id", "A Song", "Lead, Guest", null,
        242_120, true, 0, albumTitle = "Album", releaseYear = "2011", releaseType = "album",
    )

    private fun candidate(kind: YTMusicCatalogItem.Kind) = YTMusicCatalogItem(
        kind, "ABCDEFGHIJK", "A Song", "Lead, Guest", null, 242_000, true, 0,
        releaseTitle = "Album", releaseYear = "2011", releaseType = "album",
    )
}

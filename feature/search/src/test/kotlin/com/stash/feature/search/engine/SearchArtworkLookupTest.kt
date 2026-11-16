package com.stash.feature.search.engine

import com.stash.data.spotify.SpotifyApiException
import com.stash.data.spotify.SpotifyCatalogItem
import com.stash.data.spotify.SpotifyCatalogItem.Kind
import com.stash.data.spotify.SpotifyRateLimitException
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchReleaseMetadata
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchArtworkLookupTest {
    @Test
    fun `thumbnail fallback is tied to the exact video id`() {
        val original = "https://i.ytimg.com/custom"
        assertEquals(original, videoArtwork("UGCdemoAB12", original))
        assertEquals(
            com.stash.core.common.ArtUrlUpgrader.youTubeThumbnail("UGCdemoAB12"),
            videoArtwork("UGCdemoAB12", null),
        )
        assertNull(videoArtwork("unverified-id", null))
    }

    @Test
    fun `track uses full title credits release and duration`() {
        assertEquals(cover, match(track, song))
        assertNull(match(track, song.copy(durationMs = 10_000)))
        assertNull(match(track, song.copy(artists = "Cover band")))
        assertNull(match(track, song.copy(albumTitle = "Record (Deluxe Edition)")))
        assertNull(match(track, song.copy(isExplicit = true)))
        assertNull(match(track, song.copy(artworkUrl = "https://other.test/image")))
        for (suffix in listOf("Live", "Cover", "Remix", "2011 Remaster")) {
            val alternate = track.copy(title = "Track ($suffix)")
            assertNull(match(alternate, song))
            assertEquals(cover, match(alternate, song.copy(title = alternate.title)))
        }
    }

    @Test
    fun `release covers distinguish deluxe editions and require year and type`() {
        val album = track.copy(
            type = SearchResultType.ALBUM,
            title = "Record (Deluxe)",
            release = SearchReleaseMetadata("Record (Deluxe)", "2010", "Album"),
        )
        val edition = song.copy(kind = Kind.ALBUM, title = album.title, releaseYear = "2010", releaseType = "album")
        assertEquals(cover, matchingSpotifyArtwork(album, Kind.ALBUM, listOf(edition), null))
        for (wrong in listOf(
            edition.copy(title = "Record"),
            edition.copy(releaseYear = "1984"),
            edition.copy(releaseType = "single"),
        )) {
            assertNull(matchingSpotifyArtwork(album, Kind.ALBUM, listOf(wrong), null))
        }
        assertNull(album.copy(release = album.release?.copy(year = null)).artworkKind())
    }

    @Test
    fun `homonyms and duplicate editions are ambiguous unless identity is verified`() {
        val homonym = song.copy(id = "another", artistIds = listOf("other-artist"))
        assertNull(matchingSpotifyArtwork(track, Kind.TRACK, listOf(song, homonym), null))
        assertEquals(cover, matchingSpotifyArtwork(track, Kind.TRACK, listOf(song, homonym), "verified-artist"))
        assertNull(matchingSpotifyArtwork(track, Kind.TRACK, listOf(homonym), "verified-artist"))
        assertNull(matchingSpotifyArtwork(track, Kind.TRACK, listOf(song, song.copy(id = "duplicate")), null))
    }

    @Test
    fun `cache hits and misses expire and Spotify errors never replace original art`() = runTest {
        var now = 0L
        var calls = 0
        val lookup = SearchArtworkLookup(search = { _, kind ->
            assertEquals(Kind.TRACK, kind)
            if (++calls == 1) listOf(song) else emptyList()
        }, now = { now })
        assertEquals(cover, lookup.find(track))
        assertEquals(cover, lookup.find(track))
        assertEquals(1, calls)
        now = 24 * 60 * 60_000L
        assertNull(lookup.find(track))
        assertNull(lookup.find(track))
        assertEquals(2, calls)
        now += 30 * 60_000L
        assertNull(lookup.find(track))
        assertEquals(3, calls)
    }

    @Test
    fun `unavailable Spotify backs off globally and videos keep catalogue thumbnails`() = runTest {
        var now = 0L
        var calls = 0
        val lookup = SearchArtworkLookup(search = { _, _ ->
            when (++calls) {
                1 -> throw SpotifyApiException(0, "search", "No account")
                2 -> throw SpotifyRateLimitException(120)
                else -> listOf(song)
            }
        }, now = { now })
        assertNull(lookup.find(track.copy(type = SearchResultType.VIDEO)))
        assertEquals(0, calls)
        assertNull(lookup.find(track))
        assertNull(lookup.find(track.copy(identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, "another"))))
        assertEquals(1, calls)
        now += 60_000
        assertNull(lookup.find(track))
        now += 119_000
        assertNull(lookup.find(track))
        assertEquals(2, calls)
        now += 1_000
        assertEquals(cover, lookup.find(track))
        assertEquals("original", track.artworkUrl)
    }

    private fun match(item: UniversalSearchResult, candidate: SpotifyCatalogItem) =
        matchingSpotifyArtwork(item, Kind.TRACK, listOf(candidate), null)

    private val cover = "https://i.scdn.co/image/verified"
    private val track = UniversalSearchResult(
        identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, "actual-id"),
        type = SearchResultType.TRACK,
        title = "Track", subtitle = "Artist", artworkUrl = "original", durationMs = 180_000,
        providerRank = 0, rankingScore = 1.0,
        release = SearchReleaseMetadata("Record"),
    )
    private val song = SpotifyCatalogItem(
        Kind.TRACK, "candidate", null, "Track", "Artist", cover, 180_400, false, 0,
        artistIds = listOf("verified-artist"), albumTitle = "Record",
    )
}

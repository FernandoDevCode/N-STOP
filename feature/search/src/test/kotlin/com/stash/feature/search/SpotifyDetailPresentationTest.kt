package com.stash.feature.search

import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyDetailPresentationTest {
    private fun item(
        source: CatalogSource = CatalogSource.SPOTIFY,
        type: SearchResultType = SearchResultType.ALBUM,
        id: String = "AAAAAAAAAAAAAAAAAAAAAA",
        kind: String? = null,
    ) = UniversalSearchResult(
        identity = CatalogIdentity(source, id),
        type = type,
        title = "Release",
        releaseKind = kind,
        providerRank = 0,
        rankingScore = 1.0,
    )

    @Test fun `single and EP releases are not mislabeled as albums`() {
        assertTrue(item(kind = "single").isSingleOrEp())
        assertTrue(item(kind = "SINGLE").isSingleOrEp())
        assertTrue(item(kind = "ep").isSingleOrEp())
        assertFalse(item(kind = "album").isSingleOrEp())
        assertFalse(item(kind = "compilation").isSingleOrEp())
        assertFalse(item(kind = null).isSingleOrEp())
    }

    @Test fun `artist album and track links retain original Spotify identities`() {
        assertEquals(
            "https://open.spotify.com/artist/AAAAAAAAAAAAAAAAAAAAAA",
            item(type = SearchResultType.ARTIST).spotifyUrl(),
        )
        assertEquals(
            "https://open.spotify.com/album/AAAAAAAAAAAAAAAAAAAAAA",
            item(type = SearchResultType.ALBUM).spotifyUrl(),
        )
        assertEquals(
            "https://open.spotify.com/track/AAAAAAAAAAAAAAAAAAAAAA",
            item(type = SearchResultType.TRACK).spotifyUrl(),
        )
    }

    @Test fun `never synthesize Spotify links for YouTube videos or invalid IDs`() {
        assertNull(item(source = CatalogSource.YOUTUBE).spotifyUrl())
        assertNull(item(id = "not-a-spotify-id").spotifyUrl())
        assertNull(item(type = SearchResultType.VIDEO).spotifyUrl())
    }
}

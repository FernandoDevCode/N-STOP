package com.stash.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyCatalogSearchTest {
    @Test fun `tracks artists albums retain distinct Spotify identities and covers`() {
        val response = """
            {
              "tracks": {
                "next": "https://api.spotify.com/v1/search?offset=10",
                "items": [
                  {
                    "id": "trackA", "name": "Daydream", "duration_ms": 182000,
                    "explicit": false,
                    "artists": [{"name":"Example Choir"}],
                    "album": {"name":"Daydream Best Of", "images":[{"url":"https://img.test/track.jpg"}]}
                  }
                ]
              },
              "artists": {"next":null,"items":[{"id":"artistA","name":"Daydream","images":[{"url":"https://img.test/artist.jpg"}]}]},
              "albums": {"next":null,"items":[{"id":"albumA","name":"Daydream","artists":[{"name":"Example"}],"images":[{"url":"https://img.test/album.jpg"}]}]}
            }
        """.trimIndent()
        val page = parseSpotifyCatalogPage(response)
        assertTrue(page.hasMore)
        assertEquals(listOf(SpotifyCatalogKind.TRACK, SpotifyCatalogKind.ARTIST, SpotifyCatalogKind.ALBUM),
            page.items.map { it.kind })
        assertEquals(listOf("trackA", "artistA", "albumA"), page.items.map { it.id })
        assertEquals("https://img.test/track.jpg", page.items[0].artworkUrl)
        assertEquals(182000L, page.items[0].durationMs)
    }

    @Test fun `invalid entries are discarded without mixing result kinds`() {
        val page = parseSpotifyCatalogPage("""
          {"tracks":{"next":null,"items":[{"name":"no id"},{"id":"valid","name":"Track"},{"id":"valid","name":"Track"}]},
           "artists":{"items":[]},"albums":{"items":[]}}
        """.trimIndent())
        assertFalse(page.hasMore)
        assertEquals(1, page.items.size)
        assertEquals(SpotifyCatalogKind.TRACK, page.items.single().kind)
        assertEquals("valid", page.items.single().id)
    }
    @Test fun `artist discography retains separate regular and anniversary IDs`() {
        val page = parseSpotifyArtistAlbumsPage("""
          {"next":"https://api.spotify.com/v1/artists/artist/albums?offset=10",
           "items":[
             {"id":"regular","name":"Slipknot","release_date":"1999-06-29","album_group":"album","artists":[{"name":"Slipknot"}]},
             {"id":"anniversary","name":"Slipknot (25th Anniversary Edition)","release_date":"2024-06-29","album_group":"album","artists":[{"name":"Slipknot"}]}
           ]}
        """.trimIndent())
        assertTrue(page.hasMore)
        assertEquals(listOf("regular","anniversary"), page.items.map { it.id })
        assertEquals("2024-06-29", page.items.last().releaseDate)
        assertEquals("album", page.items.last().releaseGroup)
    }

    @Test fun `album track pagination preserves Spotify track IDs`() {
        val page = parseSpotifyAlbumTracksPage("""
          {"next":null,"items":[
            {"id":"spotifytrack1","name":"Track 1","artists":[{"name":"Slipknot"}],"duration_ms":152000}
          ]}
        """.trimIndent())
        assertFalse(page.hasMore)
        assertEquals(SpotifyCatalogKind.TRACK, page.items.single().kind)
        assertEquals("spotifytrack1", page.items.single().id)
        assertEquals("Slipknot", page.items.single().subtitle)
    }

}

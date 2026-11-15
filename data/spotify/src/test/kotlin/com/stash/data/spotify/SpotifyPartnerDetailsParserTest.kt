package com.stash.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPartnerDetailsParserTest {
    private fun obj(json: String) = Json.parseToJsonElement(json.trimIndent()).jsonObject

    @Test fun artistDiscographyPreservesDistinctRegularAndAnniversaryReleases() {
        val page = parseSpotifyPartnerArtistReleasesPage(obj("""
          {"data":{"artistUnion":{"discography":{"all":{
            "totalCount":4,
            "items":[{"releases":{"items":[
              {"uri":"spotify:album:AAAAAAAAAAAAAAAAAAAAAA","name":"Slipknot",
                "type":"ALBUM","date":{"isoString":"1999-06-29T00:00:00Z"},
                "artists":{"items":[{"profile":{"name":"Slipknot"}}]},
                "coverArt":{"sources":[{"url":"https://img.test/regular.jpg"}]}},
              {"uri":"spotify:album:BBBBBBBBBBBBBBBBBBBBBB",
                "name":"Slipknot (25th Anniversary Edition)","type":"ALBUM",
                "date":{"isoString":"2024-06-29T00:00:00Z"},
                "artists":{"items":[{"profile":{"name":"Slipknot"}}]},
                "coverArt":{"sources":[{"url":"https://img.test/anniversary.jpg"}]}}
            ]}}]
          }}}}}
        """), 0, 10)
        requireNotNull(page)
        assertEquals(2, page.items.size)
        assertEquals(listOf("AAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBBBBBBBBBB"),
            page.items.map { it.id })
        assertEquals(listOf("Slipknot", "Slipknot (25th Anniversary Edition)"),
            page.items.map { it.title })
        assertEquals("2024-06-29", page.items[1].releaseDate)
        assertEquals("Slipknot", page.items[1].subtitle)
        assertEquals("https://img.test/anniversary.jpg", page.items[1].artworkUrl)
        assertTrue(page.hasMore)
    }

    @Test fun artistDiscographyGroupsDetermineCursorNotExpandedReleases() {
        val page = parseSpotifyPartnerArtistReleasesPage(obj("""
            {"data":{"artistUnion":{"discography":{"all":{"totalCount":1,
              "items":[{"releases":{"items":[
                {"uri":"spotify:album:AAAAAAAAAAAAAAAAAAAAAA","name":"Original"},
                {"uri":"spotify:album:BBBBBBBBBBBBBBBBBBBBBB","name":"Deluxe"},
                {"uri":"spotify:album:BBBBBBBBBBBBBBBBBBBBBB","name":"Deluxe"}
              ]}}]}}}}}
        """), 0, 10)
        requireNotNull(page)
        assertFalse(page.hasMore)
        assertEquals(2, page.items.size)
    }

    @Test fun albumTracksKeepSpotifyTrackIdAndMusicMetadata() {
        val page = parseSpotifyPartnerAlbumTracksPage(obj("""
          {"data":{"albumUnion":{
            "artists":{"items":[{"profile":{"name":"Slipknot"}}]},
            "coverArt":{"sources":[{"url":"https://img.test/album.jpg"}]},
            "tracksV2":{"totalCount":4,"items":[
              {"track":{"uri":"spotify:track:CCCCCCCCCCCCCCCCCCCCCC",
                "name":"(sic)","duration":{"totalMilliseconds":199000},
                "artists":{"items":[{"profile":{"name":"Slipknot"}}]}}},
              {"track":{"uri":"spotify:track:DDDDDDDDDDDDDDDDDDDDDD",
                "name":"Eyeless","duration":{"totalMilliseconds":236000}}},
              {"track":{"uri":"spotify:album:EEEEEEEEEEEEEEEEEEEEEE",
                "name":"Invalid mixed ID"}}
            ]}
          }}}
        """), 0, 10)
        requireNotNull(page)
        assertEquals(listOf("CCCCCCCCCCCCCCCCCCCCCC", "DDDDDDDDDDDDDDDDDDDDDD"),
            page.items.map { it.id })
        assertEquals("Slipknot", page.items[1].subtitle)
        assertEquals(236000L, page.items[1].durationMs)
        assertEquals("https://img.test/album.jpg", page.items[1].artworkUrl)
        assertTrue(page.hasMore)
    }

    @Test fun schemaErrorsAreNotReportedAsEmptyDiscographies() {
        assertNull(parseSpotifyPartnerArtistReleasesPage(obj("""{"data":{"artistUnion":{}}}"""), 0, 10))
        assertNull(parseSpotifyPartnerAlbumTracksPage(obj("""{"data":{"albumUnion":{}}}"""), 0, 10))
        assertNull(parseSpotifyPartnerArtistReleasesPage(obj(
            """{"errors":[{"message":"PersistedQueryNotFound"}],"data":{"artistUnion":{"discography":{"all":{"items":[]}}}}}"""),
            0, 10))
        val empty = parseSpotifyPartnerAlbumTracksPage(obj(
            """{"data":{"albumUnion":{"tracksV2":{"items":[],"totalCount":0}}}}"""), 0, 10)
        requireNotNull(empty)
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasMore)
    }
}

package com.stash.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPartnerSearchParserTest {
    @Test fun `web-player search returns only real Spotify IDs across three buckets`() {
        val response = Json.parseToJsonElement("""
            {"data":{"search":{
              "tracks":{"totalCount":25,"items":[
                {"track":{"uri":"spotify:track:aaa","name":"Daydream",
                   "artists":{"items":[{"profile":{"name":"I Monster"}}]},
                   "duration":{"totalMilliseconds":190000},
                   "albumOfTrack":{"coverArt":{"sources":[{"url":"https://example.org/track.jpg"}]}}
                }}
              ]},
              "artists":{"totalCount":1,"items":[
                {"data":{"uri":"spotify:artist:bbb",
                  "profile":{"name":"Slipknot"},
                  "visuals":{"avatarImage":{"sources":[{"url":"https://example.org/artist.jpg"}]}}}}
              ]},
              "albums":{"totalCount":1,"items":[
                {"data":{"uri":"spotify:album:ccc","name":"Anniversary",
                  "artists":{"items":[{"profile":{"name":"Slipknot"}}]},
                  "coverArt":{"sources":[{"url":"https://example.org/album.jpg"}]}}}
              ]}
            }}}
        """.trimIndent()).jsonObject
        val page = parseSpotifyPartnerSearchPage(response, offset = 0, limit = 10)
        requireNotNull(page)
        assertEquals(listOf(SpotifyCatalogKind.TRACK, SpotifyCatalogKind.ARTIST,
            SpotifyCatalogKind.ALBUM), page.items.map { it.kind })
        assertEquals(listOf("aaa", "bbb", "ccc"), page.items.map { it.id })
        assertEquals("I Monster", page.items[0].subtitle)
        assertEquals(190000L, page.items[0].durationMs)
        assertEquals("https://example.org/track.jpg", page.items[0].artworkUrl)
        assertEquals("https://example.org/artist.jpg", page.items[1].artworkUrl)
        assertEquals("https://example.org/album.jpg", page.items[2].artworkUrl)
        assertTrue("pagination must preserve the Spotify reported total", page.hasMore)
    }

    @Test fun `searchV2 variant uses nested item data without changing source identity`() {
        val root = Json.parseToJsonElement("""
            {"data":{"searchV2":{
              "tracksV2":{"items":[{"item":{"data":{
                "uri":"spotify:track:track2","name":"Song Two",
                "duration":{"totalMilliseconds":155000}
              }}}]},
              "artistsV2":{"items":[]},
              "albumsV2":{"items":[]}
            }}}
        """.trimIndent()).jsonObject
        val page = parseSpotifyPartnerSearchPage(root, offset = 0, limit = 10)
        requireNotNull(page)
        assertEquals(1, page.items.size)
        assertEquals("track2", page.items.single().id)
        assertFalse(page.hasMore)
    }

    @Test fun `invalid schema is an error not a legitimate empty search`() {
        assertNull(parseSpotifyPartnerSearchPage(
            Json.parseToJsonElement("""{"errors":[{"message":"bad hash"}]}""").jsonObject,
            offset = 0, limit = 10))
        assertNull(parseSpotifyPartnerSearchPage(
            Json.parseToJsonElement("""{"data":{"search":{"topResults":{}}}}""").jsonObject,
            offset = 0, limit = 10))
        val empty = parseSpotifyPartnerSearchPage(
            Json.parseToJsonElement("""{"data":{"search":{"tracks":{"items":[]},"artists":{"items":[]},"albums":{"items":[]}}}}""").jsonObject,
            offset = 0, limit = 10)
        requireNotNull(empty)
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasMore)
    }

    @Test fun `unknown unrelated types never borrow an artist or track identity`() {
        val root = Json.parseToJsonElement("""
            {"data":{"search":{
              "tracks":{"items":[{"track":{"uri":"spotify:album:wrong","name":"Album"}}]},
              "artists":{"items":[{"data":{"uri":"spotify:track:wrong","name":"Song"}}]},
              "albums":{"items":[]}
            }}}
        """.trimIndent()).jsonObject
        val page = parseSpotifyPartnerSearchPage(root, 0, 10)
        requireNotNull(page)
        assertTrue(page.items.isEmpty())
    }
}

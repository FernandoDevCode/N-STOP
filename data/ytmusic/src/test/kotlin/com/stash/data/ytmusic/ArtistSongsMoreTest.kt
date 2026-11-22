package com.stash.data.ytmusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistSongsMoreTest {
    private fun obj(input: String) = Json.parseToJsonElement(input).jsonObject

    @Test fun `artist song shelf browse ID points to full YTM playlist`() {
        val shelf = obj("""{"title":{"runs":[{
          "text":"Songs","navigationEndpoint":{"browseEndpoint":{"browseId":"VLOLAK5uy_abc"}}
        }]}}""")
        assertEquals("VLOLAK5uy_abc", parseArtistSongsMore(shelf))
    }

    @Test fun `artist song shelf without playlist never invents a browse ID`() {
        assertNull(parseArtistSongsMore(obj("""{"title":{"runs":[{"text":"Songs"}]}}""")))
        assertNull(parseArtistSongsMore(obj("""{"title":{"runs":[{
          "navigationEndpoint":{"browseEndpoint":{"browseId":"UC123"}}
        }]}}""")))
    }
}

package com.stash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtistWordmarkLookupTest {
    private val mbid = "cc197bad-dc9c-440d-a5b5-d52ba2e14234"

    @Test fun `artist names must match exactly and case insensitively`() {
        assertTrue(ArtistWordmarkLookup.matchingArtist("Megadeth", "MEGADETH"))
        assertTrue(ArtistWordmarkLookup.matchingArtist("  U2", "U2  "))
        assertFalse(ArtistWordmarkLookup.matchingArtist("Megadeth", "Megadeth Tribute"))
        assertFalse(ArtistWordmarkLookup.matchingArtist("The Weeknd", "Weeknd"))
        assertFalse(ArtistWordmarkLookup.matchingArtist("U2", "U2 Cover Band"))
    }

    @Test fun `AudioDB returns only the exact artist and logo image`() {
        val json = """
          {"artists":[
            {"strArtist":"Our Divide Tribute","strArtistLogo":"https://www.theaudiodb.com/images/media/artist/logo/wrong.png"},
            {"strArtist":"Our Divide","strMusicBrainzID":"$mbid",
             "strArtistLogo":"https://www.theaudiodb.com/images/media/artist/logo/real.png"}
          ]}
        """.trimIndent()
        val resolved = ArtistWordmarkLookup.parseAudioDb(json, "our divide")
        assertEquals(mbid, resolved?.mbid)
        assertEquals("https://www.theaudiodb.com/images/media/artist/logo/real.png", resolved?.logoUrl)
    }

    @Test fun `AudioDB rejects photo URLs and untrusted hosts`() {
        for (url in listOf(
            "https://www.theaudiodb.com/images/media/artist/fanart/cover.png",
            "https://www.theaudiodb.com.evil.example/images/media/artist/logo/a.png",
            "http://www.theaudiodb.com/images/media/artist/logo/a.png",
            "https://www.theaudiodb.com/images/media/artist/logo/a.jpg",
        )) {
            val result = ArtistWordmarkLookup.parseAudioDb(
                """{"artists":[{"strArtist":"Coldplay","strArtistLogo":"$url"}]}""",
                "Coldplay",
            )
            assertNull("Must reject $url", result?.logoUrl)
        }
    }

    @Test fun `Fanart chooses highest-rated HD logo before standard image`() {
        val json = """
          {"name":"Coldplay","mbid_id":"$mbid",
           "hdmusiclogo":[
             {"url":"https://assets.fanart.tv/fanart/music/$mbid/hdmusiclogo/a.png","likes":"3"},
             {"url":"https://assets.fanart.tv/fanart/music/$mbid/hdmusiclogo/b.png","likes":"27"}
           ],
           "musiclogo":[
             {"url":"https://assets.fanart.tv/fanart/music/$mbid/musiclogo/c.png","likes":"100"}
           ]}
        """.trimIndent()
        assertEquals(
            "https://assets.fanart.tv/fanart/music/$mbid/hdmusiclogo/b.png",
            ArtistWordmarkLookup.parseFanartLogo(json, "Coldplay", mbid),
        )
    }

    @Test fun `Fanart falls back to standard logo and rejects other artists`() {
        val json = """
          {"name":"Coldplay","mbid_id":"$mbid",
            "musiclogo":[{"url":"https://assets.fanart.tv/fanart/music/$mbid/musiclogo/logo.png","likes":"5"}]}
        """.trimIndent()
        assertEquals(
            "https://assets.fanart.tv/fanart/music/$mbid/musiclogo/logo.png",
            ArtistWordmarkLookup.parseFanartLogo(json, "Coldplay", mbid),
        )
        // Once the caller has resolved an unambiguous MusicBrainz ID, the
        // Fanart display name can differ without changing artist identity.
        assertEquals(
            "https://assets.fanart.tv/fanart/music/$mbid/musiclogo/logo.png",
            ArtistWordmarkLookup.parseFanartLogo(json, "Coldplay (UK)", mbid),
        )
        assertNull(ArtistWordmarkLookup.parseFanartLogo(json, "Coldplay", "00000000-0000-0000-0000-000000000000"))
    }

    @Test fun `Fanart rejects impersonated CDN and artist photos`() {
        val json = """
          {"name":"Coldplay","mbid_id":"$mbid",
           "hdmusiclogo":[
             {"url":"https://assets.fanart.tv.attacker.com/fanart/music/$mbid/hdmusiclogo/a.png","likes":"999"},
             {"url":"https://assets.fanart.tv/fanart/music/$mbid/artistbackground/a.png","likes":"999"},
             {"url":"https://assets.fanart.tv/fanart/music/$mbid/hdmusiclogo/legit.png","likes":"2"}
           ]}
        """.trimIndent()
        assertEquals(
            "https://assets.fanart.tv/fanart/music/$mbid/hdmusiclogo/legit.png",
            ArtistWordmarkLookup.parseFanartLogo(json, "Coldplay", mbid),
        )
    }

    @Test fun `MusicBrainz name collision is not resolved by guessing`() {
        val artists = """
          {"artists":[
            {"id":"$mbid","name":"Our Divide"},
            {"id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","name":"Our Divide"}
          ]}
        """.trimIndent()
        assertNull(ArtistWordmarkLookup.parseMusicBrainzId(artists, "Our Divide"))
        val unique = """
          {"artists":[
            {"id":"$mbid","name":"Our Divide"},
            {"id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","name":"Our Divide Tribute"}
          ]}
        """.trimIndent()
        assertEquals(mbid, ArtistWordmarkLookup.parseMusicBrainzId(unique, "Our Divide"))
    }
}

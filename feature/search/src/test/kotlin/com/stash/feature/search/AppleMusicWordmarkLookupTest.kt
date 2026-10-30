package com.stash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppleMusicWordmarkLookupTest {
    private val apple = AppleMusicWordmarkLookup

    @Test fun `official search resolves only exact artist name and matching Apple ID`() {
        val raw = """
            {"resultCount":2,"results":[
              {"wrapperType":"artist","artistName":"Our Divide Tribute",
               "artistId":222,"artistLinkUrl":"https://music.apple.com/us/artist/a/222"},
              {"wrapperType":"artist","artistName":"Our Divide","artistId":1605542240,
               "artistLinkUrl":"https://music.apple.com/us/artist/our-divide/1605542240?uo=4"}
            ]}
        """.trimIndent()
        val candidate = apple.parseSearch(raw, "our divide")
        assertEquals(1605542240L, candidate?.id)
        assertEquals("https://music.apple.com/us/artist/our-divide/1605542240", candidate?.pageUrl)
    }

    @Test fun `multiple unrelated artists with exact same name are not guessed`() {
        val raw = """
            {"results":[
              {"wrapperType":"artist","artistName":"Rival","artistId":22,
               "artistLinkUrl":"https://music.apple.com/us/artist/rival/22"},
              {"wrapperType":"artist","artistName":"Rival","artistId":33,
               "artistLinkUrl":"https://music.apple.com/us/artist/rival/33"}
            ]}
        """.trimIndent()
        assertNull(apple.parseSearch(raw, "Rival"))
    }

    @Test fun `Apple artist search rejects deceptive and invalid links`() {
        for (page in listOf(
            "https://music.apple.com.evil.example/us/artist/a/1605542240",
            "http://music.apple.com/us/artist/a/1605542240",
            "https://music.apple.com/us/album/a/1605542240",
            "https://music.apple.com/us/artist/a/999",
        )) {
            val raw = """{"results":[{"wrapperType":"artist","artistName":"Our Divide",
              "artistId":1605542240,"artistLinkUrl":"$page"}]}"""
            assertNull(apple.parseSearch(raw, "Our Divide"))
        }
    }

    @Test fun `explicit artistLogo is used not sharing image or artist photo`() {
        val artist = AppleMusicWordmarkLookup.Artist(
            1605542240,"https://music.apple.com/us/artist/our-divide/1605542240",
        )
        val page = """
            <meta property="og:image" content="https://evil.example/artwork.png">
            <script>{"storeAdamID":"1605542240","artistLogo":{"dictionary":{
              "width":1200,"height":225,
              "url":"https://is1-ssl.mzstatic.com/image/thumb/MusicArtist/v4/abc/artist-logo.png/1200x480bb.png"
            }}}
            </script>
        """.trimIndent()
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/MusicArtist/v4/abc/artist-logo.png/1200x480bb.png",
            apple.parseLogo(page, artist),
        )
        assertNull(
            apple.parseLogo(
                """<script>{"storeAdamID":"1605542240","artwork":{"dictionary":{"url":
                  "https://is1-ssl.mzstatic.com/image/thumb/Artist/photo.png"}}}</script>""",
                artist,
            ),
        )
    }

    @Test fun `Apple profile must confirm matching artist ID`() {
        val artist = AppleMusicWordmarkLookup.Artist(89, "https://music.apple.com/us/artist/x/89")
        val page = """<script>{"storeAdamID":"99","artistLogo":{"dictionary":
            {"url":"https://is1-ssl.mzstatic.com/image/thumb/logo.png"}}}</script>"""
        assertNull(apple.parseLogo(page, artist))
    }

    @Test fun `Apple logo CDN is restricted to Apple PNG art`() {
        for (url in listOf(
            "https://is1-ssl.mzstatic.com.evil.example/image/thumb/logo.png",
            "https://is1-ssl.mzstatic.com/image/thumb/artist.jpg",
            "http://is1-ssl.mzstatic.com/image/thumb/logo.png",
            "https://music.apple.com/image/thumb/logo.png",
            "https://is1-ssl.mzstatic.com/marketing/logo.png",
        )) assertNull(apple.validateLogoAsset(url))
        assertEquals(
            "https://is5-ssl.mzstatic.com/image/thumb/a/b/1200x480bb.png",
            apple.validateLogoAsset("https://is5-ssl.mzstatic.com/image/thumb/a/b/{w}x{h}bb.{f}"),
        )
    }
}

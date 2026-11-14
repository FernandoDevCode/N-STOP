package com.stash.data.spotify

import com.stash.core.auth.TokenManager
import com.stash.core.auth.spotify.SpotifyAuthManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Regression: searchDesktop can work while artist / album REST hard-429s. */
class SpotifyPartnerDetailsRoutingTest {
    private val user = mockk<TokenManager> {
        coEvery { getSpotifyAccessToken() } returns "user-access"
        coEvery { getSpotifyClientId() } returns "client-id"
        coEvery { forceRefreshSpotifyAccessToken() } returns null
    }
    private val auth = mockk<SpotifyAuthManager> {
        every { getClientVersion() } returns "1.2.3.4.g0"
        coEvery { getClientToken("client-id") } returns "client-token"
        coEvery { getClientCredentialsToken() } returns "public-access"
        every { scrapePersistedQueryHash(any(), any()) } returns null
    }

    private val artistPage = """
      {"data":{"artistUnion":{"discography":{"all":{"totalCount":1,"items":[
        {"releases":{"items":[
          {"uri":"spotify:album:AAAAAAAAAAAAAAAAAAAAAA","name":"Slipknot (25th Anniversary Edition)",
           "artists":{"items":[{"profile":{"name":"Slipknot"}}]}}
        ]}}
      ]}}}}}
    """.trimIndent()
    private val albumPage = """
      {"data":{"albumUnion":{"tracksV2":{"totalCount":1,"items":[
        {"track":{"uri":"spotify:track:BBBBBBBBBBBBBBBBBBBBBB","name":"(sic)",
          "artists":{"items":[{"profile":{"name":"Slipknot"}}]},
          "duration":{"totalMilliseconds":199000}}}
      ]}}}}
    """.trimIndent()

    private fun response(request: okhttp3.Request, status: Int, body: String, retry: String? = null): Response {
        val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(status).message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
        if (retry != null) builder.addHeader("Retry-After", retry)
        return builder.build()
    }

    @Test fun `artist and album pages use Partner before throttled REST`() = runBlocking {
        val restCalls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            if (req.url.host == "api-partner.spotify.com") {
                response(req, 200, if (req.url.queryParameter("operationName") == "getAlbum")
                    albumPage else artistPage)
            } else {
                restCalls.incrementAndGet()
                response(req, 429, "{}", "86400")
            }
        }.build()
        val client = SpotifyApiClient(http, user, auth)
        val artist = client.searchArtistReleasesPage("CCCCCCCCCCCCCCCCCCCCCC")
        val album = client.searchAlbumTracksPage("AAAAAAAAAAAAAAAAAAAAAA")
        assertEquals("Slipknot (25th Anniversary Edition)", artist.items.single().title)
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA", artist.items.single().id)
        assertEquals("BBBBBBBBBBBBBBBBBBBBBB", album.items.single().id)
        assertEquals(0, restCalls.get())
    }

    @Test fun `REST fallback uses public metadata token if Partner schema unavailable`() = runBlocking {
        val restCalls = AtomicInteger()
        val rest = """
          {"next":null,"items":[
            {"id":"AAAAAAAAAAAAAAAAAAAAAA","name":"Album",
             "artists":[{"name":"Slipknot"}]}
          ]}
        """.trimIndent()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            if (req.url.host == "api-partner.spotify.com") response(req, 200, """{"data":{}}""")
            else {
                assertEquals("Bearer public-access", req.header("Authorization"))
                restCalls.incrementAndGet()
                response(req, 200, rest)
            }
        }.build()
        val page = SpotifyApiClient(http, user, auth)
            .searchArtistReleasesPage("CCCCCCCCCCCCCCCCCCCCCC")
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA", page.items.single().id)
        assertEquals(1, restCalls.get())
    }

    @Test fun `429 on one detail blocks repeat REST across album and artist routes`() = runBlocking {
        val restCalls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            if (req.url.host == "api-partner.spotify.com") response(req, 503, "{}")
            else {
                restCalls.incrementAndGet()
                response(req, 429, "{}", "1800")
            }
        }.build()
        val client = SpotifyApiClient(http, user, auth)
        try {
            client.searchArtistReleasesPage("CCCCCCCCCCCCCCCCCCCCCC")
            fail("429 must remain a rate-limit error")
        } catch (rateLimit: SpotifyRateLimitException) {
            assertEquals(1800L, rateLimit.retryAfterSeconds)
        }
        try {
            client.searchAlbumTracksPage("AAAAAAAAAAAAAAAAAAAAAA")
            fail("REST should be skipped during the shared cooldown")
        } catch (rateLimit: SpotifyRateLimitException) {
            assertTrue((rateLimit.retryAfterSeconds ?: 0L) > 0)
        }
        assertEquals(1, restCalls.get())
    }
}

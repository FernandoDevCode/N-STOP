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

/** Pins the Spotify-first network strategy, not merely JSON parsing. */
class SpotifyCatalogRoutingTest {
    private val access = mockk<TokenManager> {
        coEvery { getSpotifyAccessToken() } returns "user-access"
        coEvery { getSpotifyClientId() } returns "client-id"
        coEvery { forceRefreshSpotifyAccessToken() } returns null
    }
    private val auth = mockk<SpotifyAuthManager> {
        every { getClientVersion() } returns "1.2.3.4.g0"
        coEvery { getClientToken("client-id") } returns "client-token"
        every { scrapePersistedQueryHash(any(), any()) } returns null
    }
    private val partnerPage = """
        {"data":{"search":{
           "tracks":{"totalCount":1,"items":[
             {"track":{"uri":"spotify:track:abc","name":"Daydream",
             "artists":{"items":[{"profile":{"name":"I Monster"}}]}}}
           ]},
           "artists":{"totalCount":0,"items":[]},
           "albums":{"totalCount":0,"items":[]}
        }}}
    """.trimIndent()

    @Test fun `catalog uses working partner search instead of 429 REST path`() = runBlocking {
        val restCalls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val isPartner = request.url.host.contains("api-partner.spotify.com")
            if (!isPartner) restCalls.incrementAndGet()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (isPartner) 200 else 429).message("test")
                .body((if (isPartner) partnerPage else "{}")
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val page = SpotifyApiClient(http, access, auth).searchCatalogPage("daydream")
        assertEquals("abc", page.items.single().id)
        assertEquals(0, restCalls.get())
    }

    @Test fun `429 REST fallback enforces cooldown instead of retry spamming`() = runBlocking {
        val restCalls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val isPartner = request.url.host.contains("api-partner.spotify.com")
            if (!isPartner) restCalls.incrementAndGet()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (isPartner) 500 else 429).message("test")
                .addHeader("Retry-After", "3600")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val client = SpotifyApiClient(http, access, auth)
        repeat(2) {
            try {
                client.searchCatalogPage("daydream")
                fail("429 must be an error, not an empty search")
            } catch (limited: SpotifyRateLimitException) {
                assertTrue(limited.retryAfterSeconds != null)
            }
        }
        assertEquals("REST must be called only once during cooldown", 1, restCalls.get())
    }
}

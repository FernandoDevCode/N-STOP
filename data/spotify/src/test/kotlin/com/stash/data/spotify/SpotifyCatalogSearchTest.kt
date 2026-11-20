package com.stash.data.spotify

import com.stash.core.auth.TokenManager
import com.stash.core.auth.spotify.SpotifyAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyCatalogSearchTest {
    private val tokens = mockk<TokenManager>()
    private val requests = mutableListOf<Request>()

    @Test
    fun `signed out makes no catalog request`() = runBlocking {
        coEvery { tokens.getSpotifyAccessToken() } returns null
        val search = client { error("Signed-out search must not reach HTTP") }
        val error = runCatching { search.searchCatalog("rust in peace") }.exceptionOrNull()
        assertEquals(0, (error as SpotifyApiException).httpCode)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `refreshes once and retains native catalog metadata in bucket order`() = runBlocking {
        coEvery { tokens.getSpotifyAccessToken() } returns "expired"
        coEvery { tokens.forceRefreshSpotifyAccessToken() } returns "fresh"
        val search = client { request ->
            if (request.header("Authorization") == "Bearer expired") response(request, 401)
            else response(request, 200, catalogBody)
        }
        val results = search.searchCatalog("rust + peace")
        assertEquals(listOf("Bearer expired", "Bearer fresh"), requests.map { it.header("Authorization") })
        assertEquals("track,artist,album", requests.last().url.queryParameter("type"))
        assertEquals("rust + peace", requests.last().url.queryParameter("q"))
        assertEquals(listOf("trackA", "artistA", "albumA", "trackB"), results.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), results.map { it.rank })
        assertEquals("spotify:track:trackA", results.first().uri)
        assertEquals("Hangar 18 (Demo)", results.first().title)
        assertEquals("https://img.test/art.jpg", results.first().artworkUrl)
        assertEquals(listOf("artistA"), results.first().artistIds)
        assertEquals("Rust in Peace", results.first().albumTitle)
        assertEquals("1990", results.first().releaseYear)
        assertEquals("album", results.first().releaseType)
        assertEquals(123000L, results.first().durationMs)
        assertNull(results.last().durationMs)
        assertTrue(results.first().isExplicit)
        coVerify(exactly = 1) { tokens.forceRefreshSpotifyAccessToken() }
    }

    @Test
    fun `429 retains retry-after and does not refresh or retry`() = runBlocking {
        coEvery { tokens.getSpotifyAccessToken() } returns "fresh"
        val search = client { response(it, 429).newBuilder().header("Retry-After", "12").build() }
        val error = runCatching { search.searchCatalog("demo") }.exceptionOrNull()
        assertEquals(12L, (error as SpotifyRateLimitException).retryAfterSeconds)
        val blocked = runCatching { search.searchCatalogPage("other query") }.exceptionOrNull()
        assertTrue(checkNotNull((blocked as SpotifyRateLimitException).retryAfterSeconds) in 1L..12L)
        assertEquals(1, requests.size)
        coVerify(exactly = 0) { tokens.forceRefreshSpotifyAccessToken() }
    }

    @Test
    fun `partial search distinguishes missing and empty types and pages only the requested type`() = runBlocking {
        coEvery { tokens.getSpotifyAccessToken() } returns "fresh"
        val search = client { request ->
            if (request.url.queryParameter("offset") == "10") {
                response(request, 200, """{"tracks":{"items":[null,{"id":"later","name":"Better match"}],"next":null}}""")
            } else {
                response(request, 200, """
                    {"tracks":{"items":[{"id":"first","name":"First","artists":[{"name":"Lead"},{"name":"Guest"}]}],
                     "offset":0,"limit":10,"next":"https://api.spotify.com/v1/search?offset=10"},
                     "albums":{"items":[],"next":null},"artists":null}
                """.trimIndent())
            }
        }
        val first = search.searchCatalogPage("query")
        assertEquals(setOf(SpotifyCatalogItem.Kind.TRACK, SpotifyCatalogItem.Kind.ALBUM), first.returnedKinds)
        assertEquals(mapOf(SpotifyCatalogItem.Kind.TRACK to 10), first.nextOffsets)
        assertEquals(listOf("Lead", "Guest"), first.items.single().artistNames)
        val second = search.searchCatalogPage("query", kind = SpotifyCatalogItem.Kind.TRACK, offset = 10)
        assertEquals("track", requests.last().url.queryParameter("type"))
        assertEquals("query", requests.last().url.queryParameter("q"))
        assertEquals("10", requests.first().url.queryParameter("limit"))
        assertEquals("later", second.items.single().id)
        assertEquals(33, second.items.single().rank)
        assertTrue(second.nextOffsets.isEmpty())
    }

    private fun client(respond: (Request) -> Response) = SpotifyApiClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            respond(request)
        }.build(),
        tokens,
        mockk<SpotifyAuthManager>(),
    )

    private fun response(request: Request, code: Int, body: String = "") = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
        .body(body.toResponseBody()).build()

    private val catalogBody = """
        {
          "tracks":{"items":[
            {"id":"trackA","uri":"spotify:track:trackA","name":"Hangar 18 (Demo)",
             "explicit":true,"duration_ms":123000,"artists":[{"id":"artistA","name":"Megadeth"}],
             "album":{"name":"Rust in Peace","release_date":"1990-09-24","album_type":"album",
                      "images":[{"url":"https://img.test/art.jpg"}]}},
            {"id":"trackB","name":"Hangar 18 (Live)"}
          ]},
          "artists":{"items":[{"id":"artistA","name":"Megadeth"}]},
          "albums":{"items":[{"id":"albumA","name":"Rust in Peace","artists":[{"name":"Megadeth"}]}]}
        }
    """.trimIndent()
}

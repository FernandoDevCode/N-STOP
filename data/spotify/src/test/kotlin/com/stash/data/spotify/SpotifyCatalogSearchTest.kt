package com.stash.data.spotify

import com.stash.core.auth.TokenManager
import com.stash.core.auth.spotify.SpotifyAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyCatalogSearchTest {
    private val tokens = mockk<TokenManager>()
    private val requests = mutableListOf<Request>()

    private fun ready() {
        coEvery { tokens.getSpotifySearchAccessToken() } returns "access-B"
        coEvery { tokens.getSpotifySearchClientToken(any()) } returns "client-B"
        every { tokens.getSpotifySearchClientVersion() } returns "1.2.test"
        every { tokens.scrapeSpotifySearchQueryHash(any()) } returns null
    }

    @Test fun missingCatalogAccountNeverUsesSync() = runBlocking {
        coEvery { tokens.getSpotifySearchAccessToken() } returns null
        val search = client { error("Missing account B must not reach HTTP") }
        val error = runCatching { search.searchCatalog("rust in peace") }.exceptionOrNull()
        assertEquals(0, (error as SpotifyApiException).httpCode)
        assertTrue(requests.isEmpty())
        coVerify(exactly = 0) { tokens.getSpotifyAccessToken() }
    }

    @Test fun webCatalogPreservesSpotifyIdentityMetadataAndNativeBucketOrder() = runBlocking {
        ready()
        val search = client { response(it, 200, catalogBody) }
        val results = search.searchCatalog("rust + peace")
        val req = requests.single()
        assertEquals("api-partner.spotify.com", req.url.host)
        assertEquals("/pathfinder/v1/query", req.url.encodedPath)
        assertEquals("searchDesktop", req.url.queryParameter("operationName"))
        assertTrue(req.url.queryParameter("variables")!!.contains("rust + peace"))
        assertEquals("Bearer access-B", req.header("Authorization"))
        assertEquals("client-B", req.header("Client-Token"))
        assertEquals(listOf("trackA", "artistA", "albumA", "trackB"), results.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), results.map { it.rank })
        assertEquals("spotify:track:trackA", results.first().uri)
        assertEquals("Hangar 18 (Demo)", results.first().title)
        assertEquals("https://i.scdn.co/image/track", results.first().artworkUrl)
        assertEquals(listOf("artistA"), results.first().artistIds)
        assertEquals(listOf("Megadeth"), results.first().artistNames)
        assertEquals("Rust in Peace", results.first().albumTitle)
        assertEquals("1990", results.first().releaseYear)
        assertEquals("album", results.first().releaseType)
        assertEquals(123000L, results.first().durationMs)
        assertNull(results.last().durationMs)
        assertTrue(results.first().isExplicit)
        assertEquals("https://i.scdn.co/image/artist", results[1].artworkUrl)
        assertEquals("https://i.scdn.co/image/album", results[2].artworkUrl)
        coVerify(exactly = 0) { tokens.getSpotifyAccessToken() }
        coVerify(exactly = 0) { tokens.forceRefreshSpotifyAccessToken() }
    }

    @Test fun oneUnauthorizedResponseRefreshesOnlyCatalogAccount() = runBlocking {
        ready()
        coEvery { tokens.forceRefreshSpotifySearchAccessToken() } returns "new-B"
        val search = client { request ->
            if (request.header("Authorization") == "Bearer access-B") response(request, 401)
            else response(request, 200, catalogBody)
        }
        assertEquals(4, search.searchCatalog("demo").size)
        assertEquals(listOf("Bearer access-B", "Bearer new-B"), requests.map { it.header("Authorization") })
        coVerify(exactly = 1) { tokens.forceRefreshSpotifySearchAccessToken() }
        coVerify(exactly = 0) { tokens.forceRefreshSpotifyAccessToken() }
    }

    @Test fun rateLimitHonorsRetryAfterAndDoesNotRetry() = runBlocking {
        ready()
        val search = client { response(it, 429).newBuilder().header("Retry-After", "12").build() }
        val error = runCatching { search.searchCatalog("demo") }.exceptionOrNull()
        assertEquals(12L, (error as SpotifyRateLimitException).retryAfterSeconds)
        val blocked = runCatching { search.searchCatalogPage("another query") }.exceptionOrNull()
        assertTrue(checkNotNull((blocked as SpotifyRateLimitException).retryAfterSeconds) in 1L..12L)
        assertEquals(1, requests.size)
        coVerify(exactly = 0) { tokens.forceRefreshSpotifySearchAccessToken() }
    }

    @Test fun stalePersistedQueryHashIsRefreshedOnlyOnce() = runBlocking {
        ready()
        val freshHash = "b".repeat(64)
        every { tokens.scrapeSpotifySearchQueryHash(any()) } returns freshHash
        val search = client { request ->
            if (request.url.queryParameter("extensions")!!.contains(freshHash)) response(request, 200, catalogBody)
            else response(request, 400, """{"errors":[{"message":"PersistedQueryNotFound"}]}""")
        }
        assertEquals(4, search.searchCatalog("query").size)
        assertEquals(2, requests.size)
        verify(exactly = 1) { tokens.scrapeSpotifySearchQueryHash(any()) }
        coVerify(exactly = 0) { tokens.getSpotifyAccessToken() }
    }

    @Test fun rejectedClientTokenIsRefreshedAtMostOnce() = runBlocking {
        ready()
        coEvery { tokens.getSpotifySearchClientToken(forceRefresh = true) } returns "new-client-B"
        val search = client { request ->
            if (request.header("Client-Token") == "client-B") response(request, 400)
            else response(request, 200, catalogBody)
        }
        assertEquals(4, search.searchCatalog("demo").size)
        assertEquals(listOf("client-B", "new-client-B"), requests.map { it.header("Client-Token") })
        coVerify(exactly = 1) { tokens.getSpotifySearchClientToken(forceRefresh = true) }
    }

    @Test fun partialWebSearchDistinguishesMissingAndEmptyTypesAndPagesOnlyOneKind() = runBlocking {
        ready()
        val search = client { request ->
            if (request.url.queryParameter("variables")!!.contains("\\"offset\\":10")) {
                response(request, 200, """{"data":{"searchV2":{"tracksV2":{"items":[null,
                    {"item":{"data":{"uri":"spotify:track:later","name":"Better match"}}}],"totalCount":12}}}}""")
            } else {
                response(request, 200, """{"data":{"searchV2":{
                    "tracksV2":{"items":[{"item":{"data":{"uri":"spotify:track:first","name":"First",
                        "artists":{"items":[{"uri":"spotify:artist:lead","profile":{"name":"Lead"}}]}}}}],
                        "pagingInfo":{"nextOffset":10}},
                    "albums":{"items":[]},"artists":null}}}""")
            }
        }
        val first = search.searchCatalogPage("query")
        assertEquals(setOf(SpotifyCatalogItem.Kind.TRACK, SpotifyCatalogItem.Kind.ALBUM), first.returnedKinds)
        assertEquals(mapOf(SpotifyCatalogItem.Kind.TRACK to 10), first.nextOffsets)
        assertEquals(listOf("Lead"), first.items.single().artistNames)
        val second = search.searchCatalogPage("query", kind = SpotifyCatalogItem.Kind.TRACK, offset = 10)
        assertEquals("later", second.items.single().id)
        assertEquals(34, second.items.single().rank)
        assertTrue(second.nextOffsets.isEmpty())
        assertTrue(requests.all { it.url.host == "api-partner.spotify.com" })
    }

    @Test fun emptyBucketIsDataButMissingShapeAndGraphQLErrorsAreFailures() = runBlocking {
        ready()
        val empty = client { response(it, 200, """{"data":{"search":{"tracks":{"items":[]},"artists":{"items":[]},"albums":{"items":[]}}}}""") }
        val result = empty.searchCatalogPage("no results")
        assertEquals(3, result.returnedKinds.size)
        assertTrue(result.items.isEmpty())
        assertTrue(result.nextOffsets.isEmpty())

        val malformed = client { response(it, 200, """{"data":{"search":{"tracks":null}}}""") }
        assertTrue(runCatching { malformed.searchCatalog("query") }.exceptionOrNull() is IOException)

        val graphqlError = client { response(it, 200, """{"errors":[{"message":"invalid request"}]}""") }
        assertTrue(runCatching { graphqlError.searchCatalog("query") }.exceptionOrNull() is IOException)
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
        {"data":{"search":{
            "tracks":{"items":[
                {"track":{"uri":"spotify:track:trackA","name":"Hangar 18 (Demo)",
                    "contentRating":{"label":"EXPLICIT"},"duration":{"totalMilliseconds":123000},
                    "artists":{"items":[{"uri":"spotify:artist:artistA","profile":{"name":"Megadeth"}}]},
                    "albumOfTrack":{"uri":"spotify:album:albumA","name":"Rust in Peace",
                        "date":{"year":1990},"type":"ALBUM",
                        "coverArt":{"sources":[{"url":"https://i.scdn.co/image/track"}]}}}},
                {"track":{"uri":"spotify:track:trackB","name":"Hangar 18 (Live)"}}
            ],"totalCount":2},
            "artists":{"items":[
                {"data":{"uri":"spotify:artist:artistA","profile":{"name":"Megadeth"},
                    "visuals":{"avatarImage":{"sources":[{"url":"https://i.scdn.co/image/artist"}]}}}}
            ],"totalCount":1},
            "albums":{"items":[
                {"data":{"uri":"spotify:album:albumA","name":"Rust in Peace",
                    "artists":{"items":[{"uri":"spotify:artist:artistA","profile":{"name":"Megadeth"}}]},
                    "date":{"year":1990},"type":"ALBUM",
                    "coverArt":{"sources":[{"url":"https://i.scdn.co/image/album"}]}}}
            ],"totalCount":1}
        }}}
    """.trimIndent()
}

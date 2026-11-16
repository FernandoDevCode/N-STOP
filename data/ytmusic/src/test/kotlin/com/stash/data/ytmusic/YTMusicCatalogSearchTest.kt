package com.stash.data.ytmusic

import com.stash.core.auth.TokenManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class YTMusicCatalogSearchTest {
    @Test
    fun `search returns native types and IDs in response order without a player call`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response))
            val hits = client(server).searchCatalog("Hangar 18")
            assertEquals(
                listOf("UGCdemoAB12", "UCArtist", "ATVtrackAB1", "MPREb_release"),
                hits.map { it.id },
            )
            assertEquals(
                listOf(
                    YTMusicCatalogItem.Kind.VIDEO, YTMusicCatalogItem.Kind.ARTIST,
                    YTMusicCatalogItem.Kind.TRACK, YTMusicCatalogItem.Kind.ALBUM,
                ),
                hits.map { it.kind },
            )
            assertEquals(listOf(0, 1, 2, 4), hits.map { it.rank })
            assertEquals("Hangar 18 (Demo)", hits[0].title)
            assertNull(hits[0].durationMs)
            assertEquals(215000L, hits[2].durationMs)
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertTrue(request.path!!.startsWith("/youtubei/v1/search?"))
            assertTrue(request.body.readUtf8().contains("Hangar 18"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `http failure differs from an explicit empty response`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(fixture("search_empty.json")))
            val api = client(server)
            val error = runCatching { api.searchCatalog("test") }.exceptionOrNull()
            assertEquals(503, (error as SearchUnavailableException).statusCode)
            assertTrue(api.searchCatalog("test").isEmpty())
        }
    }

    @Test
    fun `canceling the search cancels the real OkHttp call`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val canceled = CompletableDeferred<Boolean>()
            val api = client(server, object : EventListener() {
                override fun canceled(call: Call) {
                    canceled.complete(call.isCanceled())
                }
            })
            val job = launch { api.searchCatalog("Hangar 18") }
            kotlinx.coroutines.yield()
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            job.cancelAndJoin()
            assertTrue(withTimeout(5_000) { canceled.await() })
        }
    }

    private fun client(server: MockWebServer, listener: EventListener = EventListener.NONE): YTMusicApiClient {
        val http = OkHttpClient.Builder().eventListener(listener).addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath +
                "?" + request.url.encodedQuery.orEmpty())).build())
        }.build()
        return YTMusicApiClient(InnerTubeClient(http, mock<TokenManager>(), mock()))
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")).readText()

    private fun row(id: String, title: String, type: String, subtitle: String): String = """
        {"musicResponsiveListItemRenderer":{
          "navigationEndpoint":{"watchEndpoint":{"videoId":"$id",
            "watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$type"}}}},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[$subtitle]}}}
          ]
        }}
    """.trimIndent()

    private val response = """
        {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
          "sectionListRenderer":{"contents":[
            {"itemSectionRenderer":{"contents":[
              ${row("UGCdemoAB12", "Hangar 18 (Demo)", "MUSIC_VIDEO_TYPE_UGC", """{"text":"Megadeth"}""")},
              {"musicResponsiveListItemRenderer":{
                "navigationEndpoint":{"browseEndpoint":{"browseId":"UCArtist",
                  "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{
                    "pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}},
                "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{
                  "text":{"runs":[{"text":"Megadeth"}]}}}]
              }},
              ${row("ATVtrackAB1", "Hangar 18", "MUSIC_VIDEO_TYPE_ATV",
                  """{"text":"Megadeth"},{"text":" • "},{"text":"3:35"}""")},
              ${row("PodcastAB12", "Episode", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE", "")}
            ]}},
            {"musicShelfRenderer":{"title":{"runs":[{"text":"Albums"}]},"contents":[
              {"musicTwoRowItemRenderer":{"navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_release"}},
                "title":{"runs":[{"text":"Rust in Peace"}]},"subtitle":{"runs":[{"text":"Megadeth"}]}}}
            ]}}
          ]}
        }}}]}}}
    """.trimIndent()
}

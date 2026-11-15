package com.stash.data.ytmusic

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import java.util.concurrent.Executors

class YouTubeWebSearchClientTest {
    private fun video(id: String) = """{"videoRenderer":{"videoId":"$id","title":{"simpleText":"Live"}}}"""
    private fun token(value: String) = """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$value"}}}}"""
    private fun primary(items: String) = """{"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[$items]}},"secondaryContents":{"contents":[${video("MoL0u4fxUW4")},${token("secondary")}]}}}}"""

    @Test fun onlyMainSearchListContributesVideosRanksAndToken() {
        val raw = primary("""{"itemSectionRenderer":{"contents":[${video("JOvAJJI2Ui8")},{"shelfRenderer":{"contents":[${video("JQ2pTamaqQ4")},${token("shelf")}]}}]}},${token("main")}""")
        val page = YouTubeWebSearchClient(OkHttpClient()).parse(raw, 7)
        assertEquals(listOf("JOvAJJI2Ui8"), page.videos.map { it.videoId })
        assertEquals(7, page.videos.single().providerRank)
        assertEquals(8, page.nextRank)
        assertEquals("main", page.continuation)
    }

    @Test fun continuationUsesOnlySearchFeedAndItsDirectToken() {
        val raw = """{"onResponseReceivedActions":[
          {"appendContinuationItemsAction":{"targetId":"sidebar","continuationItems":[${video("MoL0u4fxUW4")},${token("wrong")}] }},
          {"appendContinuationItemsAction":{"targetId":"search-feed","continuationItems":[${video("JOvAJJI2Ui8")},${token("next")},{"shelfRenderer":{"contents":[${video("JQ2pTamaqQ4")},${token("nested-wrong")}]}}]}},
          {"appendContinuationItemsAction":{"targetId":"other","continuationItems":[${token("last-wrong")}]}}
        ]}"""
        val page = YouTubeWebSearchClient(OkHttpClient()).parse(raw, 12)
        assertEquals(listOf("JOvAJJI2Ui8"), page.videos.map { it.videoId })
        assertEquals(12, page.videos.single().providerRank)
        assertEquals("next", page.continuation)
        val unrelated = raw.replace("search-feed", "unrelated")
        assertTrue(YouTubeWebSearchClient(OkHttpClient()).parse(unrelated).videos.isEmpty())
        assertNull(YouTubeWebSearchClient(OkHttpClient()).parse(unrelated).continuation)
    }

    @Test fun emptyMainListDoesNotFallBackToSecondaryVideos() {
        val page = YouTubeWebSearchClient(OkHttpClient()).parse(primary(""))
        assertTrue(page.videos.isEmpty())
        assertNull(page.continuation)
        assertEquals(0, page.nextRank)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun searchWorkAndResumptionsLeaveMainDispatcher() = runBlocking {
        Executors.newSingleThreadExecutor { Thread(it, "test-main") }.asCoroutineDispatcher().use { main ->
            Dispatchers.setMain(main)
            val server = MockWebServer(); server.start()
            try {
                val threads = java.util.Collections.synchronizedList(mutableListOf<String>())
                val http = OkHttpClient.Builder().eventListener(object : EventListener() {
                    override fun callStart(call: Call) { threads += Thread.currentThread().name }
                }).build()
                server.enqueue(MockResponse().setBody(config))
                server.enqueue(MockResponse().setBody(fixture()))
                withContext(Dispatchers.Main) {
                    assertTrue(Thread.currentThread().name.startsWith("test-main"))
                    val page = YouTubeWebSearchClient(http, origin = server.url("/").toString().trimEnd('/')).search("demo")
                    assertEquals(4, page.videos.size)
                    assertTrue(Thread.currentThread().name.startsWith("test-main"))
                }
                assertEquals(2, threads.size)
                assertTrue(threads.all { it.startsWith("DefaultDispatcher-worker") })
            } finally { server.shutdown(); Dispatchers.resetMain() }
        }
    }
    private val config = "\"INNERTUBE_API_KEY\":\"public-test-key\",\"INNERTUBE_CLIENT_VERSION\":\"2.test\""
    private fun fixture() = javaClass.getResource("/fixtures/youtube_web_search.json")!!.readText()

    @Test fun parsesMetadataIncompleteItemsDuplicatesAndVersions() {
        val page = YouTubeWebSearchClient(OkHttpClient()).parse(fixture())
        assertEquals(listOf("JOvAJJI2Ui8", "JQ2pTamaqQ4", "MoL0u4fxUW4", "JOvAJJI2Ui8"), page.videos.map { it.videoId })
        assertEquals(listOf(0, 2, 3, 4), page.videos.map { it.providerRank })
        assertEquals("Megadeth", page.videos.first().channel)
        assertEquals("https://img/large", page.videos.first().thumbnailUrl)
        assertEquals(3723000L, page.videos.first().durationMs)
        assertNull(page.videos[1].channel)
        assertNull(page.videos[1].thumbnailUrl)
        assertNull(page.videos[2].durationMs)
        assertEquals("page-two", page.continuation)
        assertEquals(5, page.nextRank)
        assertTrue(page.videos[0].title.contains("Demo"))
        assertTrue(page.videos[1].title.contains("Live"))
        assertTrue(page.videos[2].title.contains("cover"))
    }

    @Test fun emptyAndNonVideoResponsesAreEmpty() {
        val client = YouTubeWebSearchClient(OkHttpClient())
        assertTrue(client.parse("{}").videos.isEmpty())
        assertTrue(client.parse("""{"contents":[{"playlistRenderer":{}},{"channelRenderer":{}},{"videoRenderer":{"videoId":"JOvAJJI2Ui8"}}]}""").videos.isEmpty())
    }

    @Test fun requestsSearchAndContinuationWithOriginalRank() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(config)); server.enqueue(MockResponse().setBody(fixture()))
            server.enqueue(MockResponse().setBody(config))
            server.enqueue(MockResponse().setBody("""{"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"targetId":"search-feed","continuationItems":[{"videoRenderer":{"videoId":"9Vz-MkbnSg4","title":{"simpleText":"Garden"}}}]}}]}"""))
            val client = YouTubeWebSearchClient(OkHttpClient(), origin = server.url("/").toString().trimEnd('/'))
            val first = client.search("demo")
            val second = client.search("demo", first.continuation, first.nextRank)
            server.takeRequest()
            assertTrue(server.takeRequest().body.readUtf8().contains("\"query\":\"demo\""))
            server.takeRequest()
            assertTrue(server.takeRequest().body.readUtf8().contains("\"continuation\":\"page-two\""))
            assertEquals(5, second.videos.single().providerRank)
            assertEquals("9Vz-MkbnSg4", second.videos.single().videoId)
        } finally { server.shutdown() }
    }

    @Test fun httpFailureIsNotEmptyResults() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody(config)); server.enqueue(MockResponse().setResponseCode(429))
            try {
                YouTubeWebSearchClient(OkHttpClient(), origin = server.url("/").toString().trimEnd('/')).search("demo")
                fail("Expected HTTP error")
            } catch (e: YouTubeWebHttpException) { assertEquals(429, e.statusCode) }
        } finally { server.shutdown() }
    }

    @Test fun timeoutFailsAndCancellationCancelsActualCall() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                YouTubeWebSearchClient(OkHttpClient(), origin = server.url("/").toString().trimEnd('/'), timeoutMs = 100).search("timeout")
                fail("Expected timeout")
            } catch (_: IOException) { }
            server.takeRequest(2, TimeUnit.SECONDS)
            val captured = AtomicReference<Call>()
            val canceled = CountDownLatch(1)
            val http = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callStart(call: Call) { captured.set(call) }
                override fun canceled(call: Call) { canceled.countDown() }
            }).build()
            server.enqueue(MockResponse().setBody(config))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch(Dispatchers.Default) {
                YouTubeWebSearchClient(http, origin = server.url("/").toString().trimEnd('/')).search("cancel")
            }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(canceled.await(2, TimeUnit.SECONDS))
            assertTrue(captured.get().isCanceled())
        } finally { server.shutdown() }
    }
}

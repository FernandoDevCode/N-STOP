package com.stash.core.network.art

import okhttp3.Connection
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rung-walk itself: which URLs get tried, in what order, and that a
 * non-404 is handed back untouched. The URL arithmetic lives in
 * `ArtUrlUpgrader.artFallback` and is tested there.
 */
class ArtFallbackInterceptorTest {
    /** Answers each request with the code [codes] gives for its URL. */
    private class FakeChain(
        private val start: Request,
        private val codes: (String) -> Int,
    ) : Interceptor.Chain {
        val tried = mutableListOf<String>()
        val closed = mutableListOf<String>()

        override fun request(): Request = start

        override fun proceed(request: Request): Response {
            val url = request.url.toString()
            tried += url
            val code = codes(url)
            val body = object : okhttp3.ResponseBody() {
                private val delegate = "x".toResponseBody("image/jpeg".toMediaType())
                override fun contentType() = delegate.contentType()
                override fun contentLength() = delegate.contentLength()
                override fun source() = delegate.source()
                override fun close() {
                    closed += url
                    delegate.close()
                }
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Not Found")
                .body(body)
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun readTimeoutMillis() = 0
        override fun writeTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }

    @Test
    fun `retired hosts cannot initiate network calls even for cached legacy art`() {
        listOf(
            "https://lastfm-img.freetls.fastly.net/i/u/300x300/abc.png",
            "https://lastfm.freetls.fastly.net/i/u/770x0/abc.jpg",
            "https://www.last.fm/music/example",
            "https://ws.audioscrobbler.last.fm/2.0/",
        ).forEach { url ->
            val chain = FakeChain(Request.Builder().url(url).build()) { 200 }
            val response = ArtFallbackInterceptor().intercept(chain)
            assertEquals(410, response.code)
            assertTrue("network request escaped to $url", chain.tried.isEmpty())
            response.close()
        }
    }

    @Test
    fun `non-retired artwork is fetched normally`() {
        val url = "https://i.scdn.co/image/ab67616d0000b273aaaa"
        val chain = FakeChain(Request.Builder().url(url).build()) { 200 }
        val response = ArtFallbackInterceptor().intercept(chain)
        assertEquals(200, response.code)
        assertEquals(listOf(url), chain.tried)
        response.close()
    }

    @Test
    fun `a 404 on the last rung is returned as-is`() {
        val yt = "https://i.ytimg.com/vi/abc/hqdefault.jpg"
        val chain = FakeChain(Request.Builder().url(yt).build()) { 404 }
        val response = ArtFallbackInterceptor().intercept(chain)
        assertEquals(listOf(yt), chain.tried)
        assertEquals(404, response.code)
        response.close()
    }

    @Test
    fun `a youtube thumbnail walks maxres to sd to hq`() {
        val max = "https://i.ytimg.com/vi/abc/maxresdefault.jpg"
        val chain = FakeChain(Request.Builder().url(max).build()) {
            if (it.endsWith("hqdefault.jpg")) 200 else 404
        }
        ArtFallbackInterceptor().intercept(chain).close()
        assertEquals(
            listOf(max, "https://i.ytimg.com/vi/abc/sddefault.jpg", "https://i.ytimg.com/vi/abc/hqdefault.jpg"),
            chain.tried,
        )
    }

    // The large variants ArtUrlSizer asks for on Now Playing: each walks back to a size that
    // always exists, so a big cover can never render worse than the stored one did.

    @Test
    fun `a missing qobuz max falls back to the 600 that was stored`() {
        val max = "https://static.qobuz.com/images/covers/ab/cd/xyz_max.jpg"
        val chain = FakeChain(Request.Builder().url(max).build()) { if (it.endsWith("_max.jpg")) 404 else 200 }
        ArtFallbackInterceptor().intercept(chain).close()
        assertEquals(listOf(max, "https://static.qobuz.com/images/covers/ab/cd/xyz_600.jpg"), chain.tried)
    }

    @Test
    fun `a missing spotify original falls back to 640`() {
        val orig = "https://i.scdn.co/image/ab67616d000082c1aaaa"
        val chain = FakeChain(Request.Builder().url(orig).build()) { if ("82c1" in it) 404 else 200 }
        ArtFallbackInterceptor().intercept(chain).close()
        assertEquals(listOf(orig, "https://i.scdn.co/image/ab67616d0000b273aaaa"), chain.tried)
    }
}

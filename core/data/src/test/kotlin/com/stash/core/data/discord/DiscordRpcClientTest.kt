package com.stash.core.data.discord

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [DiscordRpcClient] against canned Discord answers (an interceptor, no
 * network): which 401 means "mint a new access token" and which one means the
 * user token itself was revoked.
 */
class DiscordRpcClientTest {

    /** Answers Discord's four endpoints; [sessionCodes] are used up one per headless-sessions POST. */
    private class FakeDiscord : Interceptor {
        val requests = mutableListOf<Request>()
        var authorizeCode = 200
        val sessionCodes = ArrayDeque<Int>()
        private val tokensMinted = AtomicInteger()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            synchronized(requests) { requests += req }
            val path = req.url.encodedPath
            val (code, body) = when {
                path.endsWith("/oauth2/authorize") ->
                    authorizeCode to """{"location":"https://example.invalid/?code=abc&state=undefined"}"""
                path.endsWith("/oauth2/token") ->
                    200 to """{"access_token":"access-${tokensMinted.incrementAndGet()}"}"""
                path.endsWith("/headless-sessions") ->
                    (synchronized(sessionCodes) { sessionCodes.removeFirstOrNull() } ?: 200) to """{"token":"activity"}"""
                path.endsWith("/headless-sessions/delete") -> 200 to "{}"
                else -> 404 to ""
            }
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("canned")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }

        fun paths(): List<String> = synchronized(requests) { requests.map { it.url.encodedPath } }
        fun sessionPosts(): List<Request> =
            synchronized(requests) { requests.filter { it.url.encodedPath.endsWith("/headless-sessions") } }
    }

    private val activity = DiscordActivity(name = "Stash", details = "Song", state = "Artist")

    private fun client(fake: FakeDiscord, onUnauthorized: () -> Unit) = DiscordRpcClient(
        userToken = "user-token",
        onUnauthorized = onUnauthorized,
        http = OkHttpClient.Builder().addInterceptor(fake).build(),
    )

    @Test
    fun `an expired access token is re-minted and the user stays connected`() = runTest {
        val fake = FakeDiscord()
        var unauthorized = false
        val client = client(fake) { unauthorized = true }

        client.sendNow(activity) // mints access-1, posts
        fake.sessionCodes += 401 // access-1 has expired
        client.sendNow(activity)

        assertFalse("an expired access token must not clear the Discord login", unauthorized)
        val posts = fake.sessionPosts()
        assertEquals(3, posts.size)
        assertEquals("Bearer access-1", posts[1].header("Authorization"))
        assertEquals("Bearer access-2", posts[2].header("Authorization"))
        assertEquals(2, fake.paths().count { it.endsWith("/oauth2/token") })
    }

    @Test
    fun `a 401 that persists after a new access token does not clear the login either`() = runTest {
        val fake = FakeDiscord()
        var unauthorized = false
        val client = client(fake) { unauthorized = true }
        fake.sessionCodes += listOf(401, 401)

        client.sendNow(activity)

        assertFalse(unauthorized)
        assertEquals("one retry, not a loop", 2, fake.sessionPosts().size)
    }

    @Test
    fun `a revoked user token is still reported`() = runTest {
        val fake = FakeDiscord().apply { authorizeCode = 401 }
        var unauthorized = false
        val client = client(fake) { unauthorized = true }

        client.sendNow(activity)

        assertTrue(unauthorized)
        assertTrue(fake.sessionPosts().isEmpty())
    }
}

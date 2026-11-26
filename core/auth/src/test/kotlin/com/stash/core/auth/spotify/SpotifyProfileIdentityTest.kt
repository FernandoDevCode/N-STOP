package com.stash.core.auth.spotify

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SpotifyProfileIdentityTest {
    private val auth = SpotifyAuthManager(OkHttpClient())
    @Test fun `only authenticated profile username or user URI identifies an account`() {
        assertEquals("accountA", auth.parseProfileAccountId("""{"data":{"me":{"profile":{"username":"accountA"}}}}"""))
        assertEquals("accountA", auth.parseProfileAccountId("""{"data":{"me":{"profile":{"uri":"spotify:user:accountA"}}}}"""))
        assertNull(auth.parseProfileAccountId("""{"data":{"me":{"profile":{"name":"Display Name"}}}}"""))
        assertNull(auth.parseProfileAccountId("""{"data":{"me":{"profile":{"username":"spotify_user"}}}}"""))
        assertNull(auth.parseProfileAccountId("""{"data":{"me":{"profile":{"username":"A","uri":"spotify:user:B"}}}}"""))
        assertNull(auth.parseProfileAccountId("""{"data":{"me":{"profile":{"username":"A"}}},"errors":[{"message":"unauthorized"}]}"""))
        assertNull(auth.parseProfileAccountId("""{"data":{"me":{"profile":{"username":123}}}}"""))
    }

    @Test fun `profile request retries a rotated hash from the Spotify player bundle`() = runBlocking {
        val rotated = "a".repeat(64)
        val requests = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            requests += url
            val payload = when {
                "pathfinder" in url -> {
                    assertEquals("Bearer personal-token", req.header("Authorization"))
                    assertEquals("client-token", req.header("Client-Token"))
                    if (rotated in url) """{"data":{"me":{"profile":{"username":"accountA"}}}}"""
                    else """{"errors":[{"message":"PersistedQueryNotFound"}]}"""
                }
                "web-player.abc.js" in url -> """new X("profileAttributes","query","$rotated",null)"""
                else -> """<script src="https://cdn.example/web-player/web-player.abc.js"></script>"""
            }
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(payload.toResponseBody()).build()
        }.build()
        assertEquals("accountA", SpotifyAuthManager(client).lookUpProfileAccountId("personal-token", "client-token"))
        assertEquals(2, requests.count { "pathfinder" in it })
        assertEquals(1, requests.count { "web-player.abc.js" in it })
    }
}

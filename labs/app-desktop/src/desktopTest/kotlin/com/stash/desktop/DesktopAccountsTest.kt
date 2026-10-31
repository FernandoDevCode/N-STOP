package com.stash.desktop

import com.stash.core.auth.model.*
import com.stash.core.data.lastfm.LastFmCredentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DesktopAccountsTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private val accounts = mutableListOf<DesktopAccounts>()
    @Before fun start() { server.start() }
    @After fun stop() { accounts.forEach { it.close() }; server.shutdown() }
    private fun host(): DesktopAccounts {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val target = server.url(request.url.encodedPath).newBuilder().encodedQuery(request.url.encodedQuery).build()
            chain.proceed(request.newBuilder().url(target).build())
        }.callTimeout(5, TimeUnit.SECONDS).build()
        val cipher = object : CredentialCipher {
            override fun protect(cleartext: ByteArray) = cleartext.reversedArray()
            override fun unprotect(ciphertext: ByteArray) = ciphertext.reversedArray()
        }
        return DesktopAccounts(folder.newFolder(), client, cipher, LastFmCredentials("", "")).also { accounts.add(it) }
    }
    @Test fun youtubeRequiresAuthenticatedServerProfileBeforePersisting() = runBlocking {
        val host = host()
        host.ready.first { it }
        server.enqueue(MockResponse().setBody("""{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":{"accountName":{"simpleText":"Test profile"},"email":{"simpleText":"test@example.invalid"}}}}}}}]}"""))
        host.connect(AuthService.YOUTUBE_MUSIC, "SAPISID=synthetic; LOGIN_INFO=synthetic")
        val profile = withTimeout(5000) { host.tokens.youTubeAuthState.first { it is AuthState.Connected } } as AuthState.Connected
        Assert.assertEquals("Test profile", profile.user.displayName)
        Assert.assertEquals("SAPISID=synthetic; LOGIN_INFO=synthetic", host.tokens.getYouTubeCookie())
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        Assert.assertTrue(request.getHeader("Authorization")!!.startsWith("SAPISIDHASH "))
        host.disconnect(AuthService.YOUTUBE_MUSIC)
        withTimeout(5000) { host.tokens.youTubeAuthState.first { it is AuthState.NotConnected } }
        Assert.assertNull(host.tokens.getYouTubeCookie())
    }
    @Test fun syntacticallyValidButAnonymousCookieIsNeverConnected() = runBlocking {
        val host = host()
        host.ready.first { it }
        server.enqueue(MockResponse().setBody("""{"actions":[],"signedOut":true}"""))
        host.connect(AuthService.YOUTUBE_MUSIC, "SAPISID=not-a-real-session")
        withTimeout(5000) { host.error.first { it != null } }
        Assert.assertNull(host.vault.youTubeToken.first())
        Assert.assertTrue(host.tokens.youTubeAuthState.value is AuthState.NotConnected)
        Assert.assertFalse(host.error.value!!.contains("not-a-real-session"))
    }
    @Test fun spotifyUsesOriginalCookieExchangeAndRejectsAnonymousToken() = runBlocking {
        var anonymous = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (request.path!!.startsWith("/api/token")) {
                MockResponse().setBody("""{"accessToken":"synthetic-access","accessTokenExpirationTimestampMs":${System.currentTimeMillis() + 3600000},"isAnonymous":$anonymous,"username":"test-user","clientId":"test-client"}""")
            } else MockResponse().setBody("")
        }
        val host = host()
        host.ready.first { it }
        host.connect(AuthService.SPOTIFY, "sp_dc=synthetic-cookie;")
        withTimeout(5000) { host.tokens.spotifyAuthState.first { it is AuthState.Connected } }
        Assert.assertEquals("synthetic-access", host.tokens.getSpotifyAccessToken())
        host.disconnect(AuthService.SPOTIFY)
        withTimeout(5000) { host.tokens.spotifyAuthState.first { it is AuthState.NotConnected } }
        anonymous = true
        host.connect(AuthService.SPOTIFY, "invalid-session")
        withTimeout(5000) { host.error.first { it != null } }
        Assert.assertNull(host.vault.spotifyToken.first())
    }
    @Test fun lastFmBrowserAuthorizationPersistsOnlyConfirmedSession() = runBlocking<Unit> {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.queryParameter("method")) {
                "auth.getToken" -> MockResponse().setBody("""{"token":"synthetic-pending"}""")
                "auth.getSession" -> MockResponse().setBody("""{"session":{"name":"test-lastfm","key":"synthetic-session"}}""")
                else -> MockResponse().setResponseCode(400)
            }
        }
        val host = host()
        host.ready.first { it }
        host.beginLastFm("synthetic-api-key", "synthetic-api-secret")
        withTimeout(5000) { host.lastFmAuthorizationUrl.first { it != null } }
        Assert.assertTrue(host.lastFmState.value is AuthState.NotConnected)
        host.finishLastFm()
        withTimeout(5000) { host.lastFmState.first { it is AuthState.Connected } }
        Assert.assertEquals("test-lastfm", (host.lastFmState.value as AuthState.Connected).user.displayName)
        host.disconnectLastFm()
        withTimeout(5000) { host.lastFmState.first { it is AuthState.NotConnected } }
    }
    @Test fun discordValidationUsesOriginalClientAndPersistsProfile() = runBlocking {
        val host = host()
        host.ready.first { it }
        server.enqueue(MockResponse().setBody("""{"user":{"id":"test-discord-id","username":"test-discord","avatar":"test-avatar"}}"""))
        host.connect(AuthService.DISCORD, "synthetic-discord-token")
        val state = withTimeout(5000) { host.tokens.discordAuthState.first { it is AuthState.Connected } } as AuthState.Connected
        Assert.assertEquals("test-discord-id", state.user.id)
        Assert.assertEquals("synthetic-discord-token", host.tokens.getDiscordUserToken())
    }
    @Test fun concurrentSpotifyRefreshUsesOneExchangeAndPreservesProfile() = runBlocking {
        val exchanges = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/api/token")) return MockResponse().setBody("")
                exchanges.incrementAndGet()
                return MockResponse().setBodyDelay(100, TimeUnit.MILLISECONDS).setBody("""{"accessToken":"refreshed","accessTokenExpirationTimestampMs":${System.currentTimeMillis() + 3600000},"isAnonymous":false,"username":"test-user","clientId":"test-client"}""")
            }
        }
        val host = host()
        host.ready.first { it }
        val profile = UserInfo("test-user", "Preserved profile")
        host.vault.saveSpotifyToken(ServiceToken("expired", "synthetic-cookie", 0), profile)
        val results = List(3) { async(Dispatchers.Default) { host.tokens.getSpotifyAccessToken() } }.awaitAll()
        Assert.assertEquals(listOf("refreshed", "refreshed", "refreshed"), results)
        Assert.assertEquals(1, exchanges.get())
        Assert.assertEquals(profile, host.vault.spotifyUser.first())
    }
    @Test fun disconnectCannotBeUndoneByAnInFlightTokenRefresh() = runBlocking {
        val requested = CompletableDeferred<Unit>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/api/token")) return MockResponse().setBody("")
                requested.complete(Unit)
                return MockResponse().setBodyDelay(200, TimeUnit.MILLISECONDS).setBody("""{"accessToken":"refreshed","accessTokenExpirationTimestampMs":${System.currentTimeMillis() + 3600000},"isAnonymous":false,"username":"test-user","clientId":"test-client"}""")
            }
        }
        val host = host()
        host.ready.first { it }
        host.vault.saveSpotifyToken(ServiceToken("expired", "synthetic-cookie", 0), UserInfo("test-user", "Test"))
        val refresh = async(Dispatchers.Default) { host.tokens.getSpotifyAccessToken() }
        withTimeout(5000) { requested.await() }
        val disconnect = async(Dispatchers.Default) { host.tokens.clearAuth(AuthService.SPOTIFY) }
        refresh.await(); disconnect.await()
        Assert.assertNull(host.vault.spotifyToken.first())
        Assert.assertNull(host.tokens.getSpotifyAccessToken())
    }
}

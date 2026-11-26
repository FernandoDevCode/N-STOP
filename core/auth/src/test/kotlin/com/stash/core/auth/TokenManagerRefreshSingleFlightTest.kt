package com.stash.core.auth

import com.stash.core.auth.model.ServiceToken
import com.stash.core.auth.model.UserInfo
import com.stash.core.auth.spotify.SpotifyAuthManager
import com.stash.core.auth.store.EncryptedTokenStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Near expiry a sync, the auto-save scrobbler and like mirroring all ask for a
 * Spotify token at once. Each used to refresh on its own: N hits on the
 * TOTP-guarded, rate-limited token endpoint and N store writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenManagerRefreshSingleFlightTest {

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `concurrent callers near expiry share one refresh`() = runTest {
        val now = Instant.now().epochSecond
        val stored = MutableStateFlow<ServiceToken?>(ServiceToken("old", "sp_dc", expiresAtEpoch = now - 1))
        val store = mockk<EncryptedTokenStore>(relaxed = true) {
            every { spotifyToken } returns stored
            every { spotifyUser } returns MutableStateFlow(UserInfo("me", "Me"))
            every { spotifySearchToken } returns MutableStateFlow(null)
            every { spotifySearchUser } returns MutableStateFlow(null)
            every { youTubeToken } returns MutableStateFlow(null)
            every { youTubeUser } returns MutableStateFlow(null)
            every { discordToken } returns MutableStateFlow(null)
            every { discordUser } returns MutableStateFlow(null)
            coEvery { saveSpotifyToken(any(), any()) } answers { stored.value = firstArg() }
        }
        val gate = CompletableDeferred<Unit>()
        val auth = mockk<SpotifyAuthManager> {
            coEvery { refreshAccessToken("sp_dc") } coAnswers {
                gate.await()
                ServiceToken("new", "sp_dc", expiresAtEpoch = now + 3_600)
            }
        }
        val manager = TokenManagerImpl(store, auth, mockk(relaxed = true))

        val callers = List(3) { async { manager.getSpotifyAccessToken() } }
        advanceUntilIdle()
        gate.complete(Unit)

        assertEquals(listOf("new", "new", "new"), callers.awaitAll())
        coVerify(exactly = 1) { auth.refreshAccessToken(any()) }
        coVerify(exactly = 1) { store.saveSpotifyToken(any(), any()) }
        // Close the TokenManager's independent Flow observers before @After resets Main.
        manager.close()
        manager.awaitClosed()
    }

    private fun identityStore(stored: MutableStateFlow<ServiceToken?>) =
        mockk<EncryptedTokenStore>(relaxed = true) {
            every { spotifyToken } returns stored
            every { spotifyUser } returns MutableStateFlow(UserInfo("manual", "Manual"))
            every { spotifySearchToken } returns MutableStateFlow(null)
            every { spotifySearchUser } returns MutableStateFlow(null)
            every { youTubeToken } returns MutableStateFlow(null)
            every { youTubeUser } returns MutableStateFlow(null)
            every { discordToken } returns MutableStateFlow(null)
            every { discordUser } returns MutableStateFlow(null)
            coEvery { saveSpotifyToken(any(), any()) } answers { stored.value = firstArg() }
        }

    @Test fun `legacy opaque token refreshes once and uses authenticated response identity`() = runTest {
        val expiry = Instant.now().epochSecond + 3_600
        val stored = MutableStateFlow<ServiceToken?>(ServiceToken("legacy", "cookie-A", expiry, "manual|client"))
        val store = identityStore(stored)
        val auth = mockk<SpotifyAuthManager> {
            every { extractUsernameFromJwt(any()) } returns null
            coEvery { refreshAccessToken("cookie-A") } returns
                ServiceToken("opaque", "cookie-A", expiry, "verifiedA|client|identity-v1:verifiedA")
        }
        val manager = TokenManagerImpl(store, auth, mockk(relaxed = true))
        try {
            assertEquals("verifiedA", manager.getSpotifyLibraryAccountId())
            assertEquals("verifiedA", manager.getSpotifyLibraryAccountId())
            coVerify(exactly = 1) { auth.refreshAccessToken("cookie-A") }
            // A newly connected account cannot inherit the preceding identity.
            stored.value = ServiceToken("other", "cookie-B", expiry, "verifiedB|client|identity-v1:verifiedB")
            assertEquals("verifiedB", manager.getSpotifyLibraryAccountId())
        } finally {
            manager.close()
            manager.awaitClosed()
        }
    }

    @Test fun `unverified manual username is never used as account proof`() = runTest {
        val expiry = Instant.now().epochSecond + 3_600
        val stored = MutableStateFlow<ServiceToken?>(ServiceToken("legacy", "cookie-A", expiry, "manual|client"))
        val store = identityStore(stored)
        val auth = mockk<SpotifyAuthManager> {
            every { extractUsernameFromJwt(any()) } returns null
            coEvery { refreshAccessToken("cookie-A") } returns
                ServiceToken("opaque", "cookie-A", expiry, "|client|identity-v1:")
        }
        val manager = TokenManagerImpl(store, auth, mockk(relaxed = true))
        try {
            assertEquals(null, manager.getSpotifyLibraryAccountId())
            assertEquals(null, manager.getSpotifyLibraryAccountId())
            coVerify(exactly = 1) { auth.refreshAccessToken("cookie-A") }
        } finally {
            manager.close()
            manager.awaitClosed()
        }
    }

    @Test fun `a manually entered username cannot override fresh response identity`() = runTest {
        val expiry = Instant.now().epochSecond + 3_600
        val stored = MutableStateFlow<ServiceToken?>(null)
        val store = identityStore(stored)
        val auth = mockk<SpotifyAuthManager> {
            every { extractUsernameFromJwt(any()) } returns null
            coEvery { getAccessToken("cookie-A") } returns
                ServiceToken("opaque", "cookie-A", expiry, "verifiedA|client|identity-v1:verifiedA")
        }
        val manager = TokenManagerImpl(store, auth, mockk(relaxed = true))
        try {
            assertEquals(true, manager.connectSpotifyWithCookie("cookie-A", "manual"))
            assertEquals("verifiedA", manager.getSpotifyLibraryAccountId())
            assertEquals("verifiedA|client|identity-v1:verifiedA", stored.value?.scope)
        } finally {
            manager.close()
            manager.awaitClosed()
        }
    }

    @Test fun `catalog credentials and disconnect cannot touch the sync token`() = runTest {
        val now = Instant.now().epochSecond
        val personal = MutableStateFlow<ServiceToken?>(ServiceToken("personal", "cookie-A", now + 3_600))
        val catalog = MutableStateFlow<ServiceToken?>(ServiceToken("search", "cookie-B", now + 3_600))
        val store = mockk<EncryptedTokenStore>(relaxed = true) {
            every { spotifyToken } returns personal
            every { spotifyUser } returns MutableStateFlow(UserInfo("A", "Sync"))
            every { spotifySearchToken } returns catalog
            every { spotifySearchUser } returns MutableStateFlow(UserInfo("B", "Search"))
            every { youTubeToken } returns MutableStateFlow(null)
            every { youTubeUser } returns MutableStateFlow(null)
            every { discordToken } returns MutableStateFlow(null)
            every { discordUser } returns MutableStateFlow(null)
            coEvery { clearSpotifySearch() } answers { catalog.value = null }
        }
        val manager = TokenManagerImpl(store, mockk(relaxed = true), mockk(relaxed = true))
        assertEquals("personal", manager.getSpotifyAccessToken())
        assertEquals("search", manager.getSpotifySearchAccessToken())
        manager.clearSpotifySearch()
        assertEquals(null, manager.getSpotifySearchAccessToken())
        assertEquals("personal", manager.getSpotifyAccessToken())
        coVerify(exactly = 1) { store.clearSpotifySearch() }
        coVerify(exactly = 0) { store.clearSpotify() }
        manager.close()
        manager.awaitClosed()
    }
}

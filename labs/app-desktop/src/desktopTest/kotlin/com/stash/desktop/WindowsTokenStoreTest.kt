package com.stash.desktop

import com.stash.core.auth.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.TemporaryFolder
import java.io.File

class WindowsTokenStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun realWindowsDpapiRestoresCredentialsAndKeepsDiskOpaque() = runBlocking {
        Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val directory = folder.newFolder()
        val store = WindowsTokenStore(directory)
        store.initialize()
        val token = ServiceToken("test-access-private", "test-cookie-private", Long.MAX_VALUE, "test-user|test-client")
        val user = UserInfo("test-user", "Test profile")
        store.saveSpotifyToken(token, user)
        val encrypted = File(directory, "spotify.dpapi").readBytes().decodeToString()
        Assert.assertFalse(encrypted.contains(token.refreshToken))
        Assert.assertFalse(encrypted.contains(token.accessToken))
        val restored = WindowsTokenStore(directory)
        restored.initialize()
        Assert.assertEquals(token, restored.spotifyToken.first())
        Assert.assertEquals(user, restored.spotifyUser.first())
        restored.saveYouTubeToken(token.copy(refreshToken = "test-youtube-private"), user)
        restored.clearSpotify()
        Assert.assertNull(restored.spotifyToken.first())
        Assert.assertNotNull(restored.youTubeToken.first())
        Assert.assertFalse(File(directory, "spotify.dpapi").exists())
    }
    @Test fun failedDecryptionPreservesFileUntilExplicitDisconnect() = runBlocking {
        val directory = folder.newFolder()
        val damaged = File(directory, "spotify.dpapi").apply { writeText("invalid encrypted data") }
        val store = WindowsTokenStore(directory, object : CredentialCipher {
            override fun protect(cleartext: ByteArray) = cleartext.reversedArray()
            override fun unprotect(ciphertext: ByteArray): ByteArray = error("synthetic decryption failure")
        })
        store.initialize()
        Assert.assertNull(store.spotifyToken.first())
        Assert.assertTrue(AuthService.SPOTIFY in store.unreadableServices.value)
        try { store.saveSpotifyToken(ServiceToken("new", "new", 9), null); Assert.fail("Overwrote unreadable credential") }
        catch (_: IllegalStateException) { }
        Assert.assertEquals("invalid encrypted data", damaged.readText())
        store.clearSpotify()
        Assert.assertFalse(damaged.exists())
    }
    @Test fun failedEncryptionCannotReplaceExistingCredential() = runBlocking {
        val directory = folder.newFolder()
        var fail = false
        val cipher = object : CredentialCipher {
            override fun protect(cleartext: ByteArray): ByteArray {
                if (fail) error("synthetic encryption failure")
                return cleartext.reversedArray()
            }
            override fun unprotect(ciphertext: ByteArray) = ciphertext.reversedArray()
        }
        val store = WindowsTokenStore(directory, cipher)
        store.initialize()
        val original = ServiceToken("first", "original", 99)
        store.saveSpotifyToken(original, UserInfo("one", "One"))
        val bytes = File(directory, "spotify.dpapi").readBytes()
        fail = true
        try { store.saveSpotifyToken(original.copy(accessToken = "second"), null); Assert.fail("Encryption should fail") }
        catch (_: IllegalStateException) { }
        Assert.assertArrayEquals(bytes, File(directory, "spotify.dpapi").readBytes())
        Assert.assertEquals(original, store.spotifyToken.first())
    }
}

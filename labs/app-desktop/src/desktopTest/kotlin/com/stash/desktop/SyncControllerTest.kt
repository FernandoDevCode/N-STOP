package com.stash.desktop

import com.stash.core.auth.model.*
import com.stash.core.data.lastfm.LastFmCredentials
import com.stash.core.model.SyncResult
import com.stash.data.spotify.*
import com.stash.data.spotify.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncControllerTest {
    @get:Rule val folder = TemporaryFolder()
    private val controllers = mutableListOf<AppController>()
    @After fun close() { controllers.forEach { it.close() } }
    private suspend fun controller(source: SpotifySyncSource): AppController {
        val store = LibraryStore(folder.newFolder())
        store.save(LibrarySnapshot(listOf(SavedTrack(10, "Local", "Fixture", path = "local-fixture.wav"))))
        val cipher = object : CredentialCipher {
            override fun protect(cleartext: ByteArray) = cleartext.reversedArray()
            override fun unprotect(ciphertext: ByteArray) = ciphertext.reversedArray()
        }
        val accounts = DesktopAccounts(File(store.directory, "accounts"), cipher = cipher,
            initialLastFmCredentials = LastFmCredentials("", ""))
        accounts.ready.first { it }
        accounts.vault.saveSpotifyToken(ServiceToken("synthetic", "synthetic", Long.MAX_VALUE, "test-user|test-client"), UserInfo("test-user", "Test user"))
        return AppController(store, accounts = accounts, spotifySyncSource = source).also { controllers.add(it) }
    }
    private open class Source : SpotifySyncSource {
        override suspend fun playlists(limit: Int, offset: Int, folder: String?) = SpotifyLibraryPage.EMPTY
        override suspend fun liked(limit: Int, offset: Int): SyncResult<SpotifyLikedSongsPage> = SyncResult.Empty("empty")
        override suspend fun tracks(id: String): SyncResult<SpotifyPlaylistTracks> = SyncResult.Empty("empty")
        override suspend fun mixes(): SyncResult<List<SpotifyPlaylistItem>> = SyncResult.Empty("empty")
    }
    @Test fun completeReadCommitsOnceAndRetainsLocalLibrary() = runBlocking {
        val app = controller(object : Source() {
            override suspend fun liked(limit: Int, offset: Int) = SyncResult.Success(SpotifyLikedSongsPage(listOf(
                SpotifyTrackItem(SpotifyTrackObject("remote", "Remota", listOf(SpotifyArtist("a", "Artista")), uri = "spotify:track:remote"))), 1, 1))
        })
        app.synchronize(DesktopSyncOptions(playlists = false), youtube = false)
        val state = withTimeout(5000) { app.syncState.first { !it.running && it.label.isNotEmpty() } }
        Assert.assertEquals("Sincronização concluída", state.label)
        Assert.assertEquals(2, app.store.load().tracks.size)
        Assert.assertEquals(app.library.value, app.store.load())
        Assert.assertEquals("liked", app.store.load().playlists.single().sourceId)
    }
    @Test fun cancelBeforeCommitLeavesPersistedSnapshotUnchanged() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val app = controller(object : Source() {
            override suspend fun liked(limit: Int, offset: Int): SyncResult<SpotifyLikedSongsPage> { entered.complete(Unit); awaitCancellation() }
        })
        val before = app.store.load()
        app.synchronize(DesktopSyncOptions(playlists = false), youtube = false)
        entered.await(); app.cancelSync()
        withTimeout(5000) { app.syncState.first { !it.running } }
        Assert.assertEquals(before, app.store.load())
    }
    @Test fun rejectedRemoteReadReportsPartialWithoutInventingEmptyLikedPlaylist() = runBlocking {
        val app = controller(object : Source() {
            override suspend fun liked(limit: Int, offset: Int): SyncResult<SpotifyLikedSongsPage> = SyncResult.Error("synthetic auth error", httpCode = 401)
        })
        app.synchronize(DesktopSyncOptions(playlists = false), youtube = false)
        val result = withTimeout(5000) { app.syncState.first { !it.running && it.label.isNotEmpty() } }
        Assert.assertEquals("Sincronização incompleta", result.label)
        Assert.assertTrue(result.warnings.isNotEmpty())
        Assert.assertEquals(emptyList<SavedPlaylist>(), app.store.load().playlists)
        Assert.assertEquals(1, app.store.load().tracks.size)
    }
}

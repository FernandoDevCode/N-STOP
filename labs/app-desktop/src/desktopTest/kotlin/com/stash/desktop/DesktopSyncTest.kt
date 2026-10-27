package com.stash.desktop

import com.stash.core.model.SyncResult
import com.stash.data.spotify.*
import com.stash.data.spotify.model.*
import com.stash.data.ytmusic.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DesktopSyncTest {
    private fun playlist(id: String) = SpotifyPlaylistItem(id, "Mesmo nome", SpotifyOwner("owner"))
    private fun track(id: String) = SpotifyTrackItem(SpotifyTrackObject(id, "Faixa $id", listOf(SpotifyArtist("artist", "Artista")), uri = "spotify:track:$id"))
    @Test fun spotifyUsesRawCountsAndFoldersAndKeepsPartialReadsNonDestructive() = runBlocking {
        val pages = mutableListOf<Pair<String?, Int>>()
        val likes = mutableListOf<Int>()
        val source = object : SpotifySyncSource {
            override suspend fun playlists(limit: Int, offset: Int, folder: String?): SpotifyLibraryPage {
                pages.add(folder to offset)
                return when {
                    folder == null && offset == 0 -> SpotifyLibraryPage(listOf(playlist("a"), playlist("unavailable")), listOf("folder"), 50)
                    folder == null -> SpotifyLibraryPage(emptyList(), emptyList(), 0)
                    else -> SpotifyLibraryPage(listOf(playlist("b")), listOf("folder"), 1)
                }
            }
            override suspend fun liked(limit: Int, offset: Int): SyncResult<SpotifyLikedSongsPage> {
                likes.add(offset)
                return SyncResult.Success(if (offset == 0) SpotifyLikedSongsPage(emptyList(), 50, 51)
                    else SpotifyLikedSongsPage(listOf(track("liked")), 1, 51))
            }
            override suspend fun tracks(id: String): SyncResult<SpotifyPlaylistTracks> = when (id) {
                "unavailable" -> SyncResult.Error("private transport diagnostics must not enter the report")
                else -> SyncResult.Success(SpotifyPlaylistTracks(listOf(track("shared")), partial = id == "a"))
            }
            override suspend fun mixes() = SyncResult.Empty("disabled")
        }
        val result = DesktopLibrarySync().spotify(source, DesktopSyncOptions())
        assertEquals(listOf(0, 50), likes)
        assertEquals(listOf(null to 0, null to 50, "folder" to 0), pages)
        assertEquals(setOf("liked", "a", "b"), result.snapshot.playlists.map { it.sourceId }.toSet())
        assertEquals(2, result.snapshot.tracks.size)
        assertTrue(result.partial)
        assertFalse(result.warnings.any { it.contains("private transport") })
        val current = LibrarySnapshot(listOf(SavedTrack(90, "Anterior", "Artista", spotifyUri = "spotify:track:old")),
            listOf(SavedPlaylist(1, "Playlist indisponível", "SPOTIFY", "CUSTOM", trackIds = listOf(90), sourceId = "unavailable")))
        val merged = mergeLibrary(current, result.snapshot)
        assertEquals(listOf(90L), merged.playlists.first { it.sourceId == "unavailable" }.trackIds)
        assertEquals(merged, mergeLibrary(merged, result.snapshot))
    }
    @Test fun youtubePropagatesPartialFlagsAndKeepsEmptyPlaylistIdentity() = runBlocking {
        val source = object : YouTubeSyncSource {
            override suspend fun playlists() = SyncResult.Success(PagedPlaylists(listOf(YTMusicPlaylist("empty", "Vazia")), partial = true))
            override suspend fun liked() = SyncResult.Success(PagedTracks(listOf(YTMusicTrack("abcdefghijk", "Faixa", "Artista")), partial = true))
            override suspend fun tracks(id: String) = SyncResult.Empty("empty playlist")
            override suspend fun mixes() = SyncResult.Empty("disabled")
            override suspend fun albums() = SyncResult.Empty("disabled")
            override suspend fun album(id: String): AlbumDetail = error("disabled")
        }
        val result = DesktopLibrarySync().youtube(source, DesktopSyncOptions())
        assertTrue(result.partial)
        assertEquals(2, result.warnings.size)
        assertEquals(emptyList<Long>(), result.snapshot.playlists.first { it.sourceId == "empty" }.trackIds)
        assertTrue(result.snapshot.tracks.single().liked)
    }
    @Test fun stableRemoteIdentitySurvivesRenameWithoutCollapsingEqualPlaylistNames() {
        val current = LibrarySnapshot(playlists = listOf(SavedPlaylist(7, "Antigo", "SPOTIFY", "CUSTOM", sourceId = "a")))
        val incoming = LibrarySnapshot(playlists = listOf(
            SavedPlaylist(1, "Novo", "SPOTIFY", "CUSTOM", sourceId = "a"),
            SavedPlaylist(2, "Novo", "SPOTIFY", "CUSTOM", sourceId = "b")))
        val merged = mergeLibrary(current, incoming)
        assertEquals(2, merged.playlists.size)
        assertEquals(7L, merged.playlists.first { it.sourceId == "a" }.id)
        assertEquals("Novo", merged.playlists.first { it.sourceId == "a" }.name)
    }
    @Test fun cancellationCannotProduceACompletedLibrarySnapshot() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val source = object : SpotifySyncSource {
            override suspend fun playlists(limit: Int, offset: Int, folder: String?): SpotifyLibraryPage = error("not reached")
            override suspend fun liked(limit: Int, offset: Int): SyncResult<SpotifyLikedSongsPage> { entered.complete(Unit); awaitCancellation() }
            override suspend fun tracks(id: String): SyncResult<SpotifyPlaylistTracks> = error("not reached")
            override suspend fun mixes(): SyncResult<List<SpotifyPlaylistItem>> = error("not reached")
        }
        val job = async { DesktopLibrarySync().spotify(source, DesktopSyncOptions()) }
        entered.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}

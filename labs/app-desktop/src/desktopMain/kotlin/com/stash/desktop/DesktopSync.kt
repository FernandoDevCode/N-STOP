package com.stash.desktop

import com.stash.core.model.SyncResult
import com.stash.data.spotify.*
import com.stash.data.spotify.model.*
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class DesktopSyncOptions(val liked: Boolean = true, val playlists: Boolean = true,
    val mixes: Boolean = false, val albums: Boolean = false)
data class ServiceLibrary(val snapshot: LibrarySnapshot, val warnings: List<String> = emptyList()) {
    val partial get() = warnings.isNotEmpty()
}
interface SpotifySyncSource {
    suspend fun playlists(limit: Int, offset: Int, folder: String?): SpotifyLibraryPage
    suspend fun liked(limit: Int, offset: Int): SyncResult<SpotifyLikedSongsPage>
    suspend fun tracks(id: String): SyncResult<SpotifyPlaylistTracks>
    suspend fun mixes(): SyncResult<List<SpotifyPlaylistItem>>
}
class OriginalSpotifySyncSource(private val api: SpotifyApiClient) : SpotifySyncSource {
    override suspend fun playlists(limit: Int, offset: Int, folder: String?) = api.getUserPlaylists(limit, offset, folder)
    override suspend fun liked(limit: Int, offset: Int) = api.getLikedSongs(limit, offset)
    override suspend fun tracks(id: String) = api.getPlaylistTracks(id)
    override suspend fun mixes() = api.getDailyMixes()
}
interface YouTubeSyncSource {
    suspend fun playlists(): SyncResult<PagedPlaylists>
    suspend fun liked(): SyncResult<PagedTracks>
    suspend fun tracks(id: String): SyncResult<PagedTracks>
    suspend fun mixes(): SyncResult<List<YTMusicPlaylist>>
    suspend fun albums(): SyncResult<PagedAlbums>
    suspend fun album(id: String): AlbumDetail
}
class OriginalYouTubeSyncSource(private val api: YTMusicApiClient) : YouTubeSyncSource {
    override suspend fun playlists() = api.getUserPlaylists()
    override suspend fun liked() = api.getLikedSongs()
    override suspend fun tracks(id: String) = api.getPlaylistTracks(id)
    override suspend fun mixes() = api.getHomeMixes()
    override suspend fun albums() = api.getSavedAlbums()
    override suspend fun album(id: String) = api.getAlbum(id)
}

/** Additive sync: stage data first, preserve every existing local membership on partial reads. */
class DesktopLibrarySync {
    suspend fun spotify(source: SpotifySyncSource, options: DesktopSyncOptions,
        progress: (String) -> Unit = {}): ServiceLibrary {
        val build = SnapshotBuilder("SPOTIFY")
        val warnings = mutableListOf<String>()
        if (options.liked) {
            progress("Spotify · músicas curtidas")
            val likes = mutableListOf<SavedTrack>()
            var offset = 0
            var finished = false
            var received = false
            for (pageIndex in 0 until 4000) {
                currentCoroutineContext().ensureActive()
                when (val result = source.liked(50, offset)) {
                    is SyncResult.Success -> {
                        val page = result.data
                        received = true
                        likes.addAll(page.tracks.mapNotNull { it.track?.savedSpotify() }.map { it.copy(liked = true) })
                        offset += page.rawItemCount
                        if (page.rawItemCount < 50 || page.totalCount?.let { offset >= it } == true) {
                            if (fetchedBelowExpected(offset, page.totalCount)) warnings.add("Spotify: contagem de curtidas abaixo do total informado pelo serviço.")
                            finished = true; break
                        }
                    }
                    is SyncResult.Empty -> { received = true; finished = true; break }
                    is SyncResult.Error -> { warnings.add("Spotify: leitura das curtidas incompleta."); finished = true; break }
                }
            }
            if (!finished) warnings.add("Spotify: limite de paginação de curtidas atingido.")
            if (received) build.playlist("liked", "Músicas curtidas · Spotify", "LIKED_SONGS", null, likes)
        }
        val playlists = linkedMapOf<String, SpotifyPlaylistItem>()
        if (options.playlists) {
            val folders = ArrayDeque<String>().apply { add("") }
            val visited = mutableSetOf<String>()
            while (folders.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val folder = folders.removeFirst()
                if (!visited.add(folder)) continue
                if (visited.size > 1000) { warnings.add("Spotify: limite de pastas atingido."); break }
                var offset = 0
                var finished = false
                for (pageIndex in 0 until 4000) {
                    progress("Spotify · lendo playlists e pastas")
                    val page = source.playlists(50, offset, folder.takeIf { it.isNotEmpty() })
                    page.playlists.forEach { playlists[it.id] = it }
                    folders.addAll(page.folderUris.filter { it !in visited })
                    if (!page.isComplete) { warnings.add("Spotify: listagem de playlists incompleta."); finished = true; break }
                    offset += page.rawItemCount
                    if (page.rawItemCount < 50) { finished = true; break }
                    currentCoroutineContext().ensureActive()
                }
                if (!finished) warnings.add("Spotify: limite de paginação de playlists atingido.")
            }
        }
        val mixIds = mutableSetOf<String>()
        if (options.mixes) when (val result = source.mixes()) {
            is SyncResult.Success -> result.data.forEach { playlists[it.id] = it; mixIds.add(it.id) }
            is SyncResult.Error -> warnings.add("Spotify: não foi possível ler os mixes.")
            is SyncResult.Empty -> Unit
        }
        for ((index, playlist) in playlists.values.withIndex()) {
            currentCoroutineContext().ensureActive()
            progress("Spotify · playlist ${index + 1}/${playlists.size}")
            when (val result = source.tracks(playlist.id)) {
                is SyncResult.Success -> {
                    if (result.data.partial) warnings.add("Spotify: uma playlist foi recebida parcialmente.")
                    build.playlist(playlist.id, playlist.name, if (playlist.id in mixIds) "DAILY_MIX" else "CUSTOM",
                        playlist.images?.firstOrNull()?.url, result.data.tracks.mapNotNull { it.track?.savedSpotify() })
                }
                is SyncResult.Empty -> build.playlist(playlist.id, playlist.name, "CUSTOM", playlist.images?.firstOrNull()?.url, emptyList())
                is SyncResult.Error -> warnings.add("Spotify: não foi possível ler uma playlist; dados anteriores preservados.")
            }
        }
        return ServiceLibrary(build.snapshot(), warnings.distinct())
    }
    suspend fun youtube(source: YouTubeSyncSource, options: DesktopSyncOptions,
        progress: (String) -> Unit = {}): ServiceLibrary {
        val build = SnapshotBuilder("YOUTUBE")
        val warnings = mutableListOf<String>()
        if (options.liked) {
            progress("YouTube Music · músicas curtidas")
            when (val result = source.liked()) {
                is SyncResult.Success -> {
                    if (result.data.partial) warnings.add("YouTube Music: curtidas recebidas parcialmente.")
                    build.playlist("LM", "Músicas curtidas · YouTube Music", "LIKED_SONGS", null,
                        result.data.tracks.map { it.savedYouTube().copy(liked = true) })
                }
                is SyncResult.Empty -> build.playlist("LM", "Músicas curtidas · YouTube Music", "LIKED_SONGS", null, emptyList())
                is SyncResult.Error -> warnings.add("YouTube Music: não foi possível ler as curtidas.")
            }
        }
        val playlists = linkedMapOf<String, YTMusicPlaylist>()
        if (options.playlists) when (val result = source.playlists()) {
            is SyncResult.Success -> {
                result.data.playlists.forEach { playlists[it.playlistId] = it }
                if (result.data.partial) warnings.add("YouTube Music: listagem de playlists incompleta.")
            }
            is SyncResult.Error -> warnings.add("YouTube Music: não foi possível ler as playlists.")
            is SyncResult.Empty -> Unit
        }
        val mixIds = mutableSetOf<String>()
        if (options.mixes) when (val result = source.mixes()) {
            is SyncResult.Success -> result.data.forEach { playlists[it.playlistId] = it; mixIds.add(it.playlistId) }
            is SyncResult.Error -> warnings.add("YouTube Music: não foi possível ler os mixes.")
            is SyncResult.Empty -> Unit
        }
        for ((index, playlist) in playlists.values.withIndex()) {
            currentCoroutineContext().ensureActive()
            progress("YouTube Music · playlist ${index + 1}/${playlists.size}")
            when (val result = source.tracks(playlist.playlistId)) {
                is SyncResult.Success -> {
                    if (result.data.partial) warnings.add("YouTube Music: uma playlist foi recebida parcialmente.")
                    build.playlist(playlist.playlistId, playlist.title, if (playlist.playlistId in mixIds) "DAILY_MIX" else "CUSTOM",
                        playlist.thumbnailUrl, result.data.tracks.map { it.savedYouTube() })
                }
                is SyncResult.Empty -> build.playlist(playlist.playlistId, playlist.title, "CUSTOM", playlist.thumbnailUrl, emptyList())
                is SyncResult.Error -> warnings.add("YouTube Music: não foi possível ler uma playlist; dados anteriores preservados.")
            }
        }
        if (options.albums) when (val result = source.albums()) {
            is SyncResult.Success -> {
                if (result.data.partial) warnings.add("YouTube Music: listagem de álbuns incompleta.")
                for (album in result.data.albums) {
                    currentCoroutineContext().ensureActive(); progress("YouTube Music · álbuns salvos")
                    val detail = source.album(album.id)
                    if (detail.tracks.isEmpty()) warnings.add("YouTube Music: um álbum não retornou faixas.")
                    else build.playlist("album:${album.id}", detail.title, "CUSTOM", detail.thumbnailUrl,
                        detail.tracks.map { it.model().saved() })
                }
            }
            is SyncResult.Error -> warnings.add("YouTube Music: não foi possível ler os álbuns salvos.")
            is SyncResult.Empty -> Unit
        }
        return ServiceLibrary(build.snapshot(), warnings.distinct())
    }
}
private class SnapshotBuilder(private val source: String) {
    private val tracks = linkedMapOf<String, SavedTrack>()
    private val playlists = mutableListOf<SavedPlaylist>()
    fun playlist(id: String, title: String, type: String, art: String?, entries: List<SavedTrack>) {
        val ids = entries.map { track ->
            val key = track.spotifyUri ?: track.youtubeId ?: "${track.title}|${track.artist}|${track.album}"
            val previous = tracks[key]
            val stored = previous?.copy(liked = previous.liked || track.liked) ?: track.copy(id = tracks.size.toLong() + 1)
            tracks[key] = stored
            stored.id
        }
        playlists.add(SavedPlaylist(playlists.size.toLong() + 1, title, source, type, art, ids.distinct(), id))
        require(tracks.size <= 200000 && playlists.size <= 10000) { "A biblioteca excedeu o limite desta sincronização." }
    }
    fun snapshot() = LibrarySnapshot(tracks.values.toList(), playlists)
}
private fun SpotifyTrackObject.savedSpotify(): SavedTrack? {
    if (id.isBlank() && uri.isBlank()) return null
    return SavedTrack(0, name, artists.joinToString(", ") { it.name }, album?.name.orEmpty(), duration_ms,
        art = album?.images?.firstOrNull()?.url, source = "SPOTIFY", spotifyUri = uri.ifBlank { "spotify:track:$id" })
}
private fun YTMusicTrack.savedYouTube() = SavedTrack(0, title, artists, album.orEmpty(), durationMs ?: 0,
    art = thumbnailUrl, source = "YOUTUBE", youtubeId = videoId)

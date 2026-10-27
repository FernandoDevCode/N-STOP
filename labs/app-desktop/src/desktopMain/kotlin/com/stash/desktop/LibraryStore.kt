package com.stash.desktop

import com.stash.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.zip.ZipFile

@Serializable
data class SavedTrack(
    val id: Long, val title: String, val artist: String, val album: String = "",
    val duration: Long = 0, val path: String? = null, val art: String? = null,
    val format: String = "", val source: String = "LOCAL", val youtubeId: String? = null,
    val spotifyUri: String? = null, val liked: Boolean = false,
) {
    fun model() = Track(id = id, title = title, artist = artist, album = album, durationMs = duration,
        filePath = path, albumArtUrl = art, fileFormat = format,
        source = MusicSource.entries.firstOrNull { it.name == source } ?: MusicSource.LOCAL,
        youtubeId = youtubeId, spotifyUri = spotifyUri, isDownloaded = path?.let { File(it).isFile } == true,
        stashLikedAt = if (liked) 1 else null)
}
@Serializable
data class SavedPlaylist(val id: Long, val name: String, val source: String, val type: String,
    val art: String? = null, val trackIds: List<Long> = emptyList(), val sourceId: String = "")
@Serializable
data class LibrarySnapshot(val tracks: List<SavedTrack> = emptyList(), val playlists: List<SavedPlaylist> = emptyList())

/** Separate Desktop data. Reads phone backups; never modifies the source ZIP/database. */
class LibraryStore(val directory: File = File("local-data")) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(directory, "library.json")
    @Synchronized fun load(): LibrarySnapshot {
        if (!file.exists()) return LibrarySnapshot()
        require(file.length() <= 128L * 1024 * 1024) { "Biblioteca Desktop excede 128 MiB" }
        return json.decodeFromString(file.readText())
    }
    @Synchronized fun save(snapshot: LibrarySnapshot) {
        directory.mkdirs()
        val staging = Files.createTempFile(directory.toPath(), "library-", ".tmp")
        try {
            Files.writeString(staging, json.encodeToString(snapshot))
            Files.move(staging, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(staging) }
    }
    fun readPhoneBackup(input: File): LibrarySnapshot {
        if (input.extension.equals("db", true)) return readDatabase(input)
        ZipFile(input).use { zip ->
            val entry = zip.getEntry("stash.db") ?: error("O ZIP não contém stash.db. Exporte a biblioteca pelo app do celular.")
            val staged = Files.createTempFile("nonstop-backup-", ".db")
            try {
                zip.getInputStream(entry).use { source ->
                    Files.newOutputStream(staged).use { target ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val size = source.read(buffer)
                            if (size < 0) break
                            total += size
                            require(total <= 256L * 1024 * 1024) { "Backup excede 256 MiB" }
                            target.write(buffer, 0, size)
                        }
                    }
                }
                return readDatabase(staged.toFile())
            } finally { Files.deleteIfExists(staged) }
        }
    }
    private fun readDatabase(source: File): LibrarySnapshot {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${source.toURI()}?mode=ro").use { db ->
            db.createStatement().use { it.execute("PRAGMA query_only=ON") }
            val tracks = db.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM tracks LIMIT 200001").use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(SavedTrack(rows.getLong("id"), rows.getString("title"), rows.getString("artist"),
                                rows.text("album") ?: "", rows.number("duration_ms"),
                                rows.text("file_path"), rows.text("album_art_url"), rows.text("file_format") ?: "",
                                rows.text("source") ?: "LOCAL", rows.text("youtube_id"), rows.text("spotify_uri"),
                                rows.number("stash_liked_at") > 0))
                        }
                    }.also { require(it.size <= 200000) { "Backup excede 200 mil faixas" } }
                }
            }
            val membership = mutableMapOf<Long, MutableList<Long>>()
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM playlist_tracks ORDER BY position").use { rows ->
                    var count = 0
                    while (rows.next()) {
                        require(++count <= 1000000) { "Backup excede um milhão de vínculos" }
                        if (rows.text("removed_at") == null) membership.getOrPut(rows.getLong("playlist_id")) { mutableListOf() }.add(rows.getLong("track_id"))
                    }
                }
            }
            val playlists = db.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM playlists LIMIT 10001").use { rows ->
                    buildList {
                        while (rows.next()) add(SavedPlaylist(rows.getLong("id"), rows.getString("name"),
                            rows.text("source") ?: "LOCAL", rows.text("type") ?: "CUSTOM",
                            rows.text("art_url")?.substringBefore('|'), membership[rows.getLong("id")] ?: emptyList()))
                    }.also { require(it.size <= 10000) { "Backup excede 10 mil playlists" } }
                }
            }
            return LibrarySnapshot(tracks, playlists)
        }
    }
    private fun ResultSet.text(name: String): String? {
        val names = (1..metaData.columnCount).map { metaData.getColumnName(it) }
        return if (name in names) getString(name) else null
    }
    private fun ResultSet.number(name: String): Long = text(name)?.toLongOrNull() ?: 0
}

/** Merge by original service identity or local file path; preserve existing Desktop entries. */
fun mergeLibrary(current: LibrarySnapshot, incoming: LibrarySnapshot): LibrarySnapshot {
    val tracks = current.tracks.toMutableList()
    val translatedIds = mutableMapOf<Long, Long>()
    fun keys(track: SavedTrack) = buildList {
        track.spotifyUri?.let { add("spotify:$it") }
        track.youtubeId?.let { add("youtube:$it") }
        track.path?.let { add("file:$it") }
    }
    val byKey = mutableMapOf<String, Int>()
    val byMetadata = mutableMapOf<Triple<String, String, String>, Int>()
    fun indexTrack(index: Int) {
        val track = tracks[index]
        val identities = keys(track)
        identities.forEach { byKey[it] = index }
        if (identities.isEmpty()) byMetadata[Triple(track.title, track.artist, track.album)] = index
    }
    tracks.indices.forEach(::indexTrack)
    var nextId = (tracks.maxOfOrNull { it.id } ?: 0) + 1
    incoming.tracks.forEach { item ->
        val identities = keys(item)
        val existingIndex = identities.firstNotNullOfOrNull { byKey[it] }
            ?: if (identities.isEmpty()) byMetadata[Triple(item.title, item.artist, item.album)] else null
        if (existingIndex == null) {
            val inserted = item.copy(id = nextId++)
            translatedIds[item.id] = inserted.id
            tracks.add(inserted)
            indexTrack(tracks.lastIndex)
        } else {
            val existing = tracks[existingIndex]
            translatedIds[item.id] = existing.id
            tracks[existingIndex] = existing.copy(liked = existing.liked || item.liked,
                path = existing.path?.takeIf { File(it).isFile } ?: item.path,
                youtubeId = existing.youtubeId ?: item.youtubeId, spotifyUri = existing.spotifyUri ?: item.spotifyUri,
                art = existing.art ?: item.art, duration = existing.duration.takeIf { it > 0 } ?: item.duration)
            indexTrack(existingIndex)
        }
    }
    val playlists = current.playlists.toMutableList()
    fun playlistKey(list: SavedPlaylist) = if (list.sourceId.isNotBlank()) Triple(list.sourceId, list.source, "remote")
        else Triple(list.name, list.source, list.type)
    val byPlaylist = playlists.mapIndexed { index, list -> playlistKey(list) to index }.toMap().toMutableMap()
    var nextPlaylistId = (playlists.maxOfOrNull { it.id } ?: 0) + 1
    incoming.playlists.forEach { list ->
        val mapped = list.trackIds.mapNotNull(translatedIds::get)
        val key = playlistKey(list)
        val index = byPlaylist[key]
        if (index == null) {
            playlists.add(list.copy(id = nextPlaylistId++, trackIds = mapped))
            byPlaylist[key] = playlists.lastIndex
        }
        else playlists[index] = playlists[index].copy(trackIds = (playlists[index].trackIds + mapped).distinct(),
            name = if (list.sourceId.isNotBlank()) list.name else playlists[index].name,
            art = list.art ?: playlists[index].art)
    }
    return LibrarySnapshot(tracks, playlists)
}

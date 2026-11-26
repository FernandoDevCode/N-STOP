package com.stash.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.Instant

/**
 * Many-to-many join table between [PlaylistEntity] and [TrackEntity].
 *
 * Supports soft-delete via [removedAt]: when non-null the track has been
 * removed from the playlist but its row is retained for sync diffing.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["track_id"]),
    ],
)
data class PlaylistTrackCrossRef(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    val position: Int = 0,

    @ColumnInfo(name = "added_at")
    val addedAt: Instant = Instant.now(),

    @ColumnInfo(name = "removed_at")
    val removedAt: Instant? = null,

    /**
     * True when this membership was added by the user from within Stash
     * (e.g. "Save to Playlist" on a downloaded track), false when added
     * by a sync run from Spotify or YouTube Music. Drives REFRESH-mode
     * survival: [com.stash.core.data.db.dao.PlaylistDao.clearSyncedPlaylistTracks]
     * deletes only sync-added rows so user-added tracks persist across
     * re-syncs. See issue #42.
     */
    @ColumnInfo(name = "locally_added", defaultValue = "0")
    val locallyAdded: Boolean = false,

    /** Null for legacy/unknown timestamps; never substitute download time. */
    @ColumnInfo(name = "remote_added_at")
    val remoteAddedAt: Long? = null,

    /** Earlier positions of repeated appearances; position is the final appearance. */
    @ColumnInfo(name = "repeat_positions")
    val repeatPositions: String? = null,
) {
    fun positions(): List<Int> = decodePlaylistPositions(position, repeatPositions)
}

/** Compact ordered value on a unique membership, not additional track identities. */
fun decodePlaylistPositions(position: Int, earlier: String?): List<Int> =
    (earlier.orEmpty().split(',').mapNotNull { it.toIntOrNull()?.takeIf { p -> p >= 0 && p < position } } + position)
        .distinct().sorted()

fun encodeEarlierPlaylistPositions(positions: List<Int>): String? =
    positions.distinct().sorted().dropLast(1).takeIf { it.isNotEmpty() }?.joinToString(",")

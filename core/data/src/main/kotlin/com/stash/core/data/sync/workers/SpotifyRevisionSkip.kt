package com.stash.core.data.sync.workers

import com.stash.core.data.db.entity.RemotePlaylistSnapshotEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import com.stash.core.model.SyncMode
import com.stash.data.spotify.model.SpotifyPlaylistItem

/**
 * What `playlists.snapshot_id` holds for a Spotify library playlist a sync mirrored completely:
 * the proof that reading it again would change nothing, so the next sync can skip the fetch.
 * Each skipped playlist is one to a few fewer calls to Spotify's unofficial GraphQL API (less 429
 * risk), and a faster sync that uses less data and battery.
 *
 * Spotify's revision alone is not that proof. It moves only when the playlist changes ON SPOTIFY,
 * while a full sync also undoes or acts on what changed HERE: a song removed from the playlist,
 * or deleted from the library, comes back; a recounted song count is reset; switching Accumulate
 * to Refresh drops what accumulated. So the stamp also records what the mirror left behind, and
 * is trusted ([stampToSkipOn]) only while all of it still holds:
 *  - [revision]: the playlist's `revisionId` from the library listing, read BEFORE its tracks were
 *    fetched, so it is never newer than the tracks mirrored. A change on Spotify mid-fetch only
 *    means the next sync reads the playlist again.
 *  - [digest]: an exact fingerprint of the playlist's rows here (live, removed and added-here
 *    memberships, their positions) and its stored song count
 *    ([com.stash.core.data.db.dao.mirrorDigestOf]). Any change to them, by any code path, makes
 *    the next sync read the playlist again. That is why removing a song, deleting one and the
 *    orphan sweep need no hook of their own, and why a path added later can't forget one.
 *  - [syncMode]: Refresh or Accumulate, which decides what a re-read would do.
 *  - [recordedAtMs]: when the mirror was made. Older than [MAX_AGE_MS] and the playlist is read
 *    again anyway: what the stamp can't see (a fix to the diff in an app update, a better row for
 *    a song appearing in the library) still reaches every playlist within a week. The one such
 *    thing a sync acts on often, linking a song's downloaded copy in, the diff does for a skipped
 *    playlist too, with no network (DiffWorker.reconcileSkippedPlaylist).
 *
 * What it can't see by the rows, a hook forgets: a block (a twin row of the song in another
 * playlist is filtered by a full sync) and an unblock (which playlists hold it on Spotify isn't
 * known here), both in [com.stash.core.data.blocklist.BlocklistGuard]; a backup restore (see
 * [com.stash.core.data.db.DatabaseBackupManager]); and "Undo last sync"
 * ([com.stash.core.data.db.dao.SyncUndoDao.forgetSnapshotIdsSyncedSince]). Only a library
 * playlist the user saved is ever stamped ([isStampable]).
 *
 * Bump [VERSION] when a change to the diff must reach every playlist at once: every stamp written
 * before stops matching, so the next sync reads everything.
 */
internal data class SpotifyMirrorStamp(
    val revision: String,
    val syncMode: SyncMode,
    val recordedAtMs: Long,
    val digest: String,
) {
    /** The text stored in `playlists.snapshot_id`; [decode] reads it back. */
    fun encode(): String =
        listOf(VERSION, revision, syncMode.name, recordedAtMs.toString(), digest).joinToString(SEPARATOR)

    companion object {
        /** Format and diff generation; see the class KDoc for when to bump it. */
        const val VERSION = "v1"

        /** Never in a base64 revision id, nor in a digest (hex). */
        private const val SEPARATOR = "|"

        /** How long a mirror is trusted before the playlist is read again regardless. */
        const val MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000

        /**
         * Whether [revision] can be stamped and compared: present, and free of the separator (a
         * revision carrying it would not read back). Anything else just means the playlist is read
         * in full every time, as before skipping existed.
         */
        fun isUsableRevision(revision: String?): Boolean =
            !revision.isNullOrBlank() && !revision.contains(SEPARATOR)

        /**
         * Reads back an [encode]d stamp. Null for anything else: no stamp, another [VERSION], a
         * plain revision, or text that doesn't parse. Null is always the safe answer: the playlist
         * is then read in full.
         */
        fun decode(value: String?): SpotifyMirrorStamp? {
            val parts = value?.split(SEPARATOR) ?: return null
            if (parts.size != 5 || parts[0] != VERSION) return null
            val revision = parts[1].takeIf(::isUsableRevision) ?: return null
            val syncMode = SyncMode.entries.firstOrNull { it.name == parts[2] } ?: return null
            val recordedAtMs = parts[3].toLongOrNull() ?: return null
            val digest = parts[4].takeIf { it.isNotBlank() } ?: return null
            return SpotifyMirrorStamp(revision, syncMode, recordedAtMs, digest)
        }
    }
}

/**
 * The stamp to skip a library playlist's fetch on, or null to read the playlist in full.
 *
 * [stored] is returned as it is only when it vouches for the playlist as it is now: the same
 * revision on Spotify ([listRevision]), the same rows here ([currentDigest]), the same
 * [syncMode], and recorded within [SpotifyMirrorStamp.MAX_AGE_MS] of [nowMs]. A stamp from the
 * future (the clock went back) is not trusted either. Every doubt reads the playlist: an
 * unneeded read costs one fetch, a wrong skip leaves a playlist silently stale.
 */
internal fun stampToSkipOn(
    listRevision: String?,
    stored: String?,
    currentDigest: String?,
    syncMode: SyncMode?,
    nowMs: Long,
): String? {
    if (!SpotifyMirrorStamp.isUsableRevision(listRevision) || currentDigest == null || syncMode == null) return null
    val stamp = SpotifyMirrorStamp.decode(stored) ?: return null
    val age = nowMs - stamp.recordedAtMs
    val trusted = stamp.revision == listRevision &&
        stamp.digest == currentDigest &&
        stamp.syncMode == syncMode &&
        age >= 0 && age < SpotifyMirrorStamp.MAX_AGE_MS
    return if (trusted) stored else null
}

/**
 * Whether a playlist may ever be stamped and skipped: a library playlist the user saved, never
 * one Spotify generates (an id in [com.stash.data.spotify.SpotifyApiClient.SPOTIFY_OWNED_ID_PREFIX]:
 * Discover Weekly, daylist, editorials) and never a row filed as a mix. Whether a generated
 * playlist's revision moves when its personalized content rotates is unverified, and a mix's
 * cover follows its first track on every full diff, which a skip wouldn't.
 */
internal fun isStampable(sourceId: String, type: PlaylistType): Boolean =
    !sourceId.startsWith(com.stash.data.spotify.SpotifyApiClient.SPOTIFY_OWNED_ID_PREFIX) &&
        type != PlaylistType.DAILY_MIX

/**
 * The revision a fetched library playlist's snapshot carries, which the diff stamps onto the
 * playlist once it has mirrored the tracks; null when this fetch must not be stamped.
 *
 * The LISTING's revision, read before the fetch, and only when the fetch read the same one
 * ([fetchedRevision], from the walk's first page): a playlist that changed on Spotify between
 * the two reads is mirrored but not stamped, so the next sync reads it again. And only for a
 * walk Spotify's own track count verified
 * ([com.stash.data.spotify.SpotifyPlaylistTracks.countVerified]). A short fetch that slipped
 * through as complete is mirrored today and healed by the next sync's read; stamped, the next
 * syncs would skip it and it would stay short for up to [SpotifyMirrorStamp.MAX_AGE_MS].
 */
internal fun revisionToRecord(listRevision: String?, countVerified: Boolean, fetchedRevision: String?): String? =
    listRevision?.takeIf {
        countVerified && it == fetchedRevision && SpotifyMirrorStamp.isUsableRevision(it)
    }

/**
 * The snapshot row of a Spotify library playlist, as the fetch step writes it for the diff.
 * Shared by a fetched playlist and one whose fetch was skipped, so the two can't drift apart in
 * how the diff sees the playlist itself (name, type, cover).
 */
internal fun spotifyLibraryPlaylistSnapshot(
    playlist: SpotifyPlaylistItem,
    syncId: Long,
    partial: Boolean,
    snapshotId: String?,
): RemotePlaylistSnapshotEntity = RemotePlaylistSnapshotEntity(
    syncId = syncId,
    source = MusicSource.SPOTIFY,
    sourcePlaylistId = playlist.id,
    playlistName = playlist.name,
    playlistType = PlaylistType.CUSTOM,
    trackCount = playlist.tracks?.total ?: 0,
    artUrl = com.stash.core.common.ArtUrlUpgrader.upgrade(playlist.images?.firstOrNull()?.url),
    partial = partial,
    expectedCount = playlist.tracks?.total,
    snapshotId = snapshotId,
)

/**
 * The snapshot row for a library playlist whose fetch this sync skipped ([stampToSkipOn] vouched
 * for it), with no track rows.
 *
 * Written so the playlist stays in this run's inventory: the diff still finds its row (renaming
 * it, re-filing one a mix pass had claimed, showing it again if it was hidden) and counts it as
 * checked. Then:
 *  - its snapshotId is the [stamp] itself, so the diff's "unchanged" check matches the playlist's
 *    own and leaves its tracks exactly as they are;
 *  - it is marked partial. Should that check ever miss (the stamp was forgotten between the two
 *    steps, or the row was deleted and is made again), the diff takes this for the incomplete
 *    fetch it is: nothing is cleared, nothing is recorded, and the next sync reads the playlist in
 *    full. A skipped playlist is never mirrored down to the zero tracks this row carries.
 */
internal fun unchangedSpotifyPlaylistSnapshot(
    playlist: SpotifyPlaylistItem,
    stamp: String,
    syncId: Long,
): RemotePlaylistSnapshotEntity =
    spotifyLibraryPlaylistSnapshot(playlist, syncId, partial = true, snapshotId = stamp)

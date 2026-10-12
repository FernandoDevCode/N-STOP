package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.blocklist.BlockSource
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.sync.TrackMatcher
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [PlaylistDao.mirrorDigest] is what makes a Spotify playlist's mirror stamp
 * stop matching once the playlist changes HERE, so the next sync reads it
 * again and does what a full sync would (re-add a song, reset a count). It
 * replaces a hook at every path that changes a playlist's rows, so each of
 * those paths' row changes must move it; and it must hold still otherwise, or
 * nothing would ever be skipped.
 *
 * Each test drives the same DAO primitive the real path uses (the repository
 * method named in the test) on a real in-memory database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PlaylistDaoMirrorDigestTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: PlaylistDao
    private lateinit var trackDao: TrackDao

    private var playlistId = 0L
    private val trackIds = mutableListOf<Long>()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.playlistDao()
        trackDao = db.trackDao()

        playlistId = dao.insert(
            PlaylistEntity(name = "Road Trip", source = MusicSource.SPOTIFY, sourceId = "pl1", type = PlaylistType.CUSTOM),
        )
        repeat(3) { i ->
            val id = trackDao.insert(
                TrackEntity(
                    title = "Song $i", artist = "Artist",
                    canonicalTitle = "song $i", canonicalArtist = "artist",
                    spotifyUri = "spotify:track:t$i",
                ),
            )
            trackIds += id
            dao.insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = id, position = i))
        }
        dao.updateTrackCount(playlistId, 3)
    }

    @After fun tearDown() = db.close()

    private suspend fun digest() = dao.mirrorDigest(playlistId)!!

    @Test fun `the digest holds still while nothing changes`() = runTest {
        val before = digest()

        // Another playlist's rows and a song's own details are not this playlist's rows.
        val other = dao.insert(PlaylistEntity(name = "Other", source = MusicSource.SPOTIFY, sourceId = "pl2"))
        dao.insertCrossRef(PlaylistTrackCrossRef(playlistId = other, trackId = trackIds[0], position = 0))
        trackDao.updateAlbumArtUrl(trackIds[1], "https://i.scdn.co/image/new-cover")

        assertEquals(before, digest())
    }

    @Test fun `removing a song from the playlist moves the digest`() = runTest {
        val before = digest()

        // MusicRepository.removeTrackFromPlaylist: a removal the sync must keep honoring.
        dao.softDeleteTrackFromPlaylist(playlistId, trackIds[1])

        assertNotEquals(before, digest())
    }

    @Test fun `taking a song out without a removal mark moves the digest`() = runTest {
        val before = digest()

        // removeTrackFromPlaylistAndMaybeDelete, step 1: a full sync puts this one back.
        dao.removeTrackFromPlaylist(playlistId, trackIds[1])

        assertNotEquals(before, digest())
    }

    @Test fun `adding a song here moves the digest`() = runTest {
        val before = digest()
        val added = trackDao.insert(TrackEntity(title = "Mine", artist = "Me", canonicalTitle = "mine", canonicalArtist = "me"))

        // addTrackToPlaylist / addTracksToPlaylist: a song the user saved to this playlist.
        dao.insertCrossRef(
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = added, position = dao.getNextPosition(playlistId), locallyAdded = true),
        )

        assertNotEquals(before, digest())
    }

    @Test fun `deleting a song from the library moves the digest`() = runTest {
        val before = digest()

        // deleteTrack, and removeTrackFromPlaylistAndMaybeDelete's last step: the
        // membership goes with the row (FK cascade) and a full sync re-adds the song.
        trackDao.deleteById(trackIds[2])

        assertNotEquals(before, digest())
    }

    @Test fun `blocking a song moves the digest`() = runTest {
        val before = digest()
        val guard = BlocklistGuard(
            database = db,
            blocklistDao = db.trackBlocklistDao(),
            trackDao = trackDao,
            playlistDao = dao,
            downloadQueueDao = db.downloadQueueDao(),
            fileDeleter = mockk(relaxed = true),
            matcher = TrackMatcher(),
            canonicalRecompute = mockk(relaxed = true),
        )

        guard.block(trackDao.getById(trackIds[0])!!, BlockSource.OTHER)

        assertNotEquals(before, digest())
    }

    @Test fun `a song swept as an orphan takes its removal mark along, and that moves the digest`() = runTest {
        // Removed from the playlist (a tombstone), downloaded, held by nothing else:
        // the orphan sweep deletes it, the tombstone cascades away, and a full sync
        // would then re-add the song. The stamp must stop matching for that.
        dao.softDeleteTrackFromPlaylist(playlistId, trackIds[1])
        trackDao.markAsDownloaded(trackIds[1], filePath = "/music/song1.mp3", fileSizeBytes = 1_000)
        val afterRemoval = digest()

        val swept = trackDao.deleteOrphanedDownloadedTracks()

        assertEquals(listOf(trackIds[1]), swept.deleted.map { it.id })
        assertNotEquals(afterRemoval, digest())
    }

    @Test fun `a recount moves the digest`() = runTest {
        val before = digest()

        // The recount add, remove and block each make; a full sync resets it.
        dao.updateTrackCount(playlistId, 2)

        assertNotEquals(before, digest())
    }

    @Test fun `a re-order moves the digest`() = runTest {
        val before = digest()

        dao.insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackIds[0], position = 2))
        dao.insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackIds[2], position = 0))

        assertNotEquals(before, digest())
    }

    // ── Exact, not summed: changes that sums let cancel out ──────────────

    private fun rows(vararg trackAtPosition: Pair<Long, Int>) =
        trackAtPosition.map { (track, position) -> MirrorRow(1L, track, position, removed = false, locallyAdded = false) }

    @Test fun `a re-order that keeps every sum still moves the digest`() {
        // Tracks (11, 10, 12) vs (10, 12, 11) at positions 0..2: same count, sum
        // and position-weighted sum (67), different playlists.
        assertNotEquals(
            mirrorDigestOf(3, rows(11L to 0, 10L to 1, 12L to 2)),
            mirrorDigestOf(3, rows(10L to 0, 12L to 1, 11L to 2)),
        )
    }

    @Test fun `a remove and an add that keep every sum still move the digest`() {
        // Remove {5@0, 9@2}, add {6@3, 8@0}: the totals come out the same.
        val before = rows(5L to 0, 7L to 1, 9L to 2)
        val after = rows(8L to 0, 7L to 1, 6L to 3)
        assertNotEquals(mirrorDigestOf(3, before), mirrorDigestOf(3, after))
    }

    @Test fun `the digest doesn't depend on the order rows are read in, and stays short`() {
        val a = mirrorDigestOf(3, rows(1L to 0, 2L to 1, 3L to 2))
        assertEquals(a, mirrorDigestOf(3, rows(3L to 2, 1L to 0, 2L to 1)))
        assertEquals(32, a.length)
    }

    @Test fun `a swap of two songs in the playlist moves the digest`() = runTest {
        val before = digest()
        dao.insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackIds[0], position = 1))
        dao.insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackIds[1], position = 0))

        assertNotEquals(before, digest())
    }

    @Test fun `an undo that puts back the very same rows leaves the digest as it was`() = runTest {
        // "Undo last sync" after a sync that skipped this playlist: its rows are
        // cleared and restored from the point, unchanged, so its stamp still holds.
        val before = digest()
        val undo = db.syncUndoDao()
        undo.capture(syncId = 1L, nowEpochMs = System.currentTimeMillis())

        undo.restore(1L)

        assertEquals(before, digest())
    }

    @Test fun `a playlist with no rows still has a digest`() = runTest {
        val empty = dao.insert(PlaylistEntity(name = "Empty", source = MusicSource.SPOTIFY, sourceId = "pl-empty"))

        assertEquals(mirrorDigestOf(0, emptyList()), dao.mirrorDigest(empty))
        assertNull(dao.mirrorDigest(-1L))
    }

    @Test fun `stamps are listed only for Spotify playlists that carry one, each with its digest now`() = runTest {
        dao.updateSnapshotId(playlistId, "v1|rev|REFRESH|1|x")
        dao.insert(PlaylistEntity(name = "Unstamped", source = MusicSource.SPOTIFY, sourceId = "pl-none"))
        val youtube = dao.insert(PlaylistEntity(name = "YT", source = MusicSource.YOUTUBE, sourceId = "PLyt"))
        dao.updateSnapshotId(youtube, "v1|rev|REFRESH|1|x")

        val rows = dao.spotifyMirrorStamps()

        assertEquals(listOf(SpotifyMirrorStampRow("pl1", "v1|rev|REFRESH|1|x", digest())), rows)
    }

    @Test fun `forgetting clears every Spotify stamp and nothing else`() = runTest {
        dao.updateSnapshotId(playlistId, "v1|rev|REFRESH|1|x")
        val youtube = dao.insert(PlaylistEntity(name = "YT", source = MusicSource.YOUTUBE, sourceId = "PLyt"))
        dao.updateSnapshotId(youtube, "kept")

        assertEquals(1, dao.forgetSpotifyMirrorStamps())

        assertNull(dao.getSnapshotId(playlistId))
        assertEquals("kept", dao.getSnapshotId(youtube))
    }
}

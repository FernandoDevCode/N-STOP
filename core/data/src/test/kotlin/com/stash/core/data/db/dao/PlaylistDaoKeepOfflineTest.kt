package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The playlist page's Download switch (#474): `playlists.keep_offline`. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PlaylistDaoKeepOfflineTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: PlaylistDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.playlistDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `setKeepOffline round-trips and leaves sync_enabled alone`() = runTest {
        val id = insert("Release Radar", syncEnabled = true)
        assertThat(dao.getById(id)!!.keepOffline).isFalse()

        dao.setKeepOffline(id, true)
        assertThat(dao.getById(id)!!.keepOffline).isTrue()
        assertThat(dao.getById(id)!!.syncEnabled).isTrue()

        dao.setKeepOffline(id, false)
        assertThat(dao.getById(id)!!.keepOffline).isFalse()
    }

    @Test fun `the keep-offline ids are the active playlists with the switch on`() = runTest {
        val kept = insert("Kept")
        val hidden = insert("Kept but hidden", isActive = false)
        insert("Not kept")
        // Synced is not kept: counting it would queue every synced playlist after each sync (#368).
        insert("Synced, not kept", syncEnabled = true)
        dao.setKeepOffline(kept, true)
        dao.setKeepOffline(hidden, true)

        assertThat(dao.getKeepOfflinePlaylistIds()).containsExactly(kept)
    }

    @Test fun `aggregate likes covers future imports and preserves individual collection choices`() = runTest {
        val local = dao.ensurePlaylist(PlaylistEntity(name = "Liked Songs", source = MusicSource.BOTH,
            sourceId = "stash_liked_songs", type = PlaylistType.STASH_LIKED))
        dao.setAggregateLikesOffline(true)
        val later = dao.insert(PlaylistEntity(name = "Spotify likes", source = MusicSource.SPOTIFY,
            sourceId = "later-likes", type = PlaylistType.LIKED_SONGS, syncEnabled = true))
        val plain = insert("Not a liked collection")
        val trackId = db.trackDao().insert(com.stash.core.data.db.entity.TrackEntity(title = "New", artist = "Artist"))
        dao.insertCrossRef(com.stash.core.data.db.entity.PlaylistTrackCrossRef(playlistId = later, trackId = trackId))
        db.downloadQueueDao().insert(com.stash.core.data.db.entity.DownloadQueueEntity(trackId = trackId))
        assertThat(dao.getKeepOfflinePlaylistIds()).containsExactly(local, later)
        assertThat(db.trackDao().hasOfflineCollectionClaim(trackId)).isTrue()
        assertThat(db.downloadQueueDao().pendingDiscoveryDownloads().map { it.trackId }).containsExactly(trackId)
        assertThat(dao.isAggregateLikesClaimed(plain)).isFalse()
        dao.setKeepOffline(later, true)
        dao.setAggregateLikesOffline(false)
        assertThat(dao.getKeepOfflinePlaylistIds()).containsExactly(later)
        assertThat(db.trackDao().hasOfflineCollectionClaim(trackId)).isTrue()
        dao.setKeepOffline(later, false)
        assertThat(db.trackDao().hasOfflineCollectionClaim(trackId)).isFalse()
        assertThat(db.downloadQueueDao().pendingDiscoveryDownloads()).isEmpty()
    }

    @Test fun `copy preserves repeated occurrences but owns one track membership`() = runTest {
        val a = db.trackDao().insert(com.stash.core.data.db.entity.TrackEntity(title = "A", artist = "Artist"))
        val b = db.trackDao().insert(com.stash.core.data.db.entity.TrackEntity(title = "B", artist = "Artist"))
        val id = dao.createPlaylistCopy(PlaylistEntity(name = "Copy", source = MusicSource.BOTH,
            sourceId = "custom_copy", type = PlaylistType.CUSTOM), listOf(a, b, a))
        val refs = dao.getCrossRefsForPlaylist(id)
        assertThat(refs).hasSize(2)
        assertThat(refs.single { it.trackId == a }.positions()).containsExactly(0, 2).inOrder()
        assertThat(db.trackDao().countByPlaylist(id, true)).isEqualTo(3)
        assertThat(dao.getNextPosition(id)).isEqualTo(3)
        assertThat(dao.getById(id)!!.trackCount).isEqualTo(3)
    }

    private suspend fun insert(name: String, syncEnabled: Boolean = false, isActive: Boolean = true): Long =
        dao.insert(
            PlaylistEntity(
                name = name,
                source = MusicSource.SPOTIFY,
                sourceId = "spotify:$name",
                type = PlaylistType.DAILY_MIX,
                syncEnabled = syncEnabled,
                isActive = isActive,
            )
        )
}

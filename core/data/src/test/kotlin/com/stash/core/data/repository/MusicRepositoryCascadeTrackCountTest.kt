package com.stash.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editing local composition updates the Library card count without deleting the track. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MusicRepositoryCascadeTrackCountTest {

    private lateinit var db: StashDatabase
    private var playlistId = 0L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.trackDao().insert(track(1L))
        db.trackDao().insert(track(2L))
        playlistId = db.playlistDao().insert(playlist("Road trip", PlaylistType.CUSTOM, "trip").copy(trackCount = 2))
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = 1L, position = 0))
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = playlistId, trackId = 2L, position = 1))
    }

    @After fun tearDown() = db.close()

    @Test fun `removing a local playlist song keeps its liked membership and updates the count`() = runTest {
        val liked = db.playlistDao().insert(playlist("Liked Songs", PlaylistType.LIKED_SONGS, "liked"))
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = liked, trackId = 2L, position = 0))

        val summary = repo().removeTrackFromPlaylistAndMaybeDelete(2L, playlistId, alsoBlacklist = false)

        assertEquals(0, summary.deleted)
        assertEquals(null, db.playlistDao().getCrossRef(playlistId, 2L))
        assertEquals(2L, db.playlistDao().getCrossRef(liked, 2L)?.trackId)
        assertEquals(2L, db.trackDao().getById(2L)?.id)
        assertEquals(1, db.playlistDao().getById(playlistId)?.trackCount)
    }

    @Test fun `removing the last local playlist membership preserves the track and updates the count`() = runTest {
        val summary = repo().removeTrackFromPlaylistAndMaybeDelete(2L, playlistId, alsoBlacklist = false)

        assertEquals(0, summary.deleted)
        assertEquals(null, db.playlistDao().getCrossRef(playlistId, 2L))
        assertEquals(2L, db.trackDao().getById(2L)?.id)
        assertEquals(1, db.playlistDao().getById(playlistId)?.trackCount)
    }

    @Test fun `blocking a song also updates every other playlist that held it`() = runTest {
        val gym = db.playlistDao().insert(playlist("Gym", PlaylistType.CUSTOM, "gym").copy(trackCount = 2))
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = gym, trackId = 1L, position = 0))
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = gym, trackId = 2L, position = 1))
        val guard = BlocklistGuard(
            database = db,
            blocklistDao = db.trackBlocklistDao(),
            trackDao = db.trackDao(),
            playlistDao = db.playlistDao(),
            downloadQueueDao = db.downloadQueueDao(),
            fileDeleter = mockk(relaxed = true),
            matcher = TrackMatcher(),
            canonicalRecompute = mockk(relaxed = true),
        )

        repo(guard).removeTrackFromPlaylistAndMaybeDelete(2L, playlistId, alsoBlacklist = true)

        assertEquals(1, db.playlistDao().getById(playlistId)?.trackCount)
        assertEquals(1, db.playlistDao().getById(gym)?.trackCount)
    }

    private fun track(id: Long) = TrackEntity(
        id = id, title = "T$id", artist = "A$id", source = MusicSource.SPOTIFY,
        canonicalTitle = "t$id", canonicalArtist = "a$id",
    )

    private fun playlist(name: String, type: PlaylistType, sourceId: String) = PlaylistEntity(
        name = name, source = MusicSource.BOTH, sourceId = sourceId, type = type,
    )

    private fun repo(guard: BlocklistGuard = mockk(relaxed = true)) = MusicRepositoryImpl(
        context = mockk(relaxed = true),
        trackDao = db.trackDao(),
        playlistDao = db.playlistDao(),
        syncHistoryDao = mockk(relaxed = true),
        downloadQueueDao = mockk(relaxed = true),
        discoveryQueueDao = mockk(relaxed = true),
        blocklistGuard = guard,
        trackMatcher = mockk(relaxed = true),
        stashMixRecipeDao = mockk(relaxed = true),
        downloadNetworkPreference = mockk(relaxed = true),
        streamingPreference = mockk(relaxed = true),
        localFileOps = mockk(relaxed = true),
        syncPreferencesManager = mockk(relaxed = true),
        singleTrackDownloadEnqueuer = mockk(relaxed = true),
        sharedMixDao = db.sharedMixDao(),
        downloadJobs = com.stash.core.data.sync.DownloadJobRegistry(),
    )
}

package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PlaylistDaoSpotifyDeactivationTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: PlaylistDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.playlistDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `deactivates only active missing Spotify custom playlists`() = runTest {
        val present = insert("present", MusicSource.SPOTIFY, PlaylistType.CUSTOM)
        val missing = insert("missing", MusicSource.SPOTIFY, PlaylistType.CUSTOM)
        val alreadyInactive = insert("inactive", MusicSource.SPOTIFY, PlaylistType.CUSTOM, active = false)
        val liked = insert("liked", MusicSource.SPOTIFY, PlaylistType.LIKED_SONGS)
        val daily = insert("daily", MusicSource.SPOTIFY, PlaylistType.DAILY_MIX)
        val local = insert("local", MusicSource.BOTH, PlaylistType.CUSTOM)
        val youtube = insert("youtube", MusicSource.YOUTUBE, PlaylistType.CUSTOM)

        assertEquals(1, dao.deactivateMissingSpotifyCustomPlaylists(listOf("present"), true))

        assertTrue(dao.getById(present)!!.isActive)
        assertFalse(dao.getById(missing)!!.isActive)
        assertFalse(dao.getById(alreadyInactive)!!.isActive)
        assertTrue(dao.getById(liked)!!.isActive)
        assertTrue(dao.getById(daily)!!.isActive)
        assertTrue(dao.getById(local)!!.isActive)
        assertTrue(dao.getById(youtube)!!.isActive)
    }

    @Test fun `complete empty inventory deactivates every eligible playlist`() = runTest {
        val first = insert("first", MusicSource.SPOTIFY, PlaylistType.CUSTOM)
        val second = insert("second", MusicSource.SPOTIFY, PlaylistType.CUSTOM)
        val liked = insert("liked", MusicSource.SPOTIFY, PlaylistType.LIKED_SONGS)

        assertEquals(2, dao.deactivateMissingSpotifyCustomPlaylists(emptyList(), false))

        assertFalse(dao.getById(first)!!.isActive)
        assertFalse(dao.getById(second)!!.isActive)
        assertTrue(dao.getById(liked)!!.isActive)
    }

    /**
     * `source_id NOT IN (:ids)` bound the whole remote inventory at once and
     * threw "too many SQL variables" past 999 on Android <= 11. The ids to
     * hide are now computed in Kotlin and flipped in bind-sized chunks; this
     * checks the result is exact across more ids and rows than one chunk holds.
     */
    @Test fun `an inventory larger than the bind cap hides exactly the missing playlists`() = runTest {
        val ids = (0 until 900).map { "pl-$it" }
        ids.forEach { insert(it, MusicSource.SPOTIFY, PlaylistType.CUSTOM) }
        // 1,500 current ids: the first 850 local ones plus 650 the library doesn't hold.
        val current = ids.take(850) + (0 until 650).map { "remote-only-$it" }

        assertEquals(50, dao.deactivateMissingSpotifyCustomPlaylists(current, true))
        assertEquals(850, dao.getActivePlaylistsBySource(MusicSource.SPOTIFY).size)

        val yt = (0 until 900).map { "yt-$it" }
        yt.forEach { insert(it, MusicSource.YOUTUBE, PlaylistType.CUSTOM) }
        assertEquals(100, dao.deactivateMissingForSource(MusicSource.YOUTUBE, yt.drop(100) + current))
        assertEquals(800, dao.getActivePlaylistsBySource(MusicSource.YOUTUBE).size)
    }

    private suspend fun insert(
        sourceId: String,
        source: MusicSource,
        type: PlaylistType,
        active: Boolean = true,
    ): Long = dao.insert(
        PlaylistEntity(
            name = sourceId,
            source = source,
            sourceId = sourceId,
            type = type,
            isActive = active,
        )
    )
}

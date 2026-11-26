package com.stash.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV55V56Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun `legacy downloads survive and only requested stubs receive ownership`() {
        val name = "migration-v55v56-test"
        helper.createDatabase(name, 55).use { db ->
            for (id in 1..4) {
                db.execSQL(
                    "INSERT INTO tracks (id, title, artist, album, duration_ms, file_format, quality_kbps, " +
                        "file_size_bytes, source, date_added, play_count, is_downloaded, canonical_title, " +
                        "canonical_artist, match_confidence, match_dismissed, match_flagged, file_path) " +
                        "VALUES (?, ?, 'Artist', '', 1000, 'opus', 160, 0, 'SPOTIFY', 0, 0, ?, ?, 'artist', 0.0, 0, 0, ?)",
                    arrayOf<Any?>(id, "Song $id", if (id == 1) 1 else 0, "song$id",
                        if (id == 1) "/music/legacy.flac" else null),
                )
            }
            db.execSQL("INSERT INTO playlists (id, name, source, source_id, type, is_active, sync_enabled, track_count, date_added) VALUES (9, 'Likes', 'SPOTIFY', 'spotify_liked_songs', 'LIKED_SONGS', 1, 1, 2, 0)")
            db.execSQL("INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at) VALUES (9, 1, 0, 0), (9, 3, 1, 0)")
            db.execSQL("INSERT INTO download_queue (id, track_id, status, search_query, retry_count, failure_type, user_requested, created_at) VALUES (10, 2, 'PENDING', '', 0, 'NONE', 1, 0), (11, 4, 'SKIPPED', '', 0, 'NONE', 1, 0)")
        }

        // Open the old file with the actual generated Room implementation.
        // Room itself validates every table and index on migration, even when
        // the new exported JSON has not yet been copied into test assets.
        val room = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            StashDatabase::class.java, name,
        ).addMigrations(StashDatabase.MIGRATION_55_56).allowMainThreadQueries().build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(56, db.version)
            db.query("SELECT aggregate_likes_offline FROM playlists WHERE id = 9").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT id, offline_requested, is_downloaded, file_path FROM tracks ORDER BY id").use { c ->
                val rows = buildList<List<Any?>> {
                    while (c.moveToNext()) add(listOf<Any?>(c.getLong(0), c.getInt(1), c.getInt(2), c.getString(3)))
                }
                assertEquals(listOf(
                    listOf<Any?>(1L, 1, 1, "/music/legacy.flac"),
                    listOf<Any?>(2L, 1, 0, null),
                    listOf<Any?>(3L, 0, 0, null),
                    listOf<Any?>(4L, 0, 0, null),
                ), rows)
            }
            db.query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = 9").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM download_queue").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
        } finally {
            room.close()
        }
    }
}

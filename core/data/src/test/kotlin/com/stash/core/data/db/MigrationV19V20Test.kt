package com.stash.core.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v19 -> v20 recreates `tracks` to drop `is_blacklisted`. It used to rename
 * the old table away first, which on SQLite 3.26+ (Android 11+) repoints every
 * child foreign key at the renamed table and leaves it dangling after the
 * drop. The recreate now renames the NEW table into place, so the children
 * keep referencing `tracks` — and, since foreign keys are off while
 * migrating, the drop must not take any child rows with it.
 *
 * Robolectric's SQLite may predate 3.26, so this cannot reproduce the
 * dangling reference itself; it pins the result: rows survive and every
 * child's foreign key names `tracks`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV19V20Test {

    private val dbName = "migration-19-20-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migration v19 to v20 keeps tracks, their children, and foreign keys on tracks`() {
        helper.createDatabase(dbName, 19).use { db ->
            db.insert(
                "tracks", SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 7L)
                    put("title", "505")
                    put("artist", "Arctic Monkeys")
                    put("album", "")
                    put("duration_ms", 0L)
                    put("file_format", "opus")
                    put("quality_kbps", 0)
                    put("file_size_bytes", 0L)
                    put("source", "SPOTIFY")
                    put("date_added", 1L)
                    put("play_count", 3)
                    put("is_downloaded", 0)
                    put("canonical_title", "505")
                    put("canonical_artist", "arctic monkeys")
                    put("match_confidence", 0f)
                    put("match_dismissed", 0)
                    put("match_flagged", 0)
                    put("is_blacklisted", 0)
                    put("spotify_uri", "spotify:track:abc")
                },
            )
            db.insert(
                "playlists", SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 1L)
                    put("name", "Liked Songs")
                    put("source", "SPOTIFY")
                    put("source_id", "liked")
                    put("type", "LIKED_SONGS")
                    put("track_count", 1)
                    put("is_active", 1)
                },
            )
            db.insert(
                "playlist_tracks", SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("playlist_id", 1L)
                    put("track_id", 7L)
                    put("position", 0)
                    put("added_at", 1L)
                },
            )
            db.insert(
                "listening_events", SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("track_id", 7L)
                    put("started_at", 1L)
                    put("scrobbled", 0)
                },
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 20, true, StashDatabase.MIGRATION_19_20)

        migrated.query("SELECT spotify_uri, play_count FROM tracks WHERE id = 7").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("spotify:track:abc", c.getString(0))
            assertEquals(3, c.getInt(1))
        }
        migrated.query("SELECT COUNT(*) FROM playlist_tracks WHERE track_id = 7").use { c ->
            c.moveToFirst(); assertEquals("membership must survive the drop", 1, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM listening_events WHERE track_id = 7").use { c ->
            c.moveToFirst(); assertEquals("history must survive the drop", 1, c.getInt(0))
        }
        for (child in listOf("playlist_tracks", "download_queue", "listening_events", "track_tags")) {
            val targets = mutableListOf<String>()
            migrated.query("PRAGMA foreign_key_list(`$child`)").use { c ->
                val table = c.getColumnIndexOrThrow("table")
                while (c.moveToNext()) targets += c.getString(table)
            }
            assertEquals("$child must still reference tracks", true, "tracks" in targets)
            assertEquals("$child must not reference a leftover table", false, targets.any { it.startsWith("tracks_") })
        }
    }
}

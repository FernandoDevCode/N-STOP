package com.stash.feature.home.audiobooks

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class SavedAudiobookRecord(
    val videoId: String,
    val title: String,
    val chapterCount: Int,
)

/** Tiny local SQLite store: no Room-wide migration, remote service or user account. */
internal class AudiobookChapterStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "sonora_audiobook_chapters.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE audiobook_recordings (
                video_id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE audiobook_chapters (
                video_id TEXT NOT NULL,
                chapter_number INTEGER NOT NULL,
                title TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER,
                end_estimated INTEGER NOT NULL,
                confidence TEXT NOT NULL DEFAULT 'proposed',
                PRIMARY KEY (video_id, chapter_number),
                FOREIGN KEY(video_id) REFERENCES audiobook_recordings(video_id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX audiobook_chapters_video ON audiobook_chapters(video_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migration needed for v1. Future versions must preserve saved chapters.
        require(oldVersion == newVersion)
    }

    fun save(videoId: String, title: String, chapters: List<AudiobookChapter>) {
        require(Regex("[A-Za-z0-9_-]{11}").matches(videoId))
        require(chapters.isNotEmpty() && chapters.size <= 160)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val record = ContentValues().apply {
                put("video_id", videoId)
                put("title", title.trim().take(180).ifBlank { "Audiolivro do YouTube" })
                put("updated_at_ms", System.currentTimeMillis())
            }
            db.insertWithOnConflict("audiobook_recordings", null, record, SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("audiobook_chapters", "video_id=?", arrayOf(videoId))
            chapters.forEach { chapter ->
                val row = ContentValues().apply {
                    put("video_id", videoId)
                    put("chapter_number", chapter.number)
                    put("title", chapter.title)
                    put("start_ms", chapter.startMs)
                    if (chapter.endMs == null) putNull("end_ms") else put("end_ms", chapter.endMs)
                    put("end_estimated", if (chapter.endEstimated) 1 else 0)
                    put("confidence", "proposed")
                }
                db.insertOrThrow("audiobook_chapters", null, row)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadChapters(videoId: String): List<AudiobookChapter> {
        val result = mutableListOf<AudiobookChapter>()
        readableDatabase.query(
            "audiobook_chapters",
            arrayOf("chapter_number", "title", "start_ms", "end_ms", "end_estimated"),
            "video_id=?", arrayOf(videoId), null, null, "chapter_number ASC", "160",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += AudiobookChapter(
                    number = cursor.getInt(0),
                    title = cursor.getString(1),
                    startMs = cursor.getLong(2),
                    endMs = if (cursor.isNull(3)) null else cursor.getLong(3),
                    endEstimated = cursor.getInt(4) == 1,
                )
            }
        }
        return result
    }

    fun listSaved(): List<SavedAudiobookRecord> {
        val result = mutableListOf<SavedAudiobookRecord>()
        readableDatabase.rawQuery("""
            SELECT r.video_id, r.title, COUNT(c.chapter_number)
            FROM audiobook_recordings r
            LEFT JOIN audiobook_chapters c ON c.video_id=r.video_id
            GROUP BY r.video_id, r.title, r.updated_at_ms
            ORDER BY r.updated_at_ms DESC LIMIT 60
        """.trimIndent(), null).use { cursor ->
            while (cursor.moveToNext()) {
                result += SavedAudiobookRecord(cursor.getString(0), cursor.getString(1), cursor.getInt(2))
            }
        }
        return result
    }
}

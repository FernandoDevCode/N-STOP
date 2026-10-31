package com.stash.desktop

import org.junit.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun phoneBackupImportReadsRealSqliteAndNeverChangesSource() {
        val database = folder.newFile("stash.db")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE tracks(id INTEGER, title TEXT, artist TEXT, duration_ms INTEGER, source TEXT, stash_liked_at INTEGER)")
                sql.execute("INSERT INTO tracks VALUES(42, 'Teste', 'Artista', 90000, 'YOUTUBE', 12)")
                sql.execute("CREATE TABLE playlists(id INTEGER, name TEXT, source TEXT, type TEXT)")
                sql.execute("INSERT INTO playlists VALUES(5, 'Minha playlist', 'LOCAL', 'CUSTOM')")
                sql.execute("CREATE TABLE playlist_tracks(playlist_id INTEGER, track_id INTEGER, position INTEGER, removed_at INTEGER)")
                sql.execute("INSERT INTO playlist_tracks VALUES(5,42,0,NULL)")
            }
        }
        val bytes = database.readBytes()
        val zip = folder.newFile("backup.zip")
        ZipOutputStream(zip.outputStream()).use { archive -> archive.putNextEntry(ZipEntry("stash.db")); archive.write(bytes); archive.closeEntry() }
        val store = LibraryStore(folder.newFolder())
        val imported = store.readPhoneBackup(zip)
        Assert.assertEquals(42L, imported.tracks.single().id)
        Assert.assertTrue(imported.tracks.single().liked)
        Assert.assertEquals(listOf(42L), imported.playlists.single().trackIds)
        Assert.assertArrayEquals(bytes, database.readBytes())
        val merged = mergeLibrary(LibrarySnapshot(), imported)
        Assert.assertEquals(listOf(merged.tracks.single().id), merged.playlists.single().trackIds)
        store.save(merged)
        Assert.assertEquals(merged, store.load())
        Assert.assertEquals(merged, mergeLibrary(merged, imported))
    }
    @Test fun preferencesSurviveRestartAndClampInvalidValues() {
        val store = LibraryStore(folder.newFolder())
        store.savePreferences(DesktopPreferences(dark = false, amoled = true, fontScale = 9f, volume = -2f))
        Assert.assertEquals(DesktopPreferences(false, true, 1.5f, 0f), store.loadPreferences())
    }
    @Test fun invalidBackupCannotOverwriteExistingLibrary() {
        val store = LibraryStore(folder.newFolder())
        val existing = LibrarySnapshot(listOf(SavedTrack(1, "Original", "Artista")))
        store.save(existing)
        val invalid = folder.newFile("invalid.zip").apply { writeText("not a zip") }
        Assert.assertThrows(Exception::class.java) { store.readPhoneBackup(invalid) }
        Assert.assertEquals(existing, store.load())
    }
    @Test fun distinctServiceIdentitiesNeverCollapseBecauseTitlesMatch() {
        val original = SavedTrack(1, "Mesmo título", "Mesmo artista", spotifyUri = "spotify:track:one")
        val incoming = SavedTrack(1, "Mesmo título", "Mesmo artista", spotifyUri = "spotify:track:two")
        val merged = mergeLibrary(LibrarySnapshot(listOf(original)), LibrarySnapshot(listOf(incoming)))
        Assert.assertEquals(2, merged.tracks.size)
        Assert.assertEquals(merged, mergeLibrary(merged, LibrarySnapshot(listOf(incoming))))
    }
}

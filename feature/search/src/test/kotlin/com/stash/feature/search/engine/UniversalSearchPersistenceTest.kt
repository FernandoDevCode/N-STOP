package com.stash.feature.search.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.stash.feature.search.RecentSearch
import com.stash.feature.search.RecentSearchesStore
import com.stash.feature.search.SearchSourcePreferences
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.CatalogSource.SPOTIFY
import com.stash.feature.search.contract.CatalogSource.YOUTUBE
import com.stash.feature.search.contract.CatalogSource.YOUTUBE_MUSIC
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.recentSearchesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UniversalSearchPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val legacy = RecentSearchesStore(context)

    @Test
    fun `music filter is transient by default and its pinned off state round trips`() = runBlocking {
        val prefs = SearchSourcePreferences(context)
        prefs.save(SearchSourceSelection.Default)
        val scopes = mutableListOf<CoroutineScope>()
        suspend fun session(): UniversalSearchSession {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            scopes.add(scope)
            return UniversalSearchSession(
                scope,
                CatalogSource.entries.associateWith { { _: String -> emptyList() } },
                prefs.read(), prefs::save, UniversalSearchHistory(context, legacy), {}, {},
            )
        }
        try {
            val first = session()
            first.onSourceToggled(SPOTIFY)
            assertEquals(emptySet<CatalogSource>(), first.state.value.selection.enabled)
            assertEquals(setOf(YOUTUBE), first.state.value.selection.activeCatalogs)
            assertEquals(SearchSourceSelection.Default, session().state.value.selection)
            first.onPinToggled()
            assertEquals(SearchSourceSelection(emptySet(), true), session().state.value.selection)
            first.onSourceToggled(SPOTIFY)
            assertEquals(SearchSourceSelection(setOf(SPOTIFY), true), session().state.value.selection)
            assertEquals(CatalogSource.entries.toSet(), session().state.value.selection.activeCatalogs)
            first.onSourceToggled(SPOTIFY)
            scopes.first().cancel()
            val reopened = session()
            assertEquals(SearchSourceSelection(emptySet(), true), reopened.state.value.selection)
            reopened.onRestoreDefaultSources()
            assertEquals(SearchSourceSelection(setOf(SPOTIFY), true), session().state.value.selection)
            reopened.onSourceToggled(SPOTIFY)
            reopened.onPinToggled()
            assertEquals(SearchSourceSelection(emptySet(), false), reopened.state.value.selection)
            assertEquals(SearchSourceSelection.Default, session().state.value.selection)
            reopened.onRestoreDefaultSources()
            assertEquals(SearchSourceSelection.Default, reopened.state.value.selection)
        } finally {
            scopes.forEach { it.cancel() }
            prefs.save(SearchSourceSelection.Default)
            prefs.read()
        }
    }

    @Test
    fun `legacy pinned YouTube Music filter is stored and loaded as Spotify`() = runBlocking {
        val prefs = SearchSourcePreferences(context)
        try {
            prefs.save(SearchSourceSelection(setOf(YOUTUBE_MUSIC), true))
            assertEquals(SearchSourceSelection(setOf(SPOTIFY), true), prefs.read())
            prefs.save(SearchSourceSelection(setOf(YOUTUBE_MUSIC, YOUTUBE), true))
            assertEquals(SearchSourceSelection(setOf(SPOTIFY), true), prefs.read())
            prefs.save(SearchSourceSelection(setOf(YOUTUBE), true))
            assertEquals(SearchSourceSelection(emptySet(), true), prefs.read())
        } finally {
            prefs.save(SearchSourceSelection.Default)
            prefs.read()
        }
    }

    @Test
    fun `history preserves original identity and allows individual removal and clearing`() = runBlocking {
        legacy.clear()
        context.recentSearchesDataStore.edit { it.remove(stringPreferencesKey("universal_entries")) }
        legacy.record(RecentSearch(RecentSearch.Type.QUERY, "old query"))
        legacy.record(RecentSearch(RecentSearch.Type.ARTIST, "Artist", artistId = "UClegacy"))
        val history = UniversalSearchHistory(context, legacy)
        assertEquals(listOf("Artist", "old query"), history.recent.first().map { it.label })
        val youtube = resultHistoryItem("Demo", null, CatalogIdentity(YOUTUBE, "JOvAJJI2Ui8"))
        val ytm = resultHistoryItem("Demo", null, CatalogIdentity(YOUTUBE_MUSIC, "JOvAJJI2Ui8"))
        history.record(youtube)
        history.record(ytm)
        history.record(queryHistoryItem("  DEMO "))
        val replacement = queryHistoryItem("demo")
        history.record(replacement)
        assertEquals(listOf(replacement, ytm, youtube), history.recent.first().take(3))
        history.remove(youtube)
        assertFalse(youtube in history.recent.first())
        assertTrue(ytm in history.recent.first())
        (1..12).forEach { history.record(queryHistoryItem("query $it")) }
        assertEquals(RecentSearchesStore.MAX, history.recent.first().size)
        history.clear()
        assertTrue(history.recent.first().isEmpty())
        assertEquals(2, legacy.recent.first().size)
    }

    @Test
    fun `history keeps new route metadata and restores old YouTube Music destinations`() = runBlocking {
        val historyKey = stringPreferencesKey("universal_entries")
        context.recentSearchesDataStore.edit {
            it[historyKey] = """[{"label":"Old artist","source":"YOUTUBE_MUSIC","catalogId":"MPLAUClegacy"}]"""
        }
        val history = UniversalSearchHistory(context, legacy)
        val old = history.recent.first().single()
        assertEquals(SearchResultType.ARTIST, old.resultType)
        assertEquals("MPLAUClegacy", old.resultIdentity?.catalogId)
        val release = resultHistoryItem(
            "Rare edition", "Artist", CatalogIdentity(YOUTUBE_MUSIC, "opaque-id"),
            SearchResultType.ALBUM, "https://example.test/rare.jpg",
        )
        history.record(release)
        val spotify = resultHistoryItem(
            "New single", "Artist", CatalogIdentity(SPOTIFY, "spotify-id", "spotify:album:spotify-id"),
            SearchResultType.ALBUM, browseIdentity = CatalogIdentity(YOUTUBE_MUSIC, "MPREb_verified"),
        )
        history.record(spotify)
        assertEquals(listOf(spotify, release, old), UniversalSearchHistory(context, legacy).recent.first())
    }
}

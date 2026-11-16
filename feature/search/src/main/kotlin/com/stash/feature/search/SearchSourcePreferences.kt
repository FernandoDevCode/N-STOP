package com.stash.feature.search

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchSourceSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.searchPreferences by preferencesDataStore(name = "universal_search_sources")

@Singleton
class SearchSourcePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.searchPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val pinnedKey = booleanPreferencesKey("pinned")
    private val sourcesKey = stringSetPreferencesKey("sources")

    internal suspend fun read(): SearchSourceSelection = writes.withLock {
        val prefs = try {
            store.data.first()
        } catch (error: IOException) {
            Log.w("UniversalSearch", "Could not read source preferences", error)
            return@withLock SearchSourceSelection.Default
        }
        if (prefs[pinnedKey] != true) return@withLock SearchSourceSelection.Default
        val names = prefs[sourcesKey] ?: return@withLock SearchSourceSelection.Default
        SearchSourceSelection(CatalogSource.entries.filter { it.name in names }.toSet(), isPinned = true)
    }

    internal fun save(selection: SearchSourceSelection) {
        // Enter the write queue before the caller can leave the screen or open another session.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            writes.withLock {
                try {
                    store.edit { prefs ->
                        prefs[pinnedKey] = selection.isPinned
                        if (selection.isPinned) {
                            prefs[sourcesKey] = selection.enabled.map(CatalogSource::name).toSet()
                        } else {
                            prefs.remove(sourcesKey)
                        }
                    }
                } catch (error: IOException) {
                    Log.w("UniversalSearch", "Could not save source preferences", error)
                }
            }
        }
    }
}

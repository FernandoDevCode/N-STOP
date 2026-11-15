package com.stash.feature.search

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stash.feature.search.contract.CatalogSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sonoraSearchSourceStore by preferencesDataStore(name = "sonora_search_source")

data class PinnedSearchSource(val source: CatalogSource = CatalogSource.SPOTIFY, val pinned: Boolean = false)

/** Persisted preference only. Unpinned source selection belongs to the Search VM session. */
@Singleton
class SearchSourcePreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val pinnedKey = booleanPreferencesKey("pinned")
    private val sourceKey = stringPreferencesKey("source")

    val selection: Flow<PinnedSearchSource> = context.sonoraSearchSourceStore.data.map { prefs ->
        // Migrate a previously pinned YTM tab to Spotify without exposing it.
        val source = when (prefs[sourceKey]) {
            CatalogSource.YOUTUBE.name -> CatalogSource.YOUTUBE
            else -> CatalogSource.SPOTIFY
        }
        PinnedSearchSource(source = source, pinned = prefs[pinnedKey] == true)
    }

    suspend fun save(source: CatalogSource, pinned: Boolean) {
        require(source == CatalogSource.SPOTIFY || source == CatalogSource.YOUTUBE) {
            "Only Spotify and conventional YouTube may be pinned in Search"
        }
        context.sonoraSearchSourceStore.edit { prefs ->
            prefs[pinnedKey] = pinned
            prefs[sourceKey] = source.name
        }
    }
}

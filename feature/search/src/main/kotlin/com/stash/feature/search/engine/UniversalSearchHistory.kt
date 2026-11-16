package com.stash.feature.search.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.feature.search.RecentSearch
import com.stash.feature.search.RecentSearchesStore
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.RecentSearchKind
import com.stash.feature.search.recentSearchesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class UniversalSearchHistory @Inject constructor(
    @ApplicationContext context: Context,
    private val legacy: RecentSearchesStore,
) {
    private val store = context.recentSearchesDataStore
    private val key = stringPreferencesKey("universal_entries")

    val recent = store.data.map { it[key] }.combine(legacy.recent) { saved, old ->
        saved?.let(::decodeHistory) ?: old.toUniversalHistory()
    }

    suspend fun record(item: RecentSearchItem) = update {
        (listOf(item) + it.filterNot { previous -> previous.id == item.id }).take(RecentSearchesStore.MAX)
    }

    suspend fun remove(item: RecentSearchItem) = update { entries -> entries.filterNot { it.id == item.id } }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(transform: (List<RecentSearchItem>) -> List<RecentSearchItem>) {
        val old = legacy.recent.first()
        store.edit { prefs ->
            val entries = prefs[key]?.let(::decodeHistory) ?: old.toUniversalHistory()
            prefs[key] = JsonArray(transform(entries).map { entry ->
                JsonObject(buildMap {
                    put("label", JsonPrimitive(entry.label))
                    entry.subtitle?.let { put("subtitle", JsonPrimitive(it)) }
                    entry.resultIdentity?.let {
                        put("source", JsonPrimitive(it.source.name))
                        put("catalogId", JsonPrimitive(it.catalogId))
                        it.catalogUri?.let { uri -> put("catalogUri", JsonPrimitive(uri)) }
                    }
                })
            }).toString()
        }
    }
}

private fun decodeHistory(raw: String): List<RecentSearchItem> {
    val rows = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
    return rows.orEmpty().mapNotNull { row ->
        val item = row as? JsonObject ?: return@mapNotNull null
        fun field(key: String) = (item[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val label = field("label") ?: return@mapNotNull null
        val sourceName = field("source") ?: return@mapNotNull queryHistoryItem(label)
        val source = CatalogSource.entries.firstOrNull { it.name == sourceName } ?: return@mapNotNull null
        val id = field("catalogId") ?: return@mapNotNull null
        resultHistoryItem(label, field("subtitle"), CatalogIdentity(source, id, field("catalogUri")))
    }
}

private fun List<RecentSearch>.toUniversalHistory(): List<RecentSearchItem> = mapNotNull {
    if (it.text.isBlank()) return@mapNotNull null
    when (it.type) {
        RecentSearch.Type.QUERY -> queryHistoryItem(it.text)
        RecentSearch.Type.ARTIST -> it.artistId?.takeIf(String::isNotBlank)?.let { id ->
            resultHistoryItem(it.text, it.subtitle, CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, id))
        }
        // Legacy track rows carry only display text. There is no original ID to reopen.
        RecentSearch.Type.TRACK -> null
    }
}.distinctBy(RecentSearchItem::id)

private val whitespace = Regex("""\s+""")

internal fun queryHistoryItem(query: String) = RecentSearchItem(
    id = "query:${query.trim().replace(whitespace, " ").lowercase()}",
    kind = RecentSearchKind.QUERY,
    label = query.trim(),
)

internal fun resultHistoryItem(label: String, subtitle: String?, identity: CatalogIdentity) = RecentSearchItem(
    id = "result:${identity.source.name}:${identity.catalogId}",
    kind = RecentSearchKind.RESULT,
    label = label,
    subtitle = subtitle?.takeIf { it.isNotBlank() },
    resultIdentity = identity,
)

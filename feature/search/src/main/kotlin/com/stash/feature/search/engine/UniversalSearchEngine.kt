package com.stash.feature.search.engine

import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.RecentSearchItem
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.contract.UniversalSearchUiState

/**
 * Pure aggregation boundary for universal search.
 *
 * Provider execution and score normalization deliberately live outside this class. The engine
 * only accepts already-normalized provider results, rejects cross-source items, and derives the
 * single ordered list described by the shared contract.
 */
object UniversalSearchEngine {
    fun aggregate(
        query: String,
        selection: SearchSourceSelection,
        sourceStates: Map<CatalogSource, SourceSearchState>,
        recentSearches: List<RecentSearchItem> = emptyList(),
    ): UniversalSearchUiState {
        val currentStates = CatalogSource.entries.associateWith { source ->
            val state = sourceStates[source]
            val stateQuery = state?.queryOrNull()
            when {
                source !in selection.enabled -> SourceSearchState.Idle
                state == null -> SourceSearchState.Idle
                stateQuery != null && stateQuery != query -> SourceSearchState.Idle
                else -> state.also { requireItemsBelongTo(source, it) }
            }
        }

        val orderedResults = currentStates
            .asSequence()
            .filter { (source, _) -> source in selection.enabled }
            .flatMap { (_, state) -> state.visibleItems().asSequence() }
            .distinctBy(UniversalSearchResult::stableKey)
            .sortedWith(resultComparator)
            .toList()

        return UniversalSearchUiState(
            query = query,
            selection = selection,
            sourceStates = currentStates,
            orderedResults = orderedResults,
            recentSearches = recentSearches,
        )
    }

    private val resultComparator =
        compareByDescending<UniversalSearchResult> { it.rankingScore }
            .thenBy { it.providerRank }
            .thenBy { sourceOrder.getValue(it.identity.source) }
            .thenBy { it.stableKey }

    private val sourceOrder = mapOf(
        CatalogSource.SPOTIFY to 0,
        CatalogSource.YOUTUBE to 1,
        CatalogSource.YOUTUBE_MUSIC to 2,
    )

    private fun requireItemsBelongTo(source: CatalogSource, state: SourceSearchState) {
        require(state.visibleItems().all { it.identity.source == source }) {
            "$source state may contain only results from that source"
        }
    }

    private fun SourceSearchState.queryOrNull(): String? = when (this) {
        SourceSearchState.Idle -> null
        is SourceSearchState.Loading -> query
        is SourceSearchState.Data -> query
        is SourceSearchState.Empty -> query
        is SourceSearchState.Unavailable -> query
        is SourceSearchState.Error -> query
    }

    private fun SourceSearchState.visibleItems(): List<UniversalSearchResult> = when (this) {
        is SourceSearchState.Data -> items
        is SourceSearchState.Loading -> previous
        else -> emptyList()
    }
}

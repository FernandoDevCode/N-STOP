package com.stash.feature.search.contract

interface UniversalSearchActions {
    fun onQueryChanged(query: String)
    fun onSearchSubmitted()
    fun onClearQuery()
    fun onSourceToggled(source: CatalogSource)
    fun onPinToggled()
    fun onRestoreDefaultSources()
    fun onRetrySource(source: CatalogSource)
    fun onLoadMore(query: String, type: SearchResultType)
    fun onVisibleResultsChanged(query: String, stableKeys: Set<String>)
    fun onResultAction(
        result: UniversalSearchResult,
        action: SearchResultAction,
        displayedArtworkUrl: String? = null,
    )
    fun onRecentSelected(item: RecentSearchItem)
    fun onRecentRemoved(item: RecentSearchItem)
    fun onRecentCleared()
}

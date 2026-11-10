package com.stash.feature.search.contract

interface UniversalSearchActions {
    fun onQueryChanged(query: String)
    fun onSearchSubmitted()
    fun onClearQuery()
    fun onSourceToggled(source: CatalogSource)
    fun onPinToggled()
    fun onRestoreDefaultSources()
    fun onRetrySource(source: CatalogSource)
    fun onConnectSource(source: CatalogSource)
    fun onResultAction(result: UniversalSearchResult, action: SearchResultAction)
    fun onRecentSelected(item: RecentSearchItem)
    fun onRecentRemoved(item: RecentSearchItem)
    fun onRecentCleared()
}

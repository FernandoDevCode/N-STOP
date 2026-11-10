package com.stash.feature.search.contract

enum class CatalogSource {
    SPOTIFY,
    YOUTUBE,
    YOUTUBE_MUSIC,
}

enum class SearchResultType {
    TRACK,
    VIDEO,
    ARTIST,
    ALBUM,
}

enum class SearchResultAction {
    PLAY,
    PLAY_NEXT,
    ADD_TO_QUEUE,
    START_RADIO,
    ADD_TO_PLAYLIST,
    DOWNLOAD,
    NAVIGATE,
}

val DefaultSearchSources: Set<CatalogSource> = setOf(
    CatalogSource.SPOTIFY,
    CatalogSource.YOUTUBE,
)

data class SearchSourceSelection(
    val enabled: Set<CatalogSource> = DefaultSearchSources,
    val isPinned: Boolean = false,
) {
    companion object {
        val Default = SearchSourceSelection()
    }
}

data class CatalogIdentity(
    val source: CatalogSource,
    val catalogId: String,
    val catalogUri: String? = null,
) {
    init {
        require(catalogId.isNotBlank()) { "catalogId must not be blank" }
        require(catalogUri == null || catalogUri.isNotBlank()) {
            "catalogUri must be null or non-blank"
        }
    }
}

data class AudioIdentity(
    val provider: String,
    val audioId: String,
) {
    init {
        require(provider.isNotBlank()) { "provider must not be blank" }
        require(audioId.isNotBlank()) { "audioId must not be blank" }
    }
}

data class UniversalSearchResult(
    val identity: CatalogIdentity,
    val audioIdentity: AudioIdentity? = null,
    val type: SearchResultType,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val isExplicit: Boolean = false,
    val versionLabel: String? = null,
    val providerRank: Int,
    val rankingScore: Double,
    val supportedActions: Set<SearchResultAction> = emptySet(),
) {
    val stableKey: String = "${identity.source.name}:${type.name}:${identity.catalogId}"

    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(subtitle == null || subtitle.isNotBlank()) {
            "subtitle must be null or non-blank"
        }
        require(artworkUrl == null || artworkUrl.isNotBlank()) {
            "artworkUrl must be null or non-blank"
        }
        require(durationMs == null || durationMs >= 0) {
            "durationMs must be null or non-negative"
        }
        require(versionLabel == null || versionLabel.isNotBlank()) {
            "versionLabel must be null or non-blank"
        }
        require(providerRank >= 0) { "providerRank must be non-negative" }
        require(rankingScore.isFinite() && rankingScore in 0.0..1.0) {
            "rankingScore must be finite and between 0.0 and 1.0"
        }
    }

    fun supports(action: SearchResultAction): Boolean = action in supportedActions
}

enum class UnavailableReason {
    AUTHENTICATION_REQUIRED,
    NOT_IMPLEMENTED,
    SERVICE_DISABLED,
}

sealed interface SourceSearchState {
    data object Idle : SourceSearchState

    data class Loading(
        val query: String,
        val previous: List<UniversalSearchResult> = emptyList(),
        val isRetry: Boolean = false,
    ) : SourceSearchState {
        init {
            require(query.isNotBlank()) { "Loading query must not be blank" }
            require(previous.isEmpty() || isRetry) {
                "previous results are allowed only for retry of the same query"
            }
        }
    }

    data class Data(
        val query: String,
        val items: List<UniversalSearchResult>,
    ) : SourceSearchState {
        init {
            require(query.isNotBlank()) { "Data query must not be blank" }
            require(items.isNotEmpty()) { "Data must contain at least one item" }
        }
    }

    data class Empty(val query: String) : SourceSearchState {
        init {
            require(query.isNotBlank()) { "Empty query must not be blank" }
        }
    }

    data class Unavailable(
        val query: String,
        val reason: UnavailableReason,
    ) : SourceSearchState {
        init {
            require(query.isNotBlank()) { "Unavailable query must not be blank" }
        }
    }

    data class Error(
        val query: String,
        val message: String,
        val canRetry: Boolean,
    ) : SourceSearchState {
        init {
            require(query.isNotBlank()) { "Error query must not be blank" }
            require(message.isNotBlank()) { "Error message must not be blank" }
        }
    }
}

enum class RecentSearchKind {
    QUERY,
    RESULT,
}

data class RecentSearchItem(
    val id: String,
    val kind: RecentSearchKind,
    val label: String,
    val subtitle: String? = null,
    val resultIdentity: CatalogIdentity? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
        require(subtitle == null || subtitle.isNotBlank()) {
            "subtitle must be null or non-blank"
        }
        require((kind == RecentSearchKind.QUERY) == (resultIdentity == null)) {
            "QUERY must not have resultIdentity and RESULT must have it"
        }
    }
}

data class UniversalSearchUiState(
    val query: String = "",
    val selection: SearchSourceSelection = SearchSourceSelection.Default,
    val sourceStates: Map<CatalogSource, SourceSearchState> = CatalogSource.entries
        .associateWith { SourceSearchState.Idle },
    val orderedResults: List<UniversalSearchResult> = emptyList(),
    val recentSearches: List<RecentSearchItem> = emptyList(),
) {
    init {
        sourceStates.forEach { (source, state) ->
            state.queryOrNull()?.let { stateQuery ->
                require(stateQuery == query) {
                    "$source state belongs to a different query"
                }
            }
            require(state.visibleItems().all { it.identity.source == source }) {
                "$source state may contain only results from that source"
            }
        }
        require(orderedResults.all { it.identity.source in selection.enabled }) {
            "orderedResults may contain only enabled sources"
        }
        require(orderedResults.map { it.stableKey }.distinct().size == orderedResults.size) {
            "orderedResults must not repeat the same catalog identity and type"
        }

        require(orderedResults.all { result ->
            sourceStates[result.identity.source]
                ?.visibleItems()
                ?.contains(result) == true
        }) {
            "orderedResults must exactly match visible results from their source"
        }
    }

    val hasPartialSuccess: Boolean
        get() = orderedResults.isNotEmpty() && selection.enabled.any { source ->
            sourceStates[source] is SourceSearchState.Error ||
                sourceStates[source] is SourceSearchState.Unavailable
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

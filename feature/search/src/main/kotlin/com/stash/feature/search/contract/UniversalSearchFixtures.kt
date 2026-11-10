package com.stash.feature.search.contract

/**
 * Pure contract fixtures for JVM tests and isolated UI work.
 * They do not represent working providers, network, persistence, or playback.
 */
object UniversalSearchFixtures {
    const val Query = "echoes"

    fun spotifyTrack(
        catalogId: String = "spotify-track-echoes",
        versionLabel: String? = null,
        supportedActions: Set<SearchResultAction> = emptySet(),
        providerRank: Int = 0,
        rankingScore: Double = 0.95,
    ) = UniversalSearchResult(
        identity = CatalogIdentity(
            source = CatalogSource.SPOTIFY,
            catalogId = catalogId,
            catalogUri = "spotify:track:$catalogId",
        ),
        type = SearchResultType.TRACK,
        title = "Echoes",
        subtitle = "Pink Floyd",
        artworkUrl = "https://fixtures.invalid/echoes.jpg",
        durationMs = 1_415_000,
        versionLabel = versionLabel,
        providerRank = providerRank,
        rankingScore = rankingScore,
        supportedActions = supportedActions,
    )

    fun youtubeVideo(
        catalogId: String = "youtube-live-echoes",
        versionLabel: String? = "Live at Pompeii",
        supportedActions: Set<SearchResultAction> = emptySet(),
        providerRank: Int = 0,
        rankingScore: Double = 0.90,
    ) = UniversalSearchResult(
        identity = CatalogIdentity(
            source = CatalogSource.YOUTUBE,
            catalogId = catalogId,
            catalogUri = "https://www.youtube.com/watch?v=$catalogId",
        ),
        type = SearchResultType.VIDEO,
        title = "Echoes",
        subtitle = "Pink Floyd",
        artworkUrl = "https://fixtures.invalid/echoes-live.jpg",
        durationMs = 1_458_000,
        versionLabel = versionLabel,
        providerRank = providerRank,
        rankingScore = rankingScore,
        supportedActions = supportedActions,
    )

    fun youtubeMusicAlbum(
        catalogId: String = "ytm-album-meddle",
        supportedActions: Set<SearchResultAction> = emptySet(),
        providerRank: Int = 0,
        rankingScore: Double = 0.80,
    ) = UniversalSearchResult(
        identity = CatalogIdentity(
            source = CatalogSource.YOUTUBE_MUSIC,
            catalogId = catalogId,
        ),
        type = SearchResultType.ALBUM,
        title = "Meddle",
        subtitle = "Pink Floyd",
        artworkUrl = "https://fixtures.invalid/meddle.jpg",
        providerRank = providerRank,
        rankingScore = rankingScore,
        supportedActions = supportedActions,
    )

    fun partialSuccessState(): UniversalSearchUiState {
        val spotify = spotifyTrack()
        val youtube = youtubeVideo()
        return UniversalSearchUiState(
            query = Query,
            selection = SearchSourceSelection(
                enabled = setOf(
                    CatalogSource.SPOTIFY,
                    CatalogSource.YOUTUBE,
                    CatalogSource.YOUTUBE_MUSIC,
                ),
                isPinned = false,
            ),
            sourceStates = mapOf(
                CatalogSource.SPOTIFY to SourceSearchState.Data(Query, listOf(spotify)),
                CatalogSource.YOUTUBE to SourceSearchState.Data(Query, listOf(youtube)),
                CatalogSource.YOUTUBE_MUSIC to SourceSearchState.Error(
                    query = Query,
                    message = "Fixture failure",
                    canRetry = true,
                ),
            ),
            orderedResults = listOf(spotify, youtube),
        )
    }
}

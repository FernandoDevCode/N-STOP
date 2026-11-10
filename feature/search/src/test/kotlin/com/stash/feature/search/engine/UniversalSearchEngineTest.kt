package com.stash.feature.search.engine

import com.google.common.truth.Truth.assertThat
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UnavailableReason
import com.stash.feature.search.contract.UniversalSearchResult
import org.junit.Test

class UniversalSearchEngineTest {
    @Test
    fun aggregate_interleavesProviderDataIntoOneList() {
        val spotify = result(CatalogSource.SPOTIFY, "spotify-track", score = 0.82)
        val youtube = result(CatalogSource.YOUTUBE, "youtube-video", score = 0.91)

        val state = aggregate(
            states = mapOf(
                CatalogSource.SPOTIFY to data(spotify),
                CatalogSource.YOUTUBE to data(youtube),
            ),
        )

        assertThat(state.orderedResults).containsExactly(youtube, spotify).inOrder()
        assertThat(state.sourceStates[CatalogSource.YOUTUBE_MUSIC])
            .isEqualTo(SourceSearchState.Idle)
    }

    @Test
    fun ordering_usesScoreRankSourceThenStableKey() {
        val lowerScore = result(CatalogSource.SPOTIFY, "lower", score = 0.7, rank = 0)
        val higherRank = result(CatalogSource.YOUTUBE, "higher-rank", score = 0.8, rank = 2)
        val spotifyZ = result(CatalogSource.SPOTIFY, "z", score = 0.8, rank = 1)
        val spotifyA = result(CatalogSource.SPOTIFY, "a", score = 0.8, rank = 1)
        val youtube = result(CatalogSource.YOUTUBE, "a", score = 0.8, rank = 1)
        val youtubeMusic = result(CatalogSource.YOUTUBE_MUSIC, "a", score = 0.8, rank = 1)

        val state = aggregate(
            enabled = CatalogSource.entries.toSet(),
            states = mapOf(
                CatalogSource.SPOTIFY to data(lowerScore, spotifyZ, spotifyA),
                CatalogSource.YOUTUBE to data(higherRank, youtube),
                CatalogSource.YOUTUBE_MUSIC to data(youtubeMusic),
            ),
        )

        assertThat(state.orderedResults).containsExactly(
            spotifyA,
            spotifyZ,
            youtube,
            youtubeMusic,
            higherRank,
            lowerScore,
        ).inOrder()
    }

    @Test
    fun deduplication_removesOnlySameSourceTypeAndCatalogId() {
        val first = result(CatalogSource.YOUTUBE, "same", title = "First")
        val repeatedIdentity = result(CatalogSource.YOUTUBE, "same", title = "Duplicate")
        val differentType = result(
            CatalogSource.YOUTUBE,
            "same",
            type = SearchResultType.TRACK,
            title = "Track identity",
        )
        val differentSource = result(CatalogSource.SPOTIFY, "same", title = "Spotify identity")

        val state = aggregate(
            states = mapOf(
                CatalogSource.SPOTIFY to data(differentSource),
                CatalogSource.YOUTUBE to data(first, repeatedIdentity, differentType),
            ),
        )

        assertThat(state.orderedResults)
            .containsExactly(differentSource, first, differentType)
    }

    @Test
    fun partialFailure_preservesSuccessfulProviderResults() {
        val youtube = result(CatalogSource.YOUTUBE, "video")

        val state = aggregate(
            states = mapOf(
                CatalogSource.SPOTIFY to SourceSearchState.Unavailable(
                    QUERY,
                    UnavailableReason.AUTHENTICATION_REQUIRED,
                ),
                CatalogSource.YOUTUBE to data(youtube),
            ),
        )

        assertThat(state.orderedResults).containsExactly(youtube)
        assertThat(state.hasPartialSuccess).isTrue()
    }

    @Test
    fun disabledSource_neverPublishesItsStateOrResults() {
        val disabled = result(CatalogSource.YOUTUBE_MUSIC, "disabled")

        val state = aggregate(
            enabled = setOf(CatalogSource.SPOTIFY),
            states = mapOf(CatalogSource.YOUTUBE_MUSIC to data(disabled)),
        )

        assertThat(state.orderedResults).isEmpty()
        assertThat(state.sourceStates[CatalogSource.YOUTUBE_MUSIC])
            .isEqualTo(SourceSearchState.Idle)
    }

    @Test
    fun oldQueryState_isDiscardedInsteadOfMixedWithCurrentQuery() {
        val stale = result(CatalogSource.YOUTUBE, "stale")

        val state = aggregate(
            states = mapOf(
                CatalogSource.YOUTUBE to SourceSearchState.Data("old query", listOf(stale)),
            ),
        )

        assertThat(state.orderedResults).isEmpty()
        assertThat(state.sourceStates[CatalogSource.YOUTUBE])
            .isEqualTo(SourceSearchState.Idle)
    }

    @Test
    fun retryOfSameQuery_canKeepPreviousResultsVisible() {
        val previous = result(CatalogSource.YOUTUBE, "previous")

        val state = aggregate(
            states = mapOf(
                CatalogSource.YOUTUBE to SourceSearchState.Loading(
                    query = QUERY,
                    previous = listOf(previous),
                    isRetry = true,
                ),
            ),
        )

        assertThat(state.orderedResults).containsExactly(previous)
    }

    @Test
    fun retryFromOldQuery_cannotKeepPreviousResultsVisible() {
        val previous = result(CatalogSource.YOUTUBE, "previous")

        val state = aggregate(
            states = mapOf(
                CatalogSource.YOUTUBE to SourceSearchState.Loading(
                    query = "old query",
                    previous = listOf(previous),
                    isRetry = true,
                ),
            ),
        )

        assertThat(state.orderedResults).isEmpty()
        assertThat(state.sourceStates[CatalogSource.YOUTUBE])
            .isEqualTo(SourceSearchState.Idle)
    }

    @Test
    fun alternativeVersions_remainSeparateAndKeepOriginalIds() {
        val studio = result(CatalogSource.YOUTUBE, "studio-id", title = "Song")
        val live = result(CatalogSource.YOUTUBE, "live-id", title = "Song", version = "Live")
        val cover = result(CatalogSource.YOUTUBE, "cover-id", title = "Song", version = "Cover")

        val state = aggregate(
            states = mapOf(CatalogSource.YOUTUBE to data(studio, live, cover)),
        )

        assertThat(state.orderedResults).containsExactly(studio, live, cover)
        assertThat(state.orderedResults.map { it.identity.catalogId })
            .containsExactly("studio-id", "live-id", "cover-id")
    }

    private fun aggregate(
        enabled: Set<CatalogSource> = setOf(CatalogSource.SPOTIFY, CatalogSource.YOUTUBE),
        states: Map<CatalogSource, SourceSearchState>,
    ) = UniversalSearchEngine.aggregate(
        query = QUERY,
        selection = SearchSourceSelection(enabled = enabled, isPinned = false),
        sourceStates = states,
    )

    private fun data(vararg items: UniversalSearchResult) =
        SourceSearchState.Data(QUERY, items.toList())

    private fun result(
        source: CatalogSource,
        id: String,
        score: Double = 0.8,
        rank: Int = 0,
        type: SearchResultType = SearchResultType.VIDEO,
        title: String = id,
        version: String? = null,
    ) = UniversalSearchResult(
        identity = CatalogIdentity(source = source, catalogId = id),
        type = type,
        title = title,
        providerRank = rank,
        rankingScore = score,
        versionLabel = version,
    )

    private companion object {
        const val QUERY = "song"
    }
}

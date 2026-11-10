package com.stash.feature.search.contract

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchContractTest {

    @Test fun `default sources enable Spotify and YouTube but not YouTube Music`() {
        val selection = SearchSourceSelection.Default

        assertThat(selection.enabled).containsExactly(
            CatalogSource.SPOTIFY,
            CatalogSource.YOUTUBE,
        )
        assertThat(selection.enabled).doesNotContain(CatalogSource.YOUTUBE_MUSIC)
        assertThat(selection.isPinned).isFalse()
    }

    @Test fun `stable key is derived from source type and catalog id`() {
        val result = UniversalSearchFixtures.spotifyTrack(catalogId = "track-42")

        assertThat(result.stableKey).isEqualTo("SPOTIFY:TRACK:track-42")
    }

    @Test fun `the same external id remains distinct across catalog sources`() {
        val spotify = CatalogIdentity(CatalogSource.SPOTIFY, "same-id")
        val youtube = CatalogIdentity(CatalogSource.YOUTUBE, "same-id")
        val youtubeMusic = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, "same-id")

        assertThat(setOf(spotify, youtube, youtubeMusic)).hasSize(3)
        assertThat(spotify).isNotEqualTo(youtube)
        assertThat(youtube).isNotEqualTo(youtubeMusic)
    }

    @Test fun `audio identity stays separate from catalog identity and stable key`() {
        val catalogOnly = UniversalSearchFixtures.spotifyTrack(catalogId = "spotify-7")
        val withAudio = catalogOnly.copy(
            audioIdentity = AudioIdentity(provider = "youtube", audioId = "video-9"),
        )

        assertThat(withAudio.identity).isEqualTo(catalogOnly.identity)
        assertThat(withAudio.stableKey).isEqualTo(catalogOnly.stableKey)
        assertThat(withAudio.audioIdentity?.audioId).isEqualTo("video-9")
        assertThat(withAudio.identity.catalogId).isEqualTo("spotify-7")
    }

    @Test fun `alternative versions keep separate identities and labels`() {
        val studio = UniversalSearchFixtures.youtubeVideo(
            catalogId = "video-studio",
            versionLabel = "Studio",
        )
        val live = UniversalSearchFixtures.youtubeVideo(
            catalogId = "video-live",
            versionLabel = "Live at Pompeii",
        )

        assertThat(studio.stableKey).isNotEqualTo(live.stableKey)
        assertThat(studio.versionLabel).isEqualTo("Studio")
        assertThat(live.versionLabel).isEqualTo("Live at Pompeii")
    }

    @Test fun `source states represent partial success without discarding data`() {
        val state = UniversalSearchFixtures.partialSuccessState()

        assertThat(state.orderedResults).hasSize(2)
        assertThat(state.orderedResults).containsExactly(
            UniversalSearchFixtures.spotifyTrack(),
            UniversalSearchFixtures.youtubeVideo(),
        ).inOrder()
        assertThat(state.hasPartialSuccess).isTrue()
        assertThat(state.sourceStates[CatalogSource.YOUTUBE_MUSIC])
            .isInstanceOf(SourceSearchState.Error::class.java)
    }

    @Test fun `source state rejects results owned by another source`() {
        val youtube = UniversalSearchFixtures.youtubeVideo()

        assertThrows(IllegalArgumentException::class.java) {
            UniversalSearchUiState(
                query = UniversalSearchFixtures.Query,
                sourceStates = mapOf(
                    CatalogSource.SPOTIFY to SourceSearchState.Data(
                        UniversalSearchFixtures.Query,
                        listOf(youtube),
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniversalSearchUiState(
                query = UniversalSearchFixtures.Query,
                sourceStates = mapOf(
                    CatalogSource.SPOTIFY to SourceSearchState.Loading(
                        query = UniversalSearchFixtures.Query,
                        previous = listOf(youtube),
                        isRetry = true,
                    ),
                ),
            )
        }
    }

    @Test fun `ordered result must fully match its visible source result`() {
        val visible = UniversalSearchFixtures.spotifyTrack(
            supportedActions = setOf(SearchResultAction.PLAY),
        )
        val alteredAggregate = visible.copy(
            title = "Different title",
            audioIdentity = AudioIdentity("youtube", "different-audio"),
            supportedActions = setOf(SearchResultAction.DOWNLOAD),
        )

        assertThat(alteredAggregate.stableKey).isEqualTo(visible.stableKey)
        assertThrows(IllegalArgumentException::class.java) {
            UniversalSearchUiState(
                query = UniversalSearchFixtures.Query,
                sourceStates = mapOf(
                    CatalogSource.SPOTIFY to SourceSearchState.Data(
                        UniversalSearchFixtures.Query,
                        listOf(visible),
                    ),
                ),
                orderedResults = listOf(alteredAggregate),
            )
        }
    }

    @Test fun `all six source states are representable without a parallel phase enum`() {
        val item = UniversalSearchFixtures.spotifyTrack()
        val states = listOf(
            SourceSearchState.Idle,
            SourceSearchState.Loading(UniversalSearchFixtures.Query),
            SourceSearchState.Data(UniversalSearchFixtures.Query, listOf(item)),
            SourceSearchState.Empty(UniversalSearchFixtures.Query),
            SourceSearchState.Unavailable(
                UniversalSearchFixtures.Query,
                UnavailableReason.AUTHENTICATION_REQUIRED,
            ),
            SourceSearchState.Error(
                UniversalSearchFixtures.Query,
                message = "Unavailable in fixture",
                canRetry = true,
            ),
        )

        assertThat(states).hasSize(6)
        assertThat(states.map { it::class }.toSet()).hasSize(6)
    }

    @Test fun `previous results are accepted only for retry of the same query`() {
        val previous = listOf(UniversalSearchFixtures.spotifyTrack())

        assertThrows(IllegalArgumentException::class.java) {
            SourceSearchState.Loading(
                query = UniversalSearchFixtures.Query,
                previous = previous,
                isRetry = false,
            )
        }

        val retry = SourceSearchState.Loading(
            query = UniversalSearchFixtures.Query,
            previous = previous,
            isRetry = true,
        )
        assertThat(retry.previous).containsExactlyElementsIn(previous)
    }

    @Test fun `state from an old query cannot populate the current ui state`() {
        val result = UniversalSearchFixtures.spotifyTrack()

        assertThrows(IllegalArgumentException::class.java) {
            UniversalSearchUiState(
                query = "new query",
                sourceStates = mapOf(
                    CatalogSource.SPOTIFY to SourceSearchState.Data(
                        query = "old query",
                        items = listOf(result),
                    ),
                ),
                orderedResults = listOf(result),
            )
        }
    }

    @Test fun `result actions are available only when explicitly supported`() {
        val result = UniversalSearchFixtures.spotifyTrack(
            supportedActions = setOf(
                SearchResultAction.PLAY,
                SearchResultAction.ADD_TO_QUEUE,
                SearchResultAction.NAVIGATE,
            ),
        )

        assertThat(result.supports(SearchResultAction.PLAY)).isTrue()
        assertThat(result.supports(SearchResultAction.ADD_TO_QUEUE)).isTrue()
        assertThat(result.supports(SearchResultAction.DOWNLOAD)).isFalse()
        assertThat(result.supports(SearchResultAction.START_RADIO)).isFalse()
    }

    @Test fun `youtube fixture declares no working integration by default`() {
        val result = UniversalSearchFixtures.youtubeVideo()

        assertThat(result.supportedActions).isEmpty()
        assertThat(result.audioIdentity).isNull()
    }

    @Test fun `dto invariants reject invalid identities results and states`() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogIdentity(CatalogSource.SPOTIFY, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniversalSearchFixtures.spotifyTrack(rankingScore = 1.01)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceSearchState.Data(UniversalSearchFixtures.Query, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecentSearchItem(
                id = "recent-1",
                kind = RecentSearchKind.RESULT,
                label = "Echoes",
                resultIdentity = null,
            )
        }
    }

    @Test fun `ordered results cannot contain a disabled source`() {
        val youtubeMusic = UniversalSearchFixtures.youtubeMusicAlbum()

        assertThrows(IllegalArgumentException::class.java) {
            UniversalSearchUiState(
                query = UniversalSearchFixtures.Query,
                selection = SearchSourceSelection.Default,
                sourceStates = mapOf(
                    CatalogSource.YOUTUBE_MUSIC to SourceSearchState.Data(
                        UniversalSearchFixtures.Query,
                        listOf(youtubeMusic),
                    ),
                ),
                orderedResults = listOf(youtubeMusic),
            )
        }
    }
}

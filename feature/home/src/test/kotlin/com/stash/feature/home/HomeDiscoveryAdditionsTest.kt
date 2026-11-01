package com.stash.feature.home

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.mix.MixBuildState
import com.stash.core.model.MusicSource
import org.junit.Test

class HomeDiscoveryAdditionsTest {
    private fun mix(id: Long, art: String? = "https://example.org/art.jpg",
                    state: MixBuildState = MixBuildState.READY) =
        HomeMix(id = id, title = "Mix $id", artUrl = art, source = MusicSource.LOCAL,
                buildState = state)

    @Test fun selectsReadyImageBackedMixesAcrossSourcesWithoutRepeats() {
        val result = homeQuickMixes(HomeUiState(
            yourMixes = listOf(mix(1, null), mix(2)),
            madeForYou = listOf(mix(2), mix(3)),
            radios = listOf(mix(4, state = MixBuildState.BUILDING), mix(5)),
            moodDecades = listOf(mix(6)),
        ))
        assertThat(result.map { it.id }).containsExactly(2L, 3L, 5L, 6L).inOrder()
    }

    @Test fun missingSourcesNeverProducePlaceholderMixCards() {
        assertThat(homeQuickMixes(HomeUiState())).isEmpty()
        assertThat(homeQuickMixes(HomeUiState(
            yourMixes = listOf(mix(1, ""), mix(2, state = MixBuildState.EMPTY)),
        ))).isEmpty()
    }

    @Test fun quickMixOrderDoesNotMutateConfiguredRails() {
        val state = HomeUiState(yourMixes = listOf(mix(1), mix(2)))
        assertThat(homeQuickMixes(state).map { it.id }).containsExactly(1L)
        assertThat(state.yourMixes.map { it.id }).containsExactly(1L, 2L).inOrder()
    }
}

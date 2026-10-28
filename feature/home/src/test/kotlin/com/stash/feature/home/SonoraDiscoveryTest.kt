package com.stash.feature.home

import com.stash.core.model.MusicSource
import com.stash.core.ui.visual.VisualPage
import com.stash.core.ui.visual.VisualStatus
import org.junit.Assert.*
import org.junit.Test

class SonoraDiscoveryTest {
    @Test fun emptyLibraryDoesNotSeedDemoRadios() {
        val state = HomeUiState(isLoading = false).discoveryPresentation(true)
        assertEquals(VisualPage.Radio, state.page)
        assertEquals(VisualStatus.Empty, state.status)
        assertTrue(state.sections.all { it.cards.isEmpty() })
    }
    @Test fun realMixIdentityAndArtworkReachTheRadio() {
        val mix = HomeMix(987, "Minha estação", "https://example.invalid/art", MusicSource.SPOTIFY)
        val state = HomeUiState(isLoading = false, radios = listOf(mix)).discoveryPresentation(true)
        val card = state.sections.first().cards.single()
        assertEquals("mix:987", card.id)
        assertEquals(mix.artUrl, card.artwork)
        assertEquals(mix.title, card.title)
        assertEquals(VisualStatus.Content, state.status)
    }
    @Test fun loadingAndDisabledProviderAreRepresentedWithoutFakeContent() {
        val state = HomeUiState(qobuzDiscoveryEnabled = false).discoveryPresentation(false)
        assertEquals(VisualStatus.Loading, state.status)
        assertTrue(state.filters.isEmpty())
        assertTrue(state.sections.all { it.cards.isEmpty() })
    }
}

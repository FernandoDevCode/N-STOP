package com.stash.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.stash.core.data.prefs.HomeSection
import com.stash.core.model.MusicSource
import com.stash.core.ui.components.LocalBottomChromeInset
import com.stash.core.ui.visual.VisualTheme
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.PlaylistSummary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w412dp-h960dp-mdpi")
class SonoraHomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private var opened = 0L
    private var settings = 0
    private val state = MutableStateFlow(HomeUiState(isLoading = false))
    private val minimized = MutableStateFlow(false)
    private val vm = mock<HomeViewModel> {
        on { uiState } doReturn state
        on { heroMinimized } doReturn minimized
        on { streamingEnabled } doReturn MutableStateFlow(false)
        on { showStreamingDisclosure } doReturn MutableSharedFlow<Unit>()
        on { userMessages } doReturn MutableSharedFlow<String>()
    }

    private fun render() {
        // The existing support ticker uses a wall-clock frame loop. Freeze test frames
        // so it cannot keep the Compose test scheduler busy at the end of the feed.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisualTheme {
                CompositionLocalProvider(LocalBottomChromeInset provides 130.dp) {
                    HomeScreen(modifier = Modifier.fillMaxSize().testTag("home"), viewModel = vm,
                        onNavigateToPlaylist = { opened = it }, onNavigateToSettings = { settings++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
    }

    @Test fun emptyHomeUsesRealSetupAndNoDiscoveryPlaceholder() {
        render()
        compose.onNodeWithText("Personalize seu início").assertIsDisplayed()
        compose.onNodeWithText("Configurar →").performClick()
        compose.runOnIdle { check(settings == 1) }
        compose.onNodeWithText("Continue ouvindo").assertDoesNotExist()
        compose.onNodeWithText("Abrir playlist").assertDoesNotExist()
    }

    @Test fun realHeroOpensPlaysAndMinimizesWithoutAnEmptySlot() {
        state.value = HomeUiState(isLoading = false, hero = DiscoverHeroState("Daily available", "30 faixas", null, 7L))
        render()
        compose.onNodeWithText("Ouvir agora").performClick()
        verify(vm).playHero()
        compose.onNodeWithText("Abrir playlist").performClick()
        compose.runOnIdle { check(opened == 7L) }
        compose.onNodeWithContentDescription("Opções de Daily available").performClick()
        repeat(4) {
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
        }
        // Verify the real menu action through accessibility semantics. Robolectric's
        // touch dispatch across the sheet's separate window requires device validation.
        compose.onNodeWithText("Minimize for now").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { verify(vm).setHeroMinimized(true) }
        minimized.value = true
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Daily available").assertDoesNotExist()
        compose.onNodeWithText("Continue ouvindo").assertDoesNotExist()
    }

    @Test fun savedOrderHiddenSectionsAndPlaylistContextMenuArePreserved() {
        state.value = HomeUiState(isLoading = false,
            sections = listOf(HomeSection.RADIOS, HomeSection.YOUR_PLAYLISTS),
            radios = listOf(HomeMix(8L, "Radio fixture", null, MusicSource.SPOTIFY)),
            yourPlaylists = listOf(HomeMix(9L, "Pinned fixture", null, MusicSource.SPOTIFY)),
            madeForYou = listOf(HomeMix(10L, "Hidden fixture", null, MusicSource.SPOTIFY)))
        render()
        compose.onNodeWithText("Hidden fixture").assertDoesNotExist()
        compose.onNodeWithText("Personalize seu início").assertDoesNotExist()
        val radio = compose.onNodeWithText("Rádios").fetchSemanticsNode().boundsInRoot.top
        val pinned = compose.onNodeWithText("Suas playlists").fetchSemanticsNode().boundsInRoot.top
        check(radio < pinned)
        compose.onNodeWithText("Pinned fixture").performClick()
        compose.runOnIdle { check(opened == 9L) }
        compose.onNodeWithText("Pinned fixture").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Remove from Home").assertExists()
    }

    @Test fun loadingAndRealBuildingAreDifferentFromAnEmptyHero() {
        state.value = HomeUiState()
        render()
        compose.onNodeWithText("Carregando seu início…").assertIsDisplayed()
        compose.onNodeWithText("Abrir playlist").assertDoesNotExist()
        state.value = HomeUiState(isLoading = false, heroBuilding = true)
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Preparando seu Daily Discover…").assertIsDisplayed()
        compose.onNodeWithText("Personalize seu início").assertDoesNotExist()
        compose.onNodeWithText("Ouvir agora").assertDoesNotExist()
    }

    @Test fun genresFollowNewReleasesAndKeepOriginalFilterIdentifiers() {
        state.value = HomeUiState(
            isLoading = false,
            sections = listOf(HomeSection.NEW_RELEASES, HomeSection.QOBUZ_PLAYLISTS),
            newReleases = listOf(AlbumSummary("release", "Álbum fixture", "Artista", null, "2026")),
            playlists = listOf(PlaylistSummary("playlist", "Playlist fixture", "Qobuz", null, 12)),
        )
        render()
        val releases = compose.onNodeWithText("Novos lançamentos").fetchSemanticsNode().boundsInRoot
        val genres = compose.onNodeWithText("Todos").fetchSemanticsNode().boundsInRoot
        val playlists = compose.onNodeWithText("Playlists do Qobuz").fetchSemanticsNode().boundsInRoot
        check(releases.top < genres.top && genres.top < playlists.top)
        // The filters remain comfortably separated from New Releases, while the
        // following Qobuz heading no longer inherits a full section gap.
        check(playlists.top - genres.bottom in 12f..40f)
        compose.onNodeWithText("Eletrônica").performClick()
        compose.runOnIdle { verify(vm).onSelectGenre("Electronic") }
    }

    @Test fun genresMoveToFirstPopulatedQobuzSectionWhenReleasesAreEmpty() {
        state.value = HomeUiState(
            isLoading = false,
            sections = listOf(HomeSection.NEW_RELEASES, HomeSection.QOBUZ_PLAYLISTS),
            newReleases = emptyList(),
            playlists = listOf(PlaylistSummary("playlist", "Playlist fixture", "Qobuz", null, 12)),
        )
        render()
        compose.onNodeWithText("Novos lançamentos").assertDoesNotExist()
        val genres = compose.onNodeWithText("Todos").fetchSemanticsNode().boundsInRoot
        val playlists = compose.onNodeWithText("Playlists do Qobuz").fetchSemanticsNode().boundsInRoot
        check(genres.top < playlists.top)
        check(playlists.top - genres.bottom in 6f..36f)
    }

    @Test fun emptyCustomSectionsDoNotBreakGenreToQobuzContinuity() {
        state.value = HomeUiState(
            isLoading = false,
            sections = listOf(
                HomeSection.NEW_RELEASES,
                HomeSection.MADE_FOR_YOU,
                HomeSection.RADIOS,
                HomeSection.QOBUZ_PLAYLISTS,
            ),
            newReleases = listOf(AlbumSummary("release", "Álbum fixture", "Artista", null, "2026")),
            madeForYou = emptyList(),
            radios = emptyList(),
            playlists = listOf(PlaylistSummary("playlist", "Playlist fixture", "Qobuz", null, 12)),
        )
        render()
        val genres = compose.onNodeWithText("Todos").fetchSemanticsNode().boundsInRoot
        val playlists = compose.onNodeWithText("Playlists do Qobuz").fetchSemanticsNode().boundsInRoot
        check(playlists.top - genres.bottom in 12f..40f)
    }
}

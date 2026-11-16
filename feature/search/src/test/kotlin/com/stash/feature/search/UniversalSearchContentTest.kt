package com.stash.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.VisualTheme
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.CatalogSource.SPOTIFY
import com.stash.feature.search.contract.CatalogSource.YOUTUBE
import com.stash.feature.search.contract.CatalogSource.YOUTUBE_MUSIC
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchActions
import com.stash.feature.search.contract.UniversalSearchResult
import com.stash.feature.search.contract.UniversalSearchUiState
import com.stash.feature.search.engine.queryHistoryItem
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w390dp-h840dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UniversalSearchContentTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mock<UniversalSearchActions>()

    @Test
    fun `empty query has independent filters pin restore and IME search`() {
        show(UniversalSearchUiState())
        compose.onNodeWithText("Search for a song, video, artist, or album.").assertExists()
        compose.onNodeWithText("Spotify").assertIsSelected().performClick()
        compose.onNodeWithText("YouTube").assertIsSelected()
        compose.onNodeWithText("YouTube Music").assertIsNotSelected().performClick()
        compose.onNodeWithTag("searchPin").assertIsOff().performClick()
        compose.onNodeWithText("Restore defaults").performClick()
        compose.onNodeWithTag("searchQuery").performTextInput("a")
        compose.onNodeWithTag("searchQuery").performImeAction()
        verify(actions).onSourceToggled(SPOTIFY)
        verify(actions).onSourceToggled(YOUTUBE_MUSIC)
        verify(actions).onPinToggled()
        verify(actions).onRestoreDefaultSources()
        verify(actions).onQueryChanged("a")
        verify(actions).onSearchSubmitted()
    }

    @Test
    fun `sources heading fits at 280 dp and 150 percent font scale`() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                Box(Modifier.width(280.dp)) {
                    VisualTheme { UniversalSearchContent(UniversalSearchUiState(), actions, autoFocus = false) }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Sources").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { read ->
            assertTrue(read(layouts))
        }
        assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText("Restore defaults").assertHasClickAction()
    }

    @Test
    fun `history supports open remove and clear even with no selected sources`() {
        val recent = queryHistoryItem("demo")
        show(UniversalSearchUiState(selection = SearchSourceSelection(emptySet()), recentSearches = listOf(recent)))
        compose.onNodeWithText("Select at least one source to search.").assertExists()
        compose.onNodeWithText("demo").performClick()
        compose.onNodeWithContentDescription("Remove demo from history").performClick()
        compose.onNodeWithText("Clear history").performClick()
        verify(actions).onRecentSelected(recent)
        verify(actions).onRecentRemoved(recent)
        verify(actions).onRecentCleared()
    }

    @Test
    fun `partial results stay in motor order with a single retry`() {
        val demo = hit(YOUTUBE_MUSIC, "demoOriginal", "First (Demo)", SearchResultType.VIDEO)
        val live = hit(YOUTUBE_MUSIC, "liveOriginal", "Second (Live)", SearchResultType.VIDEO)
        val artist = hit(YOUTUBE_MUSIC, "artistOriginal", "Third", SearchResultType.ARTIST)
        show(
            UniversalSearchUiState(
                query = "rock",
                selection = SearchSourceSelection(CatalogSource.entries.toSet()),
                sourceStates = mapOf(
                    YOUTUBE to SourceSearchState.Error("rock", "Network failure", canRetry = true),
                    YOUTUBE_MUSIC to SourceSearchState.Data("rock", listOf(artist, live, demo)),
                ),
                orderedResults = listOf(demo, live, artist),
            ),
        )
        compose.onNodeWithText("Search failed").assertDoesNotExist()
        compose.onNodeWithContentDescription("Connect Spotify").assertDoesNotExist()
        compose.onNodeWithContentDescription("Retry YouTube").performClick()
        compose.onNodeWithContentDescription("Clear search").performClick()
        val first = compose.onNodeWithTag(demo.stableKey).assertHasNoClickAction()
        val second = compose.onNodeWithTag(live.stableKey).assertHasNoClickAction()
        assertTrue(first.getUnclippedBoundsInRoot().top < second.getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag(artist.stableKey).assertHasClickAction().performClick()
        verify(actions).onRetrySource(YOUTUBE)
        verify(actions).onClearQuery()
        verify(actions).onResultAction(artist, SearchResultAction.NAVIGATE)
    }

    @Test
    fun `loading defers the global error until selected sources settle`() {
        val error = SourceSearchState.Error("rock", "Network failure", true)
        val youtube = androidx.compose.runtime.mutableStateOf<SourceSearchState>(SourceSearchState.Loading("rock"))
        val music = androidx.compose.runtime.mutableStateOf<SourceSearchState>(error)
        compose.setContent {
            VisualTheme {
                UniversalSearchContent(
                    UniversalSearchUiState(query = "rock", sourceStates = mapOf(YOUTUBE_MUSIC to music.value, YOUTUBE to youtube.value)),
                    actions, autoFocus = false,
                )
            }
        }
        compose.onNodeWithText("Searching YouTube…").assertExists()
        compose.onNodeWithText("Search failed").assertDoesNotExist()
        compose.runOnIdle { youtube.value = SourceSearchState.Empty("rock") }
        compose.onNodeWithText("Search failed").assertExists()
        compose.onNodeWithText("YouTube · No results").assertExists()
        compose.runOnIdle { music.value = SourceSearchState.Empty("rock") }
        compose.onNodeWithText("Search failed").assertDoesNotExist()
        compose.onNodeWithText("No results found").assertExists()
    }

    private fun show(state: UniversalSearchUiState) {
        compose.setContent { VisualTheme { UniversalSearchContent(state, actions, autoFocus = false) } }
    }

    private fun hit(source: CatalogSource, id: String, title: String, type: SearchResultType) =
        UniversalSearchResult(
            identity = CatalogIdentity(source, id),
            type = type, title = title, providerRank = 0, rankingScore = 1.0,
            supportedActions = if (type == SearchResultType.ARTIST) setOf(SearchResultAction.NAVIGATE) else emptySet(),
        )
}

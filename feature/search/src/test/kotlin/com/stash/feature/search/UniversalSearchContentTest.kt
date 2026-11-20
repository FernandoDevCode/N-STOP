package com.stash.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.VisualTheme
import com.stash.data.ytmusic.YouTubeWebVideo
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
import com.stash.feature.search.engine.toUniversalSearchResult
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
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
    fun `empty query shows history and source settings without category chips`() {
        show(UniversalSearchUiState())
        compose.onNodeWithText("Search for a song, video, artist, or album.").assertExists()
        compose.onNodeWithText("Spotify").assertDoesNotExist()
        compose.onNodeWithText("YouTube").assertDoesNotExist()
        compose.onNodeWithText("YouTube Music").assertDoesNotExist()
        compose.onNodeWithTag("searchPin").assertDoesNotExist()
        compose.onNodeWithTag("searchRestoreDefaults").assertDoesNotExist()
        compose.onNodeWithText("Sources").assertDoesNotExist()
        compose.onNodeWithTag("searchCategories").assertDoesNotExist()
        compose.onNodeWithTag("searchSourceSettings").assertHasClickAction()
        compose.onNodeWithTag("searchQuery").performTextInput("a")
        compose.onNodeWithTag("searchQuery").performImeAction()
        verify(actions).onQueryChanged("a")
        verify(actions).onSearchSubmitted()
        verify(actions, never()).onSourceToggled(any())
    }

    @Test
    fun `sources are configured in the sheet with two independent toggles and a pin`() {
        show(UniversalSearchUiState(query = "rock"))
        compose.onNodeWithTag("searchCategoryTOP").assertIsSelected()
        compose.onNodeWithTag("searchFilterSPOTIFY").assertDoesNotExist()
        openSources()
        compose.onNodeWithTag("searchFilterSPOTIFY").assertIsOn().performClick()
        compose.onNodeWithTag("searchFilterYOUTUBE").assertIsOn()
        compose.onNodeWithTag("searchFilterYOUTUBE_MUSIC").assertDoesNotExist()
        compose.onNodeWithTag("searchRestoreDefaults").assertIsNotEnabled()
        compose.onNodeWithTag("searchPin").assertIsOff().performClick()
        verify(actions).onSourceToggled(SPOTIFY)
        verify(actions).onPinToggled()
        verify(actions, never()).onSourceToggled(YOUTUBE_MUSIC)
    }

    @Test
    fun `restore defaults is enabled for a changed visible selection`() {
        show(UniversalSearchUiState(query = "rock", selection = SearchSourceSelection(setOf(YOUTUBE), isPinned = true)))
        openSources()
        compose.onNodeWithTag("searchFilterSPOTIFY").assertIsOff()
        compose.onNodeWithTag("searchFilterYOUTUBE").assertIsOn()
        compose.onNodeWithTag("searchPin").assertIsOn()
        compose.onNodeWithTag("searchRestoreDefaults").assertHasClickAction().performClick()
        verify(actions).onRestoreDefaultSources()
    }

    @Test
    fun `categories scroll at 280 dp and 150 percent font scale`() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                Box(Modifier.width(280.dp)) {
                    VisualTheme {
                        UniversalSearchContent(UniversalSearchUiState(query = "rock"), actions, autoFocus = false)
                    }
                }
            }
        }
        selectCategory("SONGS")
        compose.onNodeWithTag("searchCategorySONGS").assertIsSelected()
        selectCategory("TOP")
        compose.onNodeWithTag("searchCategoryTOP").assertIsSelected()
        openSources()
        compose.onNodeWithTag("searchFilterSPOTIFY").assertHasClickAction()
        compose.onNodeWithTag("searchFilterYOUTUBE").assertHasClickAction()
        compose.onNodeWithTag("searchPin").assertHasClickAction()
    }

    @Test
    fun `changing query resets Top while updates to the same query preserve the category`() {
        val state = mutableStateOf(UniversalSearchUiState(query = "radiohead"))
        compose.setContent {
            VisualTheme { UniversalSearchContent(state.value, actions, autoFocus = false) }
        }
        selectCategory("ALBUMS")
        compose.onNodeWithTag("searchCategoryALBUMS").assertIsSelected()

        // A recomposition of the same query (including navigation back) keeps the chosen category.
        compose.runOnIdle {
            state.value = state.value.copy(recentSearches = listOf(queryHistoryItem("older")))
        }
        compose.onNodeWithTag("searchCategoryALBUMS").assertIsSelected()

        // Replacing one nonempty query with another must restore Top.
        compose.runOnIdle { state.value = state.value.copy(query = "slipknot") }
        compose.onNodeWithTag("searchCategoryTOP").assertIsSelected()
    }

    @Test
    fun `youtube category stays available with the source off and never toggles it`() {
        show(UniversalSearchUiState(query = "rock", selection = SearchSourceSelection(setOf(SPOTIFY))))
        selectCategory("YOUTUBE")
        compose.onNodeWithTag("searchCategoryYOUTUBE").assertIsSelected()
        compose.onNodeWithText("Open Search filters and turn on YouTube to see videos here.").assertExists()
        verify(actions, never()).onSourceToggled(any())
        verify(actions, never()).onQueryChanged(any())
    }

    @Test
    fun `top leads with best ranked result once and see all opens its full category locally`() {
        val artist = hit(YOUTUBE_MUSIC, "artist", "Best artist", SearchResultType.ARTIST)
        val related = hit(YOUTUBE_MUSIC, "related", "Related artist", SearchResultType.ARTIST)
        val album = hit(YOUTUBE_MUSIC, "album", "An album", SearchResultType.ALBUM)
        val musicVideo = hit(YOUTUBE_MUSIC, "ytm", "Music video", SearchResultType.VIDEO)
        val webVideo = hit(YOUTUBE, "web", "Web video", SearchResultType.VIDEO)
        val ranked = listOf(artist, related, album, musicVideo, webVideo)
        show(UniversalSearchUiState(
            query = "rock",
            sourceStates = mapOf(
                YOUTUBE_MUSIC to SourceSearchState.Data("rock", ranked.dropLast(1)),
                YOUTUBE to SourceSearchState.Data("rock", listOf(webVideo)),
            ),
            orderedResults = ranked,
        ))
        compose.onNodeWithText("Best match").assertExists()
        compose.onNodeWithTag(artist.stableKey).assertExists()
        compose.onNodeWithContentDescription("See all: Artists").performClick()
        compose.onNodeWithText("Best match").assertDoesNotExist()
        compose.onNodeWithTag("searchCategoryARTISTS").assertIsSelected()
        compose.onNodeWithTag(artist.stableKey).assertExists()
        compose.onNodeWithTag(related.stableKey).assertExists()
        compose.onNodeWithTag(album.stableKey).assertDoesNotExist()
        selectCategory("SONGS")
        compose.onNodeWithTag(musicVideo.stableKey).assertExists()
        compose.onNodeWithTag(webVideo.stableKey).assertDoesNotExist()
        selectCategory("YOUTUBE")
        compose.onNodeWithTag(webVideo.stableKey).assertExists()
        compose.onNodeWithTag(musicVideo.stableKey).assertDoesNotExist()
        verify(actions, never()).onQueryChanged(any())
        verify(actions, never()).onSearchSubmitted()
        verify(actions, never()).onSourceToggled(any())
    }

    @Test
    fun `history supports open remove and clear even with no selected sources`() {
        val recent = queryHistoryItem("demo")
        show(UniversalSearchUiState(selection = SearchSourceSelection(emptySet()), recentSearches = listOf(recent)))
        compose.onNodeWithText("Select at least one source to search.").assertDoesNotExist()
        compose.onNodeWithTag("searchPin").assertDoesNotExist()
        compose.onNodeWithText("demo").performClick()
        compose.onNodeWithContentDescription("Remove demo from history").performClick()
        compose.onNodeWithText("Clear history").performClick()
        verify(actions).onRecentSelected(recent)
        verify(actions).onRecentRemoved(recent)
        verify(actions).onRecentCleared()
    }

    @Test
    fun `active query with no sources shows a recovery message and restore action`() {
        show(UniversalSearchUiState(query = "demo", selection = SearchSourceSelection(emptySet())))
        compose.onNodeWithText("Select at least one source to search.").assertExists()
        openSources()
        compose.onNodeWithTag("searchRestoreDefaults").assertHasClickAction().performClick()
        verify(actions).onRestoreDefaultSources()
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

    @Test
    fun `only a valid playback row can be tapped and only the pending row is disabled`() {
        val first = YouTubeWebVideo("ABCDEFGHIJK", "Track", "Artist", null, null, 0).toUniversalSearchResult()
        val second = YouTubeWebVideo("ABCDEFGHIJL", "Track (Live)", "Artist", null, null, 1).toUniversalSearchResult()
        val invalid = YouTubeWebVideo("invalid", "Unavailable", "Artist", null, null, 2).toUniversalSearchResult()
        val results = listOf(first, second, invalid)
        val state = UniversalSearchUiState(
            query = "track",
            sourceStates = mapOf(YOUTUBE to SourceSearchState.Data("track", results)),
            orderedResults = results,
        )
        val preparing = mutableStateOf<String?>(null)
        compose.setContent {
            VisualTheme { UniversalSearchContent(state, actions, autoFocus = false, preparingKey = preparing.value) }
        }
        compose.onNodeWithTag(invalid.stableKey).assertHasNoClickAction()
        compose.onNodeWithTag(first.stableKey).assertHasClickAction().performClick()
        verify(actions).onResultAction(first, SearchResultAction.PLAY, null)
        compose.runOnIdle { preparing.value = first.stableKey }
        compose.onNodeWithTag(first.stableKey).assertIsNotEnabled()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Preparing playback")).assertExists()
        compose.onNodeWithTag(second.stableKey).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("YouTube · Video").assertExists()
        compose.onNodeWithText("Play").performClick()
        verify(actions).onResultAction(second, SearchResultAction.PLAY, null)
    }

    @Test
    fun `lazy list reports result keys entering the viewport and ignores unrelated recomposition`() {
        val hits = (1..30).map { hit(YOUTUBE_MUSIC, "id$it", "Song $it", SearchResultType.TRACK) }
        val state = mutableStateOf(
            UniversalSearchUiState(
                query = "rock",
                sourceStates = mapOf(YOUTUBE_MUSIC to SourceSearchState.Data("rock", hits)),
                orderedResults = hits,
            ),
        )
        compose.setContent { VisualTheme { UniversalSearchContent(state.value, actions, autoFocus = false) } }
        selectCategory("SONGS")
        compose.waitForIdle()
        val initial = argumentCaptor<Set<String>>()
        verify(actions, atLeastOnce()).onVisibleResultsChanged(eq("rock"), initial.capture())
        assertTrue(hits.first().stableKey in initial.lastValue)
        assertFalse(initial.allValues.any { hits.last().stableKey in it })
        assertTrue(initial.allValues.flatten().all { key -> hits.any { it.stableKey == key } })

        clearInvocations(actions)
        compose.runOnIdle { state.value = state.value.copy(recentSearches = listOf(queryHistoryItem("demo"))) }
        compose.waitForIdle()
        verify(actions, never()).onVisibleResultsChanged(any(), any())

        compose.onNodeWithTag("searchList").performScrollToKey(hits.last().stableKey)
        compose.waitForIdle()
        val scrolled = argumentCaptor<Set<String>>()
        verify(actions, atLeastOnce()).onVisibleResultsChanged(eq("rock"), scrolled.capture())
        assertTrue(hits.last().stableKey in scrolled.lastValue)
        assertFalse(hits.first().stableKey in scrolled.lastValue)
    }

    private fun openSources() {
        compose.onNodeWithTag("searchSourceSettings").performClick()
    }

    private fun selectCategory(category: String) {
        compose.onNodeWithTag("searchCategory$category").performScrollTo().performClick()
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

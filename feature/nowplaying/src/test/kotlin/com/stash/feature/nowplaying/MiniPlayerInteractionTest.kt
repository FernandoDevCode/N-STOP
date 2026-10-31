package com.stash.feature.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.stash.core.media.listen.ListenTogetherState
import com.stash.core.model.Track
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the actual MiniPlayer, including swipe-to-skip and its child buttons. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-mdpi")
class MiniPlayerInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val vm = mockk<NowPlayingViewModel>(relaxed = true)
    private val listen = mockk<ListenTogetherViewModel>(relaxed = true)
    private val track = Track(id = 1, title = "Test title", artist = "Test artist")
    private val ui = MutableStateFlow(NowPlayingUiState(currentTrack = track, durationMs = 100_000,
        isSeekable = true, queue = listOf(track)))
    private var opens = 0

    @Before fun showPlayer() {
        every { vm.uiState } returns ui
        every { vm.positionMs } returns MutableStateFlow(25_000L)
        every { listen.state } returns MutableStateFlow(ListenTogetherState.Idle)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(380.dp)) {
                    MiniPlayer(onExpand = { opens++ }, onOpenQueue = {}, viewModel = vm,
                        listen = listen, backgroundVisible = false, modifier = Modifier.testTag("mini"))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun coverTitleArtistAndFreeSurfaceOpenWithoutSeeking() {
        // Coordinates measured in the actual component: margin14 + cover padding6.
        compose.onNodeWithTag("mini").performTouchInput { click(Offset(36f, 26f)) }
        // The lower cover edge overlaps the curved rail's comfortable corridor; cover wins.
        compose.onNodeWithTag("mini").performTouchInput { click(Offset(25f, 45f)) }
        compose.onNodeWithText("Test title", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Test artist", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithTag("mini").performTouchInput { click(Offset(180f, 2f)) }
        // Outside the 14dp path corridor, a body down can never become seek later.
        compose.onNodeWithTag("mini").performTouchInput { click(Offset(180f, 40f)) }
        compose.runOnIdle { assertEquals(6, opens) }
        verify(exactly = 0) { vm.onSeekTo(any()) }
    }

    @Test fun directRailTapSeeksWithoutOpeningPlayer() {
        compose.onNodeWithContentDescription("Playback progress").performTouchInput {
            click(Offset(width / 2f, height - 1f))
        }
        // Half the width is slightly past half the length on an asymmetric path.
        val bounds = compose.onNodeWithContentDescription("Playback progress").fetchSemanticsNode().boundsInRoot
        val metric = MiniPlayerRail(bounds.width, 56f, 13f)
        val expected = (metric.fraction(Offset(bounds.width / 2f, 55f)) * 100_000).toLong()
        verify(exactly = 1) { vm.onSeekTo(match { kotlin.math.abs(it - expected) < 10L }) }
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun directNeedleTapSeeksWithoutOpeningPlayer() {
        // Playback starts at 25%; this is the visible needle on the straight section.
        compose.onNodeWithContentDescription("Playback progress").performTouchInput {
            click(Offset(width * .25f, height - 1f))
        }
        verify(exactly = 1) { vm.onSeekTo(match { it in 23_000L..27_000L }) }
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun compactCardRetainsAccessibleButtons() {
        compose.onNodeWithTag("mini").assertHeightIsEqualTo(66.dp)
        compose.onNodeWithContentDescription("Play").assertHeightIsAtLeast(48.dp)
    }

    @Test fun controlsOwnTheirTouches() {
        compose.onNodeWithContentDescription("Like song").performTouchInput { click() }
        compose.onNodeWithContentDescription("Play").performTouchInput { click() }
        verify(exactly = 1) { vm.onLikeTap() }
        verify(exactly = 1) { vm.onPlayPauseClick() }
        verify(exactly = 0) { vm.onSeekTo(any()) }
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun railDragKeepsOwnershipOutsideRowAndCommitsOnce() {
        compose.onNodeWithContentDescription("Playback progress").performTouchInput {
            down(Offset(40f, 12f))
            moveTo(Offset(260f, -15f), 400)
            up()
        }
        verify(exactly = 1) { vm.onSeekTo(match { it in 70_000L..80_000L }) }
        verify(exactly = 0) { vm.onSkipNext() }
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun startingAboveRailCannotBecomeSeekAfterCrossingBoundary() {
        compose.onNodeWithTag("mini").performTouchInput {
            down(Offset(100f, 30f))
            moveTo(Offset(110f, 55f), 250)
            up()
        }
        verify(exactly = 0) { vm.onSeekTo(any()) }
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun curvedEndpointsAndAccessibleRangeReachExactTimes() {
        val rail = compose.onNodeWithContentDescription("Playback progress")
        rail.performTouchInput { click(Offset(1f, 0f)) }
        verify { vm.onSeekTo(0L) }
        rail.performTouchInput { click(Offset(width - 1f, 0f)) }
        verify { vm.onSeekTo(100_000L) }
        rail.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.5f)) }
        verify { vm.onSeekTo(50_000L) }
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun cancellationAndSecondFingerNeverCommit() {
        val rail = compose.onNodeWithContentDescription("Playback progress")
        rail.performTouchInput { down(center); moveBy(Offset(20f, 0f)); cancel() }
        rail.performTouchInput {
            down(0, Offset(30f, 12f))
            down(1, Offset(90f, 12f))
            moveTo(0, Offset(150f, 12f))
            up(1); up(0)
        }
        verify(exactly = 0) { vm.onSeekTo(any()) }
    }

    @Test fun disabledSeekAndAbsentTrack() {
        compose.runOnIdle { ui.value = ui.value.copy(isSeekable = false) }
        compose.onNodeWithContentDescription("Playback progress").performTouchInput { click() }
        verify(exactly = 0) { vm.onSeekTo(any()) }
        compose.onNodeWithContentDescription("Playback progress")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(.75f)) }
        compose.runOnIdle { ui.value = ui.value.copy(currentTrack = null) }
        compose.onNodeWithContentDescription("Playback progress").assertDoesNotExist()
    }
}

package com.stash.feature.nowplaying

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.stash.core.media.listen.ListenTogetherState
import com.stash.core.model.Track
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Offscreen Android rendering evidence. This is NOT a device recording or a GPU benchmark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniPlayerRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun renderProductionMiniPlayerWithStationaryPlaybackPosition() {
        val vm = mockk<NowPlayingViewModel>(relaxed = true)
        val listen = mockk<ListenTogetherViewModel>(relaxed = true)
        val track = Track(id = 1, title = "Atrito Cinético", artist = "SONORA • render de teste")
        val ui = MutableStateFlow(NowPlayingUiState(currentTrack = track, durationMs = 100_000,
            isSeekable = true, isPlaying = true, playWhenReady = true, queue = listOf(track)))
        every { vm.uiState } returns ui
        every { vm.positionMs } returns MutableStateFlow(48_000L)
        every { listen.state } returns MutableStateFlow(ListenTogetherState.Idle)
        lateinit var androidView: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidView = LocalView.current
            MaterialTheme {
                Box(Modifier.width(380.dp).background(Color(0xFF101116)).testTag("render")) {
                    MiniPlayer(onExpand = {}, onOpenQueue = {}, viewModel = vm, listen = listen)
                }
            }
        }
        val output = File("build/outputs/mini-player-render").apply { mkdirs() }
        fun capture(name: String) {
            val bounds = compose.onNodeWithTag("render").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                androidView.draw(canvas)
                File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        compose.mainClock.advanceTimeBy(64)
        repeat(30) { index ->
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            capture("playing-%02d.png".format(index))
        }
        val rail = compose.onNodeWithContentDescription("Playback progress")
        for ((name, travel) in listOf("slow" to .06f, "fast" to .65f)) {
            rail.performTouchInput { down(Offset(width * .15f, height - 3f)) }
            repeat(10) { index ->
                rail.performTouchInput { moveTo(Offset(width * (.15f + travel * (index + 1) / 10f), height - 3f), 32) }
                compose.mainClock.advanceTimeBy(32)
                compose.waitForIdle()
                capture("$name-%02d.png".format(index))
            }
            rail.performTouchInput { up() }
            repeat(16) {
                compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
                capture("release-$name-%02d.png".format(it))
            }
        }
        // A paused transport must also quench particles while the stream reports buffering.
        compose.runOnIdle { ui.value = ui.value.copy(isPlaying = false, playWhenReady = false, isBuffering = true) }
        repeat(16) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
        capture("paused.png")
    }
}

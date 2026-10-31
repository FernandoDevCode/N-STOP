package com.stash.feature.nowplaying

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
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

    @Test fun actualPathMatchesSeekMetricAtRequiredSamples() {
        val rail = MiniPlayerRail(1056f, 72f, 39f)
        val path = androidx.compose.ui.graphics.Path().also(rail::writePath)
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        for (f in listOf(0f, .01f, .25f, .5f, .75f, .99f, 1f)) {
            val actual = measure.getPosition(measure.length * f)
            org.junit.Assert.assertTrue((actual - rail.point(f)).getDistance() < .08f)
            org.junit.Assert.assertEquals(f, rail.fraction(actual), .0001f)
        }
        org.junit.Assert.assertEquals(rail.inset, measure.getPosition(0f).x, .00001f)
        org.junit.Assert.assertEquals(rail.right, measure.getPosition(measure.length).x, .0001f)
    }

    @Test fun renderProductionMiniPlayerWithStationaryPlaybackPosition() {
        val vm = mockk<NowPlayingViewModel>(relaxed = true)
        val listen = mockk<ListenTogetherViewModel>(relaxed = true)
        val track = Track(id = 1, title = "Atrito Cinético", artist = "SONORA • render de teste")
        val ui = MutableStateFlow(NowPlayingUiState(currentTrack = track, durationMs = 100_000,
            isSeekable = true, isPlaying = true, playWhenReady = true, queue = listOf(track)))
        every { vm.uiState } returns ui
        val position = MutableStateFlow(48_000L)
        every { vm.positionMs } returns position
        every { listen.state } returns MutableStateFlow(ListenTogetherState.Idle)
        lateinit var androidView: View
        var viewport by mutableStateOf(380.dp)
        var renderDensity by mutableFloatStateOf(3f)
        var fontScale by mutableFloatStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(renderDensity, fontScale)) {
            MaterialTheme {
                Box(Modifier.width(viewport).background(Color(0xFF101116)).testTag("render")) {
                    MiniPlayer(onExpand = {}, onOpenQueue = {}, viewModel = vm, listen = listen)
                }
            }
        }
        }
        val output = File("build/outputs/mini-player-render").apply { mkdirs() }
        var visibleBelow = false
        fun capture(name: String, markerFraction: Float? = null) {
            val bounds = compose.onNodeWithTag("render").fetchSemanticsNode().boundsInRoot
            val progressBounds = markerFraction?.let {
                compose.onNodeWithContentDescription("Playback progress").fetchSemanticsNode().boundsInRoot
            }
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                androidView.draw(canvas)
                if (name.startsWith("playing-")) {
                    for (y in 174 until minOf(190, bitmap.height)) for (x in 45 until bitmap.width - 45) {
                        if (android.graphics.Color.red(bitmap.getPixel(x, y)) > 85) visibleBelow = true
                    }
                }
                if (markerFraction != null && progressBounds != null) {
                    val radius = 13f * renderDensity
                    val cardHeight = 56f * renderDensity
                    val metric = MiniPlayerRail(progressBounds.width, cardHeight, radius)
                    val localContact = metric.point(markerFraction)
                    val tangent = metric.tangent(markerFraction)
                    val outward = Offset(-tangent.y, tangent.x)
                    // Sample near the outside of the requested 7dp solid core. A
                    // vertical blade or a low-alpha glow cannot satisfy this check.
                    val sample = Offset(
                        progressBounds.left - bounds.left + localContact.x,
                        progressBounds.bottom - bounds.top - cardHeight + localContact.y,
                    ) + outward * (2.75f * renderDensity)
                    val pixel = bitmap.getPixel(
                        sample.x.toInt().coerceIn(0, bitmap.width - 1),
                        sample.y.toInt().coerceIn(0, bitmap.height - 1),
                    )
                    assertTrue("Circular needle core must remain bright at $markerFraction",
                        android.graphics.Color.red(pixel) > 180 &&
                            android.graphics.Color.green(pixel) > 180 &&
                            android.graphics.Color.blue(pixel) > 180)
                }
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
        assertTrue("Normal playback must paint visible fragments below the card", visibleBelow)
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
        compose.runOnIdle { ui.value = ui.value.copy(isSeekable = false) }
        for (percent in listOf(0, 1, 25, 50, 75, 99, 100)) {
            compose.runOnIdle { position.value = percent * 1000L }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            capture("contour-%03d.png".format(percent), percent / 100f)
        }
        for ((width, density, font) in listOf(Triple(280, 1f, 1f), Triple(320, 2.75f, 1.5f), Triple(412, 3f, 1f))) {
            compose.runOnIdle { viewport = width.dp; renderDensity = density; fontScale = font }
            // Native offscreen View drawing flushes the pending Android layout pass.
            repeat(3) {
                compose.mainClock.advanceTimeBy(64)
                compose.waitForIdle()
                capture("layout-warmup.png")
            }
            compose.onNodeWithTag("render").assertWidthIsEqualTo(width.dp)
            compose.onNodeWithContentDescription("Play").assertHeightIsAtLeast(48.dp)
            capture("layout-$width-$density-$font.png")
        }
    }
}

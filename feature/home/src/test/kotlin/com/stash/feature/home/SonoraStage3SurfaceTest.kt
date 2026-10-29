package com.stash.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.data.prefs.HomeSection
import com.stash.core.media.listen.ListenTogetherState
import com.stash.core.model.Track
import com.stash.core.ui.components.LocalBottomChromeInset
import com.stash.core.ui.components.sonoraBottomFade
import com.stash.core.ui.visual.*
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.PlaylistSummary
import com.stash.feature.nowplaying.MiniPlayer
import com.stash.feature.nowplaying.NowPlayingUiState
import com.stash.feature.nowplaying.NowPlayingViewModel
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Home/transport composables with deterministic fixture data, offscreen Android.
 * With no Haze source these captures explicitly exercise the tonal fallback, not GPU blur.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w412dp-h960dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SonoraStage3SurfaceTest(private val width: Int, private val height: Int, private val scale: Float) {
    @get:Rule val compose = createComposeRule()

    @Test fun homeScrollAndSurfaceEvidence() {
        val state = MutableStateFlow(HomeUiState(isLoading = false,
            sections = listOf(HomeSection.NEW_RELEASES, HomeSection.QOBUZ_PLAYLISTS, HomeSection.TOP_ALBUMS),
            newReleases = listOf(AlbumSummary("fixture", "Álbum de teste", "Sonora", null, "2026")),
            playlists = listOf(PlaylistSummary("playlist", "Seleção editorial", "Qobuz", null, 18)),
            topAlbums = (1..15).map { AlbumSummary("album-$it", "Álbum de teste $it", "Sonora", null, "2026") }))
        val home = mock<HomeViewModel> {
            on { uiState } doReturn state
            on { heroMinimized } doReturn MutableStateFlow(false)
            on { streamingEnabled } doReturn MutableStateFlow(false)
            on { showStreamingDisclosure } doReturn MutableSharedFlow<Unit>()
            on { userMessages } doReturn MutableSharedFlow<String>()
        }
        val track = Track(id = 1, title = "Superfície de vidro", artist = "Sonora · teste visual")
        val player = mock<NowPlayingViewModel> {
            on { uiState } doReturn MutableStateFlow(NowPlayingUiState(currentTrack = track,
                durationMs = 100000, isSeekable = true, queue = listOf(track)))
            on { positionMs } doReturn MutableStateFlow(48000L)
        }
        val listen = mock<ListenTogetherViewModel> {
            on { this.state } doReturn MutableStateFlow(ListenTogetherState.Idle)
        }
        var backdrop by mutableStateOf<Color?>(null)
        lateinit var view: View
        var destination: VisualPage? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, scale),
                LocalBottomChromeInset provides 152.dp) {
                VisualTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("capture")) {
                        HomeScreen(Modifier.fillMaxSize().testTag("home"), viewModel = home)
                        // A controlled high-contrast stress plate behind (never over) the chrome.
                        backdrop?.let { Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height(200.dp).background(it)) }
                        Column(Modifier.align(Alignment.BottomCenter).sonoraBottomFade(86.dp)) {
                            MiniPlayer({}, {}, viewModel = player, listen = listen)
                            VisualBottomBar(VisualPage.Home, null,
                                VisualActions(onPage = { destination = it }), backgroundVisible = false)
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
        val out = File("build/reports/sonora-stage3").apply { mkdirs() }
        fun capture(name: String) {
            compose.mainClock.advanceTimeBy(64)
            val bounds = compose.onNodeWithTag("capture").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                view.draw(canvas)
                File(out, "$width-${height}-font$scale-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                // Full opacity at the bottom, even over the white stress background.
                check(android.graphics.Color.red(bitmap.getPixel(width / 2, height - 2)) < 25)
                if (name == "claro" || name == "colorido") {
                    // The clear/color stress plate must be gone before the tab icon band.
                    val iconBand = bitmap.getPixel(3, height - 54)
                    check(android.graphics.Color.red(iconBand) < 40)
                    check(android.graphics.Color.green(iconBand) < 40)
                    check(android.graphics.Color.blue(iconBand) < 45)

                    // Sample the unobstructed edge of the 64dp shoulder: no single
                    // scanline may introduce a new hard horizontal divider.
                    val luma = (height - 150..height - 88).map { y ->
                        val pixel = bitmap.getPixel(3, y)
                        (android.graphics.Color.red(pixel) * 3 +
                            android.graphics.Color.green(pixel) * 6 +
                            android.graphics.Color.blue(pixel)) / 10
                    }
                    check(luma.zipWithNext { first, second -> kotlin.math.abs(second - first) }.max() < 28)
                }
                bitmap.recycle()
            }
        }
        compose.onNodeWithTag("home").performScrollToNode(hasText("Playlists do Qobuz"))
        capture("home-generos-qobuz")
        compose.onNodeWithTag("home").performTouchInput { swipeUp(startY = height * .72f, endY = height * .18f) }
        capture("home-rolada")
        for ((name, color) in listOf("claro" to Color.White, "escuro" to Color(0xFF08090C),
            "colorido" to Color(0xFFCC4477))) {
            compose.runOnIdle { backdrop = color }
            capture(name)
        }
        compose.onNodeWithContentDescription("Play").performClick()
        verify(player).onPlayPauseClick()
        compose.onNodeWithTag("visual-tab-Library").performClick()
        compose.runOnIdle { check(destination == VisualPage.Library) }
    }

    companion object {
        @JvmStatic @Parameters(name = "{0}x{1}-font{2}")
        fun sizes() = listOf(360 to 640, 393 to 873).flatMap { (w, h) ->
            listOf(1f, 1.5f).map { arrayOf<Any>(w, h, it) }
        }
    }
}

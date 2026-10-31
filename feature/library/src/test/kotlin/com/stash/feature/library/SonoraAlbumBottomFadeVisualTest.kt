package com.stash.feature.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.media.BulkPlayAction
import com.stash.core.model.Track
import com.stash.core.ui.components.LocalBottomChromeInset
import com.stash.core.ui.components.sonoraBottomFade
import com.stash.core.ui.theme.StashTheme
import com.stash.core.ui.visual.VisualActions
import com.stash.core.ui.visual.VisualBottomBar
import com.stash.core.ui.visual.VisualPage
import com.stash.core.ui.visual.VisualTheme
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Visual evidence for the real album list under the shared bottom navigation fade. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w412dp-h960dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SonoraAlbumBottomFadeVisualTest(
    private val width: Int,
    private val height: Int,
    private val scale: Float,
) {
    @get:Rule val compose = createComposeRule()

    @Test fun albumTracksDisappearBeforeNavigationControls() {
        val tracks = (1L..60L).map { index ->
            Track(
                id = index,
                title = "Faixa de teste $index",
                artist = "Sonora",
                album = "Álbum de teste",
                durationMs = 180_000,
                filePath = "/fixture/$index.flac",
            )
        }
        val viewModel = mock<AlbumDetailViewModel> {
            on { uiState } doReturn MutableStateFlow(
                AlbumDetailUiState(
                    albumName = "Álbum de teste",
                    artistName = "Sonora",
                    tracks = tracks,
                    isLoading = false,
                ),
            )
            on { tappedTrackId } doReturn MutableStateFlow(null)
            on { bulkPlayInFlight } doReturn MutableStateFlow<BulkPlayAction?>(null)
            on { userPlaylists } doReturn MutableStateFlow(emptyList())
            on { userMessages } doReturn MutableSharedFlow()
        }
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(1f, scale),
                LocalBottomChromeInset provides 88.dp,
            ) {
                StashTheme(darkTheme = true) {
                    VisualTheme {
                        Box(Modifier.requiredSize(width.dp, height.dp).testTag("album-capture")) {
                            AlbumDetailScreen(onBack = {}, viewModel = viewModel)
                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .sonoraBottomFade(88.dp),
                            ) {
                                VisualBottomBar(
                                    page = VisualPage.Home,
                                    player = null,
                                    actions = VisualActions(),
                                    backgroundVisible = false,
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.onNode(hasScrollAction()).performTouchInput {
            swipeUp(startY = height * .78f, endY = height * .2f)
        }
        val bounds = compose.onNodeWithTag("album-capture").fetchSemanticsNode().boundsInRoot
        val output = File("build/reports/sonora-final-polish").apply { mkdirs() }
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            view.draw(canvas)
            File(output, "$width-${height}-font$scale-album-faixas.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    companion object {
        @JvmStatic @Parameters(name = "{0}x{1}-font{2}")
        fun sizes() = listOf(360 to 640, 393 to 873).flatMap { (width, height) ->
            listOf(1f, 1.5f).map { arrayOf<Any>(width, height, it) }
        }
    }
}

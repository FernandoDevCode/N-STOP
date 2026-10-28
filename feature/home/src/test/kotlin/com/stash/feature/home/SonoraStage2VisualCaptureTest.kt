package com.stash.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.VisualCard
import com.stash.core.ui.visual.VisualDesign
import com.stash.core.ui.visual.VisualTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Produces reviewable PNG evidence from the real Android stage-2 components. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "pt-rBR-w480dp-h960dp-mdpi")
class SonoraStage2VisualCaptureTest(
    private val width: Int,
    private val height: Int,
    private val fontScale: Float,
) {
    @get:Rule val compose = createComposeRule()

    @Test fun captureHomeStage2() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                VisualTheme {
                    SonoraHomeTheme {
                        Box(
                            Modifier.requiredSize(width.dp, height.dp)
                                .sonoraHomeBackground().testTag("capture"),
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                NonstopMediaFilters(selected = 0, onSelect = {})
                                Spacer(Modifier.height(12.dp))
                                SonoraSectionTitle("Novos lançamentos")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
                                    horizontalArrangement = Arrangement.spacedBy(VisualDesign.Space.Rail),
                                ) {
                                    items(
                                        listOf(
                                            VisualCard("one", "Prisma noturno", "Sonora"),
                                            VisualCard("two", "Luz violeta", "Sonora"),
                                            VisualCard("three", "Horizonte frio", "Sonora"),
                                        ),
                                        key = { it.id },
                                    ) { card -> SonoraContentCard(card, onClick = {}) }
                                }
                                Spacer(Modifier.height(12.dp))
                                SonoraGenreFilters(
                                    filters = listOf("All", "Pop/Rock", "Electronic", "Classical", "Metal"),
                                    selected = "All",
                                    onSelect = {},
                                )
                                Spacer(Modifier.height(8.dp))
                                SonoraSectionTitle("Playlists do Qobuz", Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        val output = File("build/reports/sonora-stage2").apply { mkdirs() }
        val scaleLabel = if (fontScale == 1f) "normal" else "ampliada"
        val target = File(output, "home-${width}x${height}-${scaleLabel}.png")
        lateinit var capture: Bitmap
        compose.runOnUiThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).single()
            val decor = activity.window.decorView
            val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(AndroidCanvas(full))
            capture = Bitmap.createBitmap(full, 0, 0, width, height)
        }
        FileOutputStream(target).use { stream ->
            capture.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        check(target.isFile && target.length() > 0L)
    }

    companion object {
        @JvmStatic @Parameters(name = "{0}x{1}-font{2}")
        fun sizes(): List<Array<Any>> = listOf(
            320 to 568,
            360 to 640,
            360 to 800,
            393 to 873,
        ).flatMap { (width, height) ->
            listOf(1f, 1.5f).map { scale -> arrayOf<Any>(width, height, scale) }
        }
    }
}

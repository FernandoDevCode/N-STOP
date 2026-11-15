package com.stash.feature.search

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Approved fixed blue graphite, independent of search result photography. */
internal val SONORA_SEARCH_BLUE = Color(0xFF19475B)

/**
 * An endless seeded path, not a repeating easing loop. Each knot is a new
 * pseudo-random destination; Catmull-Rom interpolation keeps both position
 * and velocity continuous at every segment boundary (C1 continuity).
 * Motion never flips playback direction at a loop seam because there is none.
 */
internal data class SearchDrift(val x: Float, val y: Float, val radius: Float)

private fun randomKnot(index: Int, seed: Int): Float {
    var x = index * 374761393 + seed * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x ushr 8).toFloat() / 16777215f
}

private fun smoothWander(seconds: Float, segmentSeconds: Float, seed: Int): Float {
    val position = seconds.coerceAtLeast(0f) / segmentSeconds
    val index = position.toInt()
    val u = position - index
    val p0 = randomKnot(index - 1, seed)
    val p1 = randomKnot(index, seed)
    val p2 = randomKnot(index + 1, seed)
    val p3 = randomKnot(index + 2, seed)
    return p1 + .5f * u * (
        p2 - p0 + u * (
            2f * p0 - 5f * p1 + 4f * p2 - p3 +
                u * (3f * (p1 - p2) + p3 - p0)
        )
    )
}

/**
 * A portrait phone is taller than it is wide. v212 used screen height for
 * radii, blurring all three fields across almost the whole screen.
 * Width-relative radii reveal the flow without bright spots.
 */
internal fun searchHazeRadius(viewportWidthPx: Float, drift: SearchDrift): Float =
    viewportWidthPx * (0.72f + drift.radius * 0.45f)

internal fun searchDriftAt(seconds: Float): List<SearchDrift> =
    listOf(
        SearchDrift(
            x = .25f + .30f * (smoothWander(seconds, 17f, 11) - .5f),
            y = .21f + .25f * (smoothWander(seconds, 22f, 12) - .5f),
            radius = .66f + .13f * (smoothWander(seconds, 29f, 13) - .5f),
        ),
        SearchDrift(
            x = .76f + .30f * (smoothWander(seconds, 23f, 21) - .5f),
            y = .51f + .27f * (smoothWander(seconds, 19f, 22) - .5f),
            radius = .58f + .12f * (smoothWander(seconds, 31f, 23) - .5f),
        ),
        SearchDrift(
            x = .41f + .27f * (smoothWander(seconds, 21f, 31) - .5f),
            y = .84f + .21f * (smoothWander(seconds, 26f, 32) - .5f),
            radius = .57f + .12f * (smoothWander(seconds, 35f, 33) - .5f),
        ),
    )

@Composable
fun SonoraSearchAtmosphere(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        val animationScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            )
        }.getOrDefault(1f)
        animationScale == 0f
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            seconds = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        var lastUpdate = start
        while (isActive) {
            withFrameNanos { frame ->
                // Max ~30 fps, independent of the result list and keyboard.
                if (frame - lastUpdate >= 32_000_000L) {
                    seconds = (frame - start) / 1_000_000_000f
                    lastUpdate = frame
                }
            }
        }
    }
    val grain = remember {
        val rng = Random(208)
        List(1250) { Offset(rng.nextFloat(), rng.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF101E2B), Color(0xFF0C1823), Color(0xFF10151D)),
            ),
        )
        val drift = searchDriftAt(seconds)
        val strength = listOf(.38f, .28f, .23f)
        drift.forEachIndexed { index, v ->
            drawRect(
                brush = Brush.radialGradient(
                    listOf(
                        SONORA_SEARCH_BLUE.copy(alpha = strength[index]),
                        SONORA_SEARCH_BLUE.copy(alpha = strength[index] * .28f),
                        Color.Transparent,
                    ),
                    center = Offset(w * v.x, h * v.y),
                    radius = searchHazeRadius(w, v),
                ),
            )
        }
        // Subtle stable grain, never regenerated or moving between frames.
        drawPoints(
            grain.map { Offset(it.x * w, it.y * h) },
            pointMode = PointMode.Points,
            color = Color.White.copy(alpha = .045f),
            strokeWidth = 0.65f,
        )
    }
}

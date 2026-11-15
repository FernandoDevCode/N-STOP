package com.stash.feature.search

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.stash.core.ui.ambient.AmbientPalette
import com.stash.core.ui.ambient.ambientPaletteFromBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SONORA_BLUE = Color(0xFF19475B)

/** Keep the default blue/graphite; photo hue is intentionally limited. */
internal fun searchAtmosphereTint(artist: Color?): Color =
    if (artist == null) SONORA_BLUE else lerp(SONORA_BLUE, artist, 0.38f)

/** Sample the actual selected top artist photograph, not a song/album cover. */
@Composable
internal fun rememberSearchArtistTint(photoUrl: String?): Color? {
    val context = LocalContext.current
    val tint by produceState<Color?>(initialValue = null, key1 = photoUrl) {
        if (photoUrl.isNullOrBlank()) return@produceState
        try {
            val loaded = withContext(Dispatchers.IO) {
                SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context)
                        .data(photoUrl)
                        .size(Size(112, 112))
                        .allowHardware(false)
                        .build(),
                )
            }
            if (loaded is SuccessResult) {
                val palette = withContext(Dispatchers.Default) {
                    ambientPaletteFromBitmap(loaded.image.toBitmap(), "search:" + photoUrl)
                }
                // The shared palette's fallback is purple; Search's is always blue.
                value = palette.vib.takeUnless { palette.key == AmbientPalette.Default.key }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Offline or unreadable images simply use Sonora's default.
        }
    }
    return tint
}

/** A smooth quasi-aperiodic field: no GIF loop, reverse playback or random jumps. */
internal data class SearchDrift(val x: Float, val y: Float, val radius: Float)

internal fun searchDriftAt(seconds: Float): List<SearchDrift> {
    val t = seconds.coerceAtLeast(0f)
    return listOf(
        SearchDrift(
            .25f + .11f * sin(t * .128f + .6f) + .06f * sin(t * .047f + 1.4f),
            .19f + .065f * cos(t * .073f + 1.2f) + .037f * sin(t * .149f),
            .68f + .06f * sin(t * .057f),
        ),
        SearchDrift(
            .80f + .12f * sin(t * .091f + 2.5f) + .035f * cos(t * .041f),
            .51f + .065f * sin(t * .111f + 1.5f) + .07f * cos(t * .063f),
            .57f + .045f * cos(t * .048f + 3.1f),
        ),
        SearchDrift(
            .39f + .085f * cos(t * .065f + 1.8f) + .055f * sin(t * .119f),
            .88f + .068f * sin(t * .083f + 2.9f) + .032f * cos(t * .153f),
            .55f + .05f * sin(t * .075f + 4.2f),
        ),
    )
}

@Composable
internal fun SonoraSearchAtmosphere(tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val animationScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            )
        }.getOrDefault(1f)
        animationScale == 0f || power?.isPowerSaveMode == true
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
        val strength = listOf(.25f, .14f, .11f)
        drift.forEachIndexed { index, v ->
            drawRect(
                brush = Brush.radialGradient(
                    listOf(
                        tint.copy(alpha = strength[index]),
                        tint.copy(alpha = strength[index] * .34f),
                        Color.Transparent,
                    ),
                    center = Offset(w * v.x, h * v.y),
                    radius = maxOf(w, h) * v.radius,
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

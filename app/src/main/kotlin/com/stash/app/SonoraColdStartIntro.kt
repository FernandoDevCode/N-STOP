package com.stash.app

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

private const val ATLAS_COLUMNS = 8
private const val FRAME_PX = 256
private const val FRAME_COUNT = 64
private const val FRAME_DURATION_MS = 40
private const val INTRO_DURATION_MS = FRAME_COUNT * FRAME_DURATION_MS

/**
 * Canonical Sonora intro. The approved 16 poses are preserved in a local atlas
 * with three offline motion-interpolated frames between each pose, at 25 fps.
 * The last pose contains the approved fine-stroke SONORA wordmark.
 * No GIF decoding, filters, paths, networking or allocations per moving frame.
 *
 * Keep the MainActivity contract: settle before composing Home, then fade the
 * motionless final mark. A failed resource load must never block startup.
 */
@Composable
internal fun SonoraColdStartIntro(
    onSettled: () -> Unit,
    onFinished: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val settled = rememberUpdatedState(onSettled)
    val finished = rememberUpdatedState(onFinished)
    val formation = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    val atlas = remember { mutableStateOf<IntroAtlas?>(null) }

    LaunchedEffect(Unit) {
        val prepared = withContext(Dispatchers.Default) { loadIntroAtlas(resources) }
        if (prepared == null) {
            settled.value()
            finished.value()
            return@LaunchedEffect
        }
        atlas.value = prepared
        // Upload the single texture while the first pose is stationary.
        withFrameNanos { }
        withFrameNanos { }
        formation.animateTo(1f, tween(INTRO_DURATION_MS, easing = LinearEasing))
        settled.value()
        delay(520)
        opacity.animateTo(0f, tween(280, easing = LinearEasing))
        finished.value()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity.value }
            .background(Color(0xFF0B0E13)),
    ) {
        val prepared = atlas.value ?: return@Canvas
        val frame = introFrameAt(formation.value)
        val side = min(size.width * 0.94f, size.height * 0.63f)
        drawIntoCanvas { canvas ->
            prepared.draw(
                canvas.nativeCanvas, frame,
                (size.width - side) / 2f, (size.height - side) / 2f, side,
            )
        }
    }
}

// The frame is selected from elapsed animation time, not a queued delay per
// frame. Slow display frames therefore cannot accumulate a playback backlog.
internal fun introFrameAt(progress: Float): Int =
    (progress.coerceIn(0f, 1f) * FRAME_COUNT).toInt().coerceIn(0, FRAME_COUNT - 1)

private class IntroAtlas(private val bitmap: Bitmap) {
    private val source = Rect()
    private val destination = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = 0xFFF0F1F3.toInt()
    }

    fun draw(canvas: NativeCanvas, frame: Int, left: Float, top: Float, side: Float) {
        val x = (frame % ATLAS_COLUMNS) * FRAME_PX
        val y = (frame / ATLAS_COLUMNS) * FRAME_PX
        source.set(x, y, x + FRAME_PX, y + FRAME_PX)
        destination.set(left, top, left + side, top + side)
        canvas.drawBitmap(bitmap, source, destination, paint)
    }
}

/**
 * Decode once off the UI thread. The atlas is a white luminance mask, so
 * retaining ALPHA_8 instead of RGBA keeps the resident bitmap at about 4 MiB.
 * The Compose owner releases its reference on removal; no recycling of a
 * bitmap while HWUI may still be using it.
 */
private fun loadIntroAtlas(resources: Resources): IntroAtlas? {
    return try {
        val decoded = BitmapFactory.decodeResource(
            resources,
            R.drawable.sonora_intro_morph_atlas,
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        try {
            if (decoded.width != ATLAS_COLUMNS * FRAME_PX ||
                decoded.height != (FRAME_COUNT / ATLAS_COLUMNS) * FRAME_PX
            ) {
                Log.w("SonoraIntro", "Unexpected intro atlas dimensions")
                return null
            }
            IntroAtlas(decoded.extractAlpha())
        } finally {
            decoded.recycle()
        }
    } catch (failure: Exception) {
        Log.w("SonoraIntro", "Unable to load intro; continuing startup", failure)
        null
    } catch (failure: OutOfMemoryError) {
        Log.w("SonoraIntro", "Insufficient memory for intro; continuing startup", failure)
        null
    }
}

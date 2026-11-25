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
import androidx.compose.runtime.derivedStateOf
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
import kotlin.math.roundToInt

private const val ATLAS_COLUMNS = 8
private const val ATLAS_ROWS = 10
private const val FRAMES_PER_ATLAS = ATLAS_COLUMNS * ATLAS_ROWS
private const val FRAME_PX = 256
private const val FRAME_COUNT = 154
private const val INTRO_DURATION_MS = 2560

/**
 * Canonical Sonora intro. The approved 16 poses are preserved at their original
 * timing, with offline motion interpolation to approximately 60 fps.
 * Two bounded alpha atlases keep decode memory and texture dimensions in check.
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
    // Only invalidate the drawing when the sampled source frame changes.
    // A 120 Hz screen does not need to redraw the same 60 fps pose twice.
    val frame = remember { derivedStateOf { introFrameAt(formation.value) } }

    LaunchedEffect(Unit) {
        val prepared = withContext(Dispatchers.Default) { loadIntroAtlas(resources) }
        if (prepared == null) {
            settled.value()
            finished.value()
            return@LaunchedEffect
        }
        atlas.value = prepared
        // Both textures are prepared before publishing the state, and the first
        // stationary draw also submits both to HWUI before starting the clock.
        withFrameNanos { }
        withFrameNanos { }
        formation.animateTo(1f, tween(INTRO_DURATION_MS, easing = LinearEasing))
        // Give the last pose a display frame before composing the real Home.
        withFrameNanos { }
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
        val index = frame.value
        val side = min(size.width * 0.94f, size.height * 0.63f)
        drawIntoCanvas { canvas ->
            prepared.draw(
                canvas.nativeCanvas, index,
                (size.width - side) / 2f, (size.height - side) / 2f, side,
            )
        }
    }
}

// Sample the nearest pose from elapsed animation time, not a queued delay per
// frame. Rounding avoids alternating repeats/skips around the 60 Hz boundary.
internal fun introFrameAt(progress: Float): Int =
    (progress.coerceIn(0f, 1f) * (FRAME_COUNT - 1)).roundToInt()

private class IntroAtlas(
    private val first: Bitmap,
    private val second: Bitmap,
) {
    private val source = Rect()
    private val destination = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = 0xFFF0F1F3.toInt()
    }
    private var warmed = false

    fun draw(canvas: NativeCanvas, frame: Int, left: Float, top: Float, side: Float) {
        destination.set(left, top, left + side, top + side)
        if (!warmed) {
            // Non-zero opacity forces a real texture draw (alpha=0 can be
            // culled). At 1/255 opacity it is imperceptible behind the mark.
            drawCell(canvas, second, 0, 1)
            warmed = true
        }
        if (frame < FRAMES_PER_ATLAS) {
            drawCell(canvas, first, frame, 255)
        } else {
            drawCell(canvas, second, frame - FRAMES_PER_ATLAS, 255)
        }
    }

    private fun drawCell(canvas: NativeCanvas, bitmap: Bitmap, cell: Int, alpha: Int) {
        val x = (cell % ATLAS_COLUMNS) * FRAME_PX
        val y = (cell / ATLAS_COLUMNS) * FRAME_PX
        source.set(x, y, x + FRAME_PX, y + FRAME_PX)
        paint.alpha = alpha
        canvas.drawBitmap(bitmap, source, destination, paint)
    }
}

/**
 * Decode sequentially off the UI thread; retain only the alpha masks (10 MiB
 * total). prepareToDraw lets HWUI start texture preparation before movement.
 * Do not recycle a published bitmap while RenderThread may still use it.
 */
private fun loadIntroAtlas(resources: Resources): IntroAtlas? {
    return try {
        val first = loadIntroMask(resources, R.drawable.sonora_intro_morph_atlas)
            ?: return null
        val second = loadIntroMask(resources, R.drawable.sonora_intro_morph_atlas_end)
            ?: return null
        first.prepareToDraw()
        second.prepareToDraw()
        IntroAtlas(first, second)
    } catch (failure: Exception) {
        Log.w("SonoraIntro", "Unable to load intro; continuing startup", failure)
        null
    } catch (failure: OutOfMemoryError) {
        Log.w("SonoraIntro", "Insufficient memory for intro; continuing startup", failure)
        null
    }
}

private fun loadIntroMask(resources: Resources, resourceId: Int): Bitmap? {
    val decoded = BitmapFactory.decodeResource(
        resources,
        resourceId,
        BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return null
    return try {
        if (decoded.width != ATLAS_COLUMNS * FRAME_PX ||
            decoded.height != ATLAS_ROWS * FRAME_PX
        ) {
            Log.w("SonoraIntro", "Unexpected intro atlas dimensions")
            null
        } else {
            decoded.extractAlpha()
        }
    } finally {
        decoded.recycle()
    }
}

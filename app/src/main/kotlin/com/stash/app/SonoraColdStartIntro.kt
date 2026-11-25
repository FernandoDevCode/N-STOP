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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

private const val SOURCE_COLUMNS = 8
private const val SOURCE_ROWS = 10
private const val SOURCE_CAPACITY = SOURCE_COLUMNS * SOURCE_ROWS
private const val ATLAS_COLUMNS = 13
private const val ATLAS_ROWS = 12
private const val FRAME_PX = 256
private const val FRAME_COUNT = 154
private const val INTRO_DURATION_MS = 2560

/**
 * Canonical Sonora intro. The approved 16 poses are preserved at their original
 * timing, with offline motion interpolation to approximately 60 fps.
 * Repack both source atlases into one alpha texture off the UI thread. There
 * is no texture switch at frame 80, when the character is fully visible.
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
        // Wait for the first stationary draw to be submitted, then leave two
        // frame ticks for HWUI before starting motion. Bound the wait if hidden.
        withTimeoutOrNull(500L) { prepared.firstDraw.await() }
        withFrameNanos { }
        withFrameNanos { }
        formation.animateTo(1f, tween(INTRO_DURATION_MS, easing = LinearEasing))
        // Give the last pose a display frame before composing the real Home.
        withFrameNanos { }
        prepared.logPacing()
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

private class IntroAtlas(private val bitmap: Bitmap) {
    private val source = Rect()
    private val destination = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = 0xFFF0F1F3.toInt()
    }
    val firstDraw = CompletableDeferred<Unit>()
    // Debug-only UI draw interval diagnostics. No per-frame logs, no telemetry.
    // These measure draw submissions, NOT actual GPU presentation times.
    private var previousIndex = 0
    private var previousNanos = 0L
    private var longestNanos = 0L
    private var longestAt = 0
    private var slowDraws = 0
    private var skippedPoses = 0

    fun draw(canvas: NativeCanvas, frame: Int, left: Float, top: Float, side: Float) {
        val x = (frame % ATLAS_COLUMNS) * FRAME_PX
        val y = (frame / ATLAS_COLUMNS) * FRAME_PX
        source.set(x, y, x + FRAME_PX, y + FRAME_PX)
        destination.set(left, top, left + side, top + side)
        canvas.drawBitmap(bitmap, source, destination, paint)
        if (!firstDraw.isCompleted) firstDraw.complete(Unit)
        if (BuildConfig.DEBUG && frame > previousIndex) {
            val now = System.nanoTime()
            if (previousNanos != 0L) {
                val gap = now - previousNanos
                if (gap > 33_333_334L) slowDraws++
                if (gap > longestNanos) {
                    longestNanos = gap
                    longestAt = frame
                }
            }
            skippedPoses += (frame - previousIndex - 1).coerceAtLeast(0)
            previousIndex = frame
            previousNanos = now
        }
    }

    fun logPacing() {
        if (BuildConfig.DEBUG) {
            Log.d("SonoraIntro", "draw pacing: slowDraws=$slowDraws skippedPoses=$skippedPoses " +
                "longestMs=${longestNanos / 1_000_000.0} atFrame=$longestAt")
        }
    }
}

/**
 * Repack the existing WebP cells into one 3328 x 3072 ALPHA_8 texture
 * (~9.75 MiB). Copy each source sequentially, at exact integer coordinates,
 * without filtering, interpolation or changing the stored poses. The temporary
 * RGBA source is recycled before decoding the next. A published atlas is
 * owned by Compose and must not be recycled while HWUI may still use it.
 */
private fun loadIntroAtlas(resources: Resources): IntroAtlas? {
    var target: Bitmap? = null
    var published = false
    return try {
        val bitmap = Bitmap.createBitmap(
            ATLAS_COLUMNS * FRAME_PX,
            ATLAS_ROWS * FRAME_PX,
            Bitmap.Config.ALPHA_8,
        )
        target = bitmap
        val canvas = NativeCanvas(bitmap)
        if (!copyIntroCells(resources, R.drawable.sonora_intro_morph_atlas,
                canvas, 0, SOURCE_CAPACITY) ||
            !copyIntroCells(resources, R.drawable.sonora_intro_morph_atlas_end,
                canvas, SOURCE_CAPACITY, FRAME_COUNT - SOURCE_CAPACITY)
        ) return null
        bitmap.prepareToDraw()
        val atlas = IntroAtlas(bitmap)
        published = true
        atlas
    } catch (failure: Exception) {
        Log.w("SonoraIntro", "Unable to load intro; continuing startup", failure)
        null
    } catch (failure: OutOfMemoryError) {
        Log.w("SonoraIntro", "Insufficient memory for intro; continuing startup", failure)
        null
    } finally {
        if (!published) target?.recycle()
    }
}

private fun copyIntroCells(
    resources: Resources,
    resourceId: Int,
    canvas: NativeCanvas,
    firstFrame: Int,
    count: Int,
): Boolean {
    val decoded = BitmapFactory.decodeResource(
        resources,
        resourceId,
        BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return false
    return try {
        if (decoded.width != SOURCE_COLUMNS * FRAME_PX ||
            decoded.height != SOURCE_ROWS * FRAME_PX
        ) {
            Log.w("SonoraIntro", "Unexpected intro atlas dimensions")
            false
        } else {
            val source = Rect()
            val destination = Rect()
            for (cell in 0 until count) {
                val x = (cell % SOURCE_COLUMNS) * FRAME_PX
                val y = (cell / SOURCE_COLUMNS) * FRAME_PX
                val frame = firstFrame + cell
                val dx = (frame % ATLAS_COLUMNS) * FRAME_PX
                val dy = (frame / ATLAS_COLUMNS) * FRAME_PX
                source.set(x, y, x + FRAME_PX, y + FRAME_PX)
                destination.set(dx, dy, dx + FRAME_PX, dy + FRAME_PX)
                canvas.drawBitmap(decoded, source, destination, null)
            }
            true
        }
    } finally {
        decoded.recycle()
    }
}

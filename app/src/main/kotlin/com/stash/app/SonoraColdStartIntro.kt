package com.stash.app

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Cinematic Sonora cold-start. Chrome gradients, glows and geometry are rasterized
 * ONCE, off the UI thread. Animation frames only draw GPU-backed bitmap layers
 * with simple clipping/opacity: no BlurMaskFilter, PathMeasure, shaders, allocations
 * of new paths or gradients in the hot frame loop.
 *
 * At t=0 the window is absolutely black. The mark stays chrome-lit at the end.
 */
@Composable
internal fun SonoraColdStartIntro(
    onSettled: () -> Unit,
    onFinished: () -> Unit,
) {
    val formation = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    val layers = remember { mutableStateOf<LogoLayers?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            layers.value?.dispose()
            layers.value = null
        }
    }

    LaunchedEffect(Unit) {
        // A cold boot gets a static black screen while shaders/blur are prepared
        // on Default, rather than blocking the main thread between animation frames.
        val prepared = withContext(Dispatchers.Default) { buildLogoLayers() }
        layers.value = prepared
        // Give HWUI two vsyncs to upload the 5 tiny textures during the
        // visually black first frame, avoiding first-use GPU upload hitches.
        withFrameNanos { }
        withFrameNanos { }
        formation.animateTo(1f, tween(2950, easing = LinearEasing))
        // Start the real app only after ALL moving logo geometry has stopped.
        // NavHost/Home initial composition therefore cannot steal its frame budget.
        onSettled()
        delay(520)
        opacity.animateTo(0f, tween(280, easing = LinearEasing))
        onFinished()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity.value }
            .background(Color(0xFF0B0E13)),
    ) {
        val available = layers.value ?: return@Canvas
        val progress = formation.value
        val side = min(size.width * 0.94f, size.height * 0.63f)
        val unit = side / LOGO_UNITS

        drawIntoCanvas { composeCanvas ->
            val native = composeCanvas.nativeCanvas
            native.save()
            native.translate((size.width - side) / 2f, (size.height - side) / 2f)
            native.scale(unit, unit)

            if (progress == 0f) {
                // 1/255 alpha is visually indistinguishable from black, but
                // forces textures into GPU memory before the moving frames.
                for (bitmap in arrayOf(
                    available.ignition, available.spine, available.left,
                    available.right, available.bars,
                )) {
                    available.draw(native, bitmap, 1f / 255f)
                }
            }

            val ignition = phase(progress, .025f, .11f) *
                (1f - phase(progress, .20f, .30f))
            available.draw(native, available.ignition, ignition)

            val spine = phase(progress, .10f, .35f)
            available.draw(
                native, available.spine, spine,
                0f, 500f - 420f * spine, LOGO_UNITS, 500f + 420f * spine,
            )

            // Two independent illuminated arcs: reveal top-to-bottom without
            // ever recomputing the chrome, mask blur or bezier curves.
            val left = phase(progress, .20f, .71f)
            available.draw(
                native, available.left, phase(progress, .20f, .64f),
                0f, 0f, 1000f, 200f + 790f * left,
            )
            val right = phase(progress, .27f, .76f)
            available.draw(
                native, available.right, phase(progress, .27f, .70f),
                0f, 0f, 1000f, 90f + 850f * right,
            )

            // Waveform progressively expands both outwards and vertically.
            val bars = phase(progress, .53f, .84f)
            available.draw(
                native, available.bars, bars,
                503f - 260f * bars, 500f - 116f * bars,
                503f + 260f * bars, 500f + 116f * bars,
            )
            native.restore()
        }
    }
}

private const val LOGO_UNITS = 1000f
private const val RASTER_PX = 512

private fun phase(time: Float, start: Float, finish: Float): Float {
    val x = ((time - start) / (finish - start)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** The only objects accessed by the per-frame drawing callback. */
private class LogoLayers(
    val ignition: Bitmap,
    val spine: Bitmap,
    val left: Bitmap,
    val right: Bitmap,
    val bars: Bitmap,
) {
    private val source = Rect(0, 0, RASTER_PX, RASTER_PX)
    private val destination = RectF(0f, 0f, LOGO_UNITS, LOGO_UNITS)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(
        canvas: NativeCanvas, bitmap: Bitmap, alpha: Float,
        left: Float = 0f, top: Float = 0f,
        right: Float = LOGO_UNITS, bottom: Float = LOGO_UNITS,
    ) {
        val opacity = (alpha * 255).toInt().coerceIn(0, 255)
        if (opacity == 0) return
        paint.alpha = opacity
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        canvas.drawBitmap(bitmap, source, destination, paint)
        canvas.restore()
    }

    fun dispose() {
        ignition.recycle()
        spine.recycle()
        left.recycle()
        right.recycle()
        bars.recycle()
    }
}

/**
 * This runs on Dispatchers.Default once per cold launch. Software blur is allowed
 * here because it no longer blocks RenderThread / per-frame Compose draw calls.
 * 5 * 512px RGBA textures ~= 5 MiB; no full-screen bitmap or animation frame cache.
 */
private fun buildLogoLayers(): LogoLayers = LogoLayers(
    ignition = raster { canvas ->
        val haze = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                503f, 500f, 72f,
                intArrayOf(0x82D3E1FF.toInt(), 0x00D3E1FF), null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(503f, 500f, 72f, haze)
        canvas.drawCircle(503f, 500f, 2.5f, solid(NativeColor.WHITE))
    },
    spine = raster { canvas ->
        val path = Path().apply {
            moveTo(503f, 82f)
            lineTo(503f, 918f)
        }
        glow(canvas, path, 1f)
        canvas.drawPath(path, solid(0xFFF1F7FF.toInt()).apply {
            strokeWidth = 2.7f
            style = Paint.Style.STROKE
        })
    },
    left = raster { canvas ->
        val shape = leftShape()
        glow(canvas, shape, .38f)
        canvas.drawPath(shape, solid(NativeColor.WHITE).apply { shader = chrome() })
        // Edge light remains in the finished mark; it never switches to flat white.
        glow(canvas, leftLightGuide(), .56f)
        canvas.drawPath(leftLightGuide(), solid(0xFFEDF5FF.toInt()).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.1f
            alpha = 115
        })
    },
    right = raster { canvas ->
        val shape = rightShape()
        glow(canvas, shape, .38f)
        canvas.drawPath(shape, solid(NativeColor.WHITE).apply { shader = chrome() })
        glow(canvas, rightLightGuide(), .56f)
        canvas.drawPath(rightLightGuide(), solid(0xFFEDF5FF.toInt()).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.1f
            alpha = 115
        })
    },
    bars = raster { canvas ->
        for ((x, h) in arrayOf(
            456f to 196f, 550f to 196f,
            409f to 124f, 597f to 124f,
            363f to 56f, 643f to 56f,
        )) {
            val rect = RectF(x - 8.5f, 500f - h / 2f, x + 8.5f, 500f + h / 2f)
            val halo = solid(0x3EB4CFFF).apply {
                style = Paint.Style.STROKE
                strokeWidth = 12f
                maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(rect, 8.5f, 8.5f, halo)
            canvas.drawRoundRect(rect, 8.5f, 8.5f, solid(NativeColor.WHITE).apply {
                shader = LinearGradient(
                    x - 10f, 0f, x + 13f, 0f,
                    intArrayOf(0xFF8998B6.toInt(), 0xFFF6FAFF.toInt(), 0xFFC3D1E9.toInt()),
                    null, Shader.TileMode.CLAMP,
                )
            })
        }
    },
)

private fun raster(draw: (NativeCanvas) -> Unit): Bitmap {
    val bitmap = Bitmap.createBitmap(RASTER_PX, RASTER_PX, Bitmap.Config.ARGB_8888)
    val canvas = NativeCanvas(bitmap)
    canvas.scale(RASTER_PX / LOGO_UNITS, RASTER_PX / LOGO_UNITS)
    draw(canvas)
    return bitmap
}

private fun solid(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun glow(canvas: NativeCanvas, path: Path, strength: Float) {
    val p = solid(android.graphics.Color.argb((30f * strength).toInt(), 173, 200, 255)).apply {
        style = Paint.Style.STROKE
        strokeWidth = 26f
        maskFilter = BlurMaskFilter(19f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(path, p)
    p.maskFilter = null
    p.strokeWidth = 9f
    p.color = android.graphics.Color.argb((36f * strength).toInt(), 190, 212, 255)
    canvas.drawPath(path, p)
}

private fun chrome(): Shader = LinearGradient(
    250f, 220f, 740f, 780f,
    intArrayOf(
        0xFFFAFCFF.toInt(),
        0xFF8F9DB5.toInt(),
        0xFFDDE8F8.toInt(),
        0xFFF9FAFD.toInt(),
        0xFF7284A3.toInt(),
    ),
    floatArrayOf(0f, .27f, .53f, .73f, 1f),
    Shader.TileMode.CLAMP,
)

private fun leftShape(): Path = Path().apply {
    moveTo(455f, 238f)
    cubicTo(340f, 248f, 246f, 356f, 242f, 475f)
    cubicTo(236f, 607f, 324f, 702f, 434f, 727f)
    cubicTo(481f, 740f, 493f, 772f, 504f, 929f)
    cubicTo(503f, 840f, 504f, 753f, 439f, 713f)
    cubicTo(336f, 682f, 280f, 595f, 283f, 477f)
    cubicTo(289f, 357f, 351f, 267f, 455f, 238f)
    close()
}

private fun rightShape(): Path = Path().apply {
    moveTo(499f, 77f)
    cubicTo(505f, 166f, 501f, 207f, 540f, 220f)
    cubicTo(666f, 249f, 756f, 365f, 757f, 476f)
    cubicTo(760f, 611f, 670f, 715f, 548f, 759f)
    cubicTo(651f, 669f, 716f, 599f, 713f, 478f)
    cubicTo(710f, 356f, 641f, 291f, 548f, 277f)
    cubicTo(524f, 270f, 511f, 282f, 509f, 322f)
    cubicTo(507f, 518f, 506f, 724f, 504f, 927f)
    cubicTo(502f, 643f, 500f, 310f, 499f, 77f)
    close()
}

private fun leftLightGuide(): Path = Path().apply {
    moveTo(449f, 240f)
    cubicTo(309f, 283f, 250f, 389f, 261f, 492f)
    cubicTo(268f, 645f, 362f, 696f, 446f, 727f)
    cubicTo(488f, 750f, 500f, 813f, 504f, 922f)
}

private fun rightLightGuide(): Path = Path().apply {
    moveTo(500f, 92f)
    cubicTo(503f, 197f, 517f, 229f, 557f, 238f)
    cubicTo(700f, 280f, 744f, 405f, 734f, 500f)
    cubicTo(724f, 630f, 648f, 705f, 552f, 755f)
}

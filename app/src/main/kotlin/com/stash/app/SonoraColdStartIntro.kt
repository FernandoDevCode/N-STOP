package com.stash.app

import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Sonora cold-start identity. This is a native, timeline-driven drawing, NOT a GIF:
 * first frame is absolutely dark; the image forms progressively and keeps its
 * graphite/chrome lighting until the entire overlay crossfades into the app.
 *
 * Rendering is independent of display resolution, density and decoder frame limits.
 * Only used for a normal cold launcher start; never for deep links or recreation.
 */
@Composable
internal fun SonoraColdStartIntro(onFinished: () -> Unit) {
    val formation = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        formation.animateTo(1f, tween(2950, easing = LinearEasing))
        delay(520) // The *illuminated* logo stays on screen; it never becomes flat.
        opacity.animateTo(0f, tween(280, easing = LinearEasing))
        onFinished()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity.value }
            .background(Color(0xFF0B0E13)),
    ) {
        val t = formation.value
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val side = min(size.width * 0.94f, size.height * 0.63f)
            val unit = side / 1000f
            canvas.save()
            canvas.translate((size.width - side) / 2f, (size.height - side) / 2f)
            canvas.scale(unit, unit)
            drawSonoraMark(canvas, t)
            canvas.restore()
        }
    }
}

private fun phase(time: Float, start: Float, finish: Float): Float {
    val x = ((time - start) / (finish - start)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun rgba(alpha: Int, r: Int, g: Int, b: Int): Int =
    android.graphics.Color.argb(alpha.coerceIn(0, 255), r, g, b)

private fun brush(alpha: Float = 1f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun chrome(): Shader = LinearGradient(
    250f, 220f, 740f, 780f,
    intArrayOf(
        android.graphics.Color.rgb(250, 252, 255),
        android.graphics.Color.rgb(143, 157, 181),
        android.graphics.Color.rgb(221, 232, 248),
        android.graphics.Color.rgb(249, 250, 253),
        android.graphics.Color.rgb(114, 132, 163),
    ),
    floatArrayOf(0f, 0.27f, 0.53f, 0.73f, 1f),
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

private fun drawGlowPath(canvas: NativeCanvas, path: Path, strength: Float) {
    if (strength <= 0f) return
    val glow = brush().apply {
        color = rgba((30f * strength).toInt(), 173, 200, 255)
        style = Paint.Style.STROKE
        strokeWidth = 26f
        maskFilter = BlurMaskFilter(19f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(path, glow)
    glow.maskFilter = null
    glow.strokeWidth = 9f
    glow.color = rgba((36f * strength).toInt(), 190, 212, 255)
    canvas.drawPath(path, glow)
}

private fun traceLight(canvas: NativeCanvas, path: Path, visible: Float, brightness: Float) {
    if (visible <= 0f || brightness <= 0f) return
    val measure = PathMeasure(path, false)
    val trace = Path()
    val distance = measure.length * visible.coerceIn(0f, 1f)
    val tail = measure.length * 0.34f
    measure.getSegment((distance - tail).coerceAtLeast(0f), distance, trace, true)
    drawGlowPath(canvas, trace, brightness)
    val line = brush().apply {
        color = android.graphics.Color.WHITE
        alpha = (255f * brightness).toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawPath(trace, line)
}

private fun drawSonoraMark(canvas: NativeCanvas, t: Float) {
    // Nothing at t=0. A single faint white ignition point emerges first.
    val ignition = phase(t, 0.025f, 0.11f) * (1f - phase(t, 0.20f, 0.30f))
    if (ignition > 0f) {
        val haze = brush().apply {
            shader = RadialGradient(
                503f, 500f, 72f,
                intArrayOf(rgba((130 * ignition).toInt(), 211, 225, 255), rgba(0, 211, 225, 255)),
                null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(503f, 500f, 72f, haze)
        canvas.drawCircle(503f, 500f, 2.5f, brush().apply {
            color = android.graphics.Color.WHITE
            alpha = (255f * ignition).toInt()
        })
    }

    val spine = phase(t, 0.10f, 0.35f)
    if (spine > 0f) {
        val line = Path().apply {
            moveTo(503f, 500f - 418f * spine)
            lineTo(503f, 500f + 418f * spine)
        }
        drawGlowPath(canvas, line, spine)
        canvas.drawPath(line, brush().apply {
            color = rgba((240 * spine).toInt(), 236, 244, 255)
            strokeWidth = 2.7f
            style = Paint.Style.STROKE
        })
    }

    val left = leftShape()
    val right = rightShape()
    val leftVisible = phase(t, 0.32f, 0.69f)
    val rightVisible = phase(t, 0.37f, 0.73f)

    // A specular edge moves over the symbol *before* the body becomes visible.
    traceLight(canvas, leftLightGuide(), phase(t, 0.20f, 0.64f), 1f - 0.3f * phase(t, 0.66f, 0.85f))
    traceLight(canvas, rightLightGuide(), phase(t, 0.29f, 0.70f), 1f - 0.3f * phase(t, 0.69f, 0.85f))

    // Permanent chrome treatment: this is never replaced with a flat-white icon.
    val surface = brush().apply { shader = chrome() }
    if (leftVisible > 0f) {
        drawGlowPath(canvas, left, 0.38f * leftVisible)
        surface.alpha = (leftVisible * 255).toInt()
        canvas.drawPath(left, surface)
    }
    if (rightVisible > 0f) {
        drawGlowPath(canvas, right, 0.38f * rightVisible)
        surface.alpha = (rightVisible * 255).toInt()
        canvas.drawPath(right, surface)
    }

    // The waveform is born from the middle, then expands to the outer short bars.
    val bars = arrayOf(
        floatArrayOf(456f, 196f, 0f),
        floatArrayOf(550f, 196f, 0f),
        floatArrayOf(409f, 124f, 0.035f),
        floatArrayOf(597f, 124f, 0.035f),
        floatArrayOf(363f, 56f, 0.065f),
        floatArrayOf(643f, 56f, 0.065f),
    )
    for (bar in bars) {
        val appear = phase(t, 0.54f + bar[2], 0.75f + bar[2])
        if (appear <= 0f) continue
        val h = bar[1] * appear
        val rect = RectF(bar[0] - 8.5f, 500f - h / 2f, bar[0] + 8.5f, 500f + h / 2f)
        val glow = brush().apply {
            style = Paint.Style.STROKE
            color = rgba((62 * appear).toInt(), 180, 207, 255)
            strokeWidth = 12f
            maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(rect, 8.5f, 8.5f, glow)
        canvas.drawRoundRect(rect, 8.5f, 8.5f, brush(appear).apply {
            shader = LinearGradient(
                bar[0] - 10f, 0f, bar[0] + 13f, 0f,
                intArrayOf(0xFF8998B6.toInt(), 0xFFF6FAFF.toInt(), 0xFFC3D1E9.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        })
    }

    // Persistent delicate rim highlights after formation, not an abrupt flat end frame.
    val settled = phase(t, 0.76f, 0.92f)
    if (settled > 0f) {
        val shine = brush(settled * 0.35f).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.1f
            shader = LinearGradient(
                240f, 255f, 740f, 705f,
                intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF, 0xFFEDF5FF.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(leftLightGuide(), shine)
        canvas.drawPath(rightLightGuide(), shine)
    }
}

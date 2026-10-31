package com.stash.core.ui.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin

/** Optical 24-unit glyphs from the supplied navigation references, shared by both hosts. */
@Composable fun VisualNavigationIcon(page: VisualPage, selected: Boolean, modifier: Modifier = Modifier) {
    val ink = if (selected && LocalVisualDark.current) VisualDesign.Light.Core else if (selected) VisualDesign.Ink else VisualDesign.Muted
    val dark = LocalVisualDark.current
    val outline = remember(page) {
        Path().apply {
            when (page) {
                VisualPage.Home -> {
                    moveTo(3.5f, 10f); lineTo(12f, 3f); lineTo(20.5f, 10f)
                    lineTo(20.5f, 21f); lineTo(14.8f, 21f); lineTo(14.8f, 14.2f)
                    lineTo(9.2f, 14.2f); lineTo(9.2f, 21f); lineTo(3.5f, 21f); close()
                }
                VisualPage.Explore -> addOval(androidx.compose.ui.geometry.Rect(3f, 3f, 21f, 21f))
                VisualPage.Search -> {
                    addOval(androidx.compose.ui.geometry.Rect(3f, 2.5f, 18.5f, 18f))
                    moveTo(16.6f, 16.2f); lineTo(22f, 21.5f)
                }
                VisualPage.Library -> {
                    moveTo(4f, 3.5f); lineTo(4f, 20.5f)
                    moveTo(8.8f, 3.5f); lineTo(8.8f, 20.5f)
                    moveTo(13.6f, 3.5f); lineTo(13.6f, 20.5f)
                    moveTo(18f, 4.5f); lineTo(22f, 20.5f)
                }
                VisualPage.Radio -> {
                    moveTo(5.5f, 4.5f); cubicTo(.2f, 8.6f, .2f, 15.4f, 5.5f, 19.5f)
                    moveTo(8.7f, 7.5f); cubicTo(5.7f, 10f, 5.7f, 14f, 8.7f, 16.5f)
                    moveTo(18.5f, 4.5f); cubicTo(23.8f, 8.6f, 23.8f, 15.4f, 18.5f, 19.5f)
                    moveTo(15.3f, 7.5f); cubicTo(18.3f, 10f, 18.3f, 14f, 15.3f, 16.5f)
                }
            }
        }
    }
    val light by animateFloatAsState(if (selected && dark) 1f else 0f, tween(VisualDesign.Light.TransitionMs), label = "navigation-emission")
    val nativeBlur = remember { BlurEffect(1f, 1f).isSupported() }
    Box(modifier) {
        if (nativeBlur && light > 0f) {
            val energy = when (page) { VisualPage.Radio -> VisualDesign.Light.RadioEnergy; VisualPage.Library -> VisualDesign.Light.LibraryEnergy; else -> 1f }
            for ((radius, opacity) in listOf(VisualDesign.Light.WideRadius to VisualDesign.Light.WideAlpha, VisualDesign.Light.NearRadius to VisualDesign.Light.NearAlpha)) {
                Canvas(Modifier.matchParentSize().blur(radius, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = light * opacity * energy }) {
                    withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
                        val color = if (radius == VisualDesign.Light.WideRadius) VisualDesign.Light.Wide else VisualDesign.Light.Near
                        if (page == VisualPage.Home || page == VisualPage.Explore) drawPath(outline, color)
                        else drawPath(outline, color, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
                if (!nativeBlur && light > 0f) glow(outline, light)
                val filled = selected && page in listOf(VisualPage.Home, VisualPage.Explore)
                if (filled) drawPath(outline, ink)
                else drawPath(outline, ink, style = Stroke(if (selected) 2f else 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (page == VisualPage.Explore) {
                    val needle = Path().apply {
                        moveTo(8f, 16f); lineTo(10f, 10f); lineTo(16f, 8f); lineTo(14f, 14f); close()
                    }
                    val needleInk = if (selected) Color(0xFF57506C) else ink
                    drawPath(needle, needleInk, style = Stroke(1.2f, join = StrokeJoin.Round))
                    drawCircle(needleInk, .9f, Offset(12f, 12f))
                }
                if (page == VisualPage.Radio) drawCircle(ink, if (selected) 2.1f else 1.6f, Offset(12f, 12f))
            }
        }
    }
}

private fun DrawScope.glow(path: Path, strength: Float) {
    // Compatibility bloom where native render effects are unavailable (Android before API 31).
    VisualDesign.Light.FallbackStrokes.forEach { (width, opacity) ->
        drawPath(path, VisualDesign.Light.Fallback.copy(alpha = opacity * strength),
            style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

enum class VisualHeaderSymbol { Cast, Bell, Settings }

@Composable fun VisualHeaderIcon(symbol: VisualHeaderSymbol, modifier: Modifier = Modifier) {
    val ink = VisualDesign.Ink.copy(alpha = .91f)
    Canvas(modifier) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            val path = Path().apply {
                when (symbol) {
                    VisualHeaderSymbol.Cast -> {
                        moveTo(4f, 7f); lineTo(4f, 4f); lineTo(21f, 4f); lineTo(21f, 17f); lineTo(15f, 17f)
                        moveTo(3f, 10f); cubicTo(9.2f, 10f, 14f, 14.8f, 14f, 21f)
                        moveTo(3f, 14f); cubicTo(7f, 14f, 10f, 17f, 10f, 21f)
                        moveTo(3f, 18f); cubicTo(4.8f, 18f, 6f, 19.2f, 6f, 21f)
                    }
                    VisualHeaderSymbol.Bell -> {
                        moveTo(5f, 17f); cubicTo(7f, 15f, 7f, 13f, 7f, 9f)
                        cubicTo(7f, 3f, 17f, 3f, 17f, 9f)
                        cubicTo(17f, 13f, 17f, 15f, 19f, 17f); close()
                        moveTo(10f, 20f); quadraticTo(12f, 22f, 14f, 20f)
                        moveTo(12f, 3f); lineTo(12f, 2f)
                    }
                    VisualHeaderSymbol.Settings -> {
                        // Eight shallow teeth and a small open centre match the thin header set.
                        repeat(32) { index ->
                            val angle = index * Math.PI / 16 - Math.PI / 2
                            val radius = if (index % 4 in 1..2) 10f else 7.8f
                            val x = 12f + cos(angle).toFloat() * radius
                            val y = 12f + sin(angle).toFloat() * radius
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close(); addOval(androidx.compose.ui.geometry.Rect(8.7f, 8.7f, 15.3f, 15.3f))
                    }
                }
            }
            drawPath(path, ink, style = Stroke(1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (symbol == VisualHeaderSymbol.Cast) drawCircle(ink, .8f, Offset(3f, 21f))
        }
    }
}

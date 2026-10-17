package com.stash.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.stash.core.ui.theme.NonstopDesign

/** Optical outline icons; selection glows without an indicator container. */
@Composable
fun NonstopNavIcon(destination: Int, selected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (selected) drawCircle(Brush.radialGradient(listOf(
            NonstopDesign.Violet.copy(alpha = .32f), NonstopDesign.Violet.copy(alpha = 0f)),
            center, size.width * 1.15f), size.width * 1.15f)
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            val path = Path().apply {
                when (destination) {
                    0 -> { moveTo(3f,10f); lineTo(12f,3f); lineTo(21f,10f); lineTo(21f,21f)
                        lineTo(15f,21f); lineTo(15f,14f); quadraticTo(12f,12f,9f,14f)
                        lineTo(9f,21f); lineTo(3f,21f); close() }
                    1 -> { moveTo(8f,16f); lineTo(10f,10f); lineTo(16f,8f); lineTo(14f,14f); close() }
                    2 -> { moveTo(6f,4f); quadraticTo(-1f,12f,6f,20f)
                        moveTo(9f,7f); quadraticTo(4f,12f,9f,17f)
                        moveTo(18f,4f); quadraticTo(25f,12f,18f,20f)
                        moveTo(15f,7f); quadraticTo(20f,12f,15f,17f) }
                    3 -> { moveTo(3f,3f); lineTo(3f,21f); moveTo(8f,3f); lineTo(8f,21f)
                        moveTo(13f,3f); lineTo(13f,21f); moveTo(17f,7f); lineTo(23f,21f) }
                    else -> { moveTo(16.5f,16.5f); lineTo(22f,22f) }
                }
            }
            val ink = if (selected) NonstopDesign.ActiveInk else NonstopDesign.Secondary
            if (selected) drawPath(path, NonstopDesign.Violet.copy(alpha = .35f), style = Stroke(3.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, ink, style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (destination == 1 || destination == 4) drawCircle(ink,
                if (destination == 1) 10f else 8f,
                if (destination == 1) Offset(12f,12f) else Offset(10f,10f), style = Stroke(1.4f))
            if (destination == 2) drawCircle(ink, 2.2f, Offset(12f,12f))
        }
    }
}

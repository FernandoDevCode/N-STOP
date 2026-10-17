package com.stash.feature.nowplaying

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stash.core.ui.ambient.AlbumChrome

/** Only the lower half of each bottom corner, joined by the bottom edge. No side strokes. */
internal fun Modifier.miniPlayerProgress(
    position: State<Long>,
    durationMs: Long,
    chrome: AlbumChrome,
    cornerRadius: Dp,
) = drawWithCache {
    val stroke = 2.dp.toPx()
    val inset = stroke / 2f
    val radius = cornerRadius.toPx().coerceAtMost(size.width / 2f).coerceAtMost(size.height / 2f)
    val inner = (radius - inset).coerceAtLeast(.1f)
    val cy = size.height - radius
    val rail = Path().apply {
        arcTo(Rect(radius - inner, cy - inner, radius + inner, cy + inner), 135f, -45f, true)
        lineTo(size.width - radius, size.height - inset)
        arcTo(Rect(size.width - radius - inner, cy - inner,
            size.width - radius + inner, cy + inner), 90f, -45f, false)
    }
    val measure = PathMeasure().apply { setPath(rail, false) }
    val filled = Path()
    val style = Stroke(stroke, cap = StrokeCap.Round)
    onDrawWithContent {
        drawContent()
        // Read playback and palette only in the drawing phase, not on every recomposition.
        val distance = measure.length * progressFraction(position.value, durationMs)
        val accent = chrome.vib
        drawPath(rail, Color(0xFF353B49), style = style)
        filled.reset()
        if (measure.getSegment(0f, distance, filled, true)) drawPath(filled, accent, style = style)
        val dot = measure.getPosition(distance)
        val glow = 7.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .45f),
            accent.copy(alpha = 0f)), dot, glow), glow, dot)
        drawCircle(accent, 3.2.dp.toPx(), dot)
        drawCircle(Color.White.copy(alpha = .9f), 2.2.dp.toPx(), dot)
    }
}

/** Behind the song/controls so seeking never steals their taps or swipe-to-skip gestures. */
@Composable
internal fun MiniPlayerSeekTarget(
    position: State<Long>,
    durationMs: Long,
    trackKey: Long?,
    cornerRadius: Dp,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = if (enabled) Modifier
        .pointerInput(trackKey, durationMs, cornerRadius) {
            val r = cornerRadius.toPx()
            val edge = r - (r - 1.dp.toPx()) * .70710678f
            detectTapGestures { point ->
                val fraction = ((point.x - edge) / (size.width - 2 * edge).coerceAtLeast(1f)).coerceIn(0f, 1f)
                onSeek((fraction * durationMs).toLong())
            }
        }
        .pointerInput(trackKey, durationMs, cornerRadius) {
            val r = cornerRadius.toPx()
            val edge = r - (r - 1.dp.toPx()) * .70710678f
            detectHorizontalDragGestures { change, _ ->
                change.consume()
                val fraction = ((change.position.x - edge) / (size.width - 2 * edge).coerceAtLeast(1f)).coerceIn(0f, 1f)
                onSeek((fraction * durationMs).toLong())
            }
        }
    else Modifier
    Box(modifier.fillMaxWidth().height(48.dp).then(input).semantics(mergeDescendants = true) {
        contentDescription = "Playback progress"
        progressBarRangeInfo = ProgressBarRangeInfo(progressFraction(position.value, durationMs), 0f..1f)
        if (!enabled) disabled()
        setProgress { fraction ->
            if (enabled && fraction.isFinite()) {
                onSeek((fraction.coerceIn(0f, 1f) * durationMs).toLong())
                true
            } else false
        }
    })
}

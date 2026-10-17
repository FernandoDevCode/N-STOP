package com.stash.feature.nowplaying

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stash.core.ui.ambient.AlbumChrome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Only the lower half of each bottom corner, joined by the bottom edge. No side strokes. */
internal fun Modifier.miniPlayerProgress(
    position: State<Long>,
    durationMs: Long,
    seek: MiniPlayerSeekState,
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
        val distance = measure.length * seek.displayFraction(position.value, durationMs)
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

/** Local drag position wins over playback ticks until release and acknowledgement. */
@Stable
internal class MiniPlayerSeekState {
    private var preview by mutableStateOf<Float?>(null)
    var pendingMs by mutableStateOf<Long?>(null)
        private set
    var generation by mutableIntStateOf(0)
        private set
    var dragging = false
        private set

    fun displayFraction(positionMs: Long, durationMs: Long): Float =
        preview ?: progressFraction(positionMs, durationMs)

    fun begin(fraction: Float) {
        generation++
        pendingMs = null
        dragging = true
        preview = fraction
    }
    fun move(fraction: Float) { preview = fraction }
    fun finish(durationMs: Long): Long {
        dragging = false
        return ((preview ?: 0f).toDouble() * durationMs).toLong()
            .coerceIn(0L, durationMs).also { pendingMs = it }
    }
    fun clear() { dragging = false; pendingMs = null; preview = null }
}

@Composable
internal fun rememberMiniPlayerSeekState(
    trackKey: Any?,
    position: State<Long>,
    enabled: Boolean,
): MiniPlayerSeekState {
    val seek = remember(trackKey) { MiniPlayerSeekState() }
    val pending = seek.pendingMs
    val generation = seek.generation
    LaunchedEffect(seek, pending, generation, enabled) {
        if (!enabled) { seek.clear(); return@LaunchedEffect }
        if (pending == null) return@LaunchedEffect
        try {
            // No polling or competing seek calls: wait for the existing position stream.
            withTimeoutOrNull(2_000) {
                snapshotFlow { position.value }.first { abs(it - pending) <= 500L }
            }
        } finally {
            if (seek.generation == generation && !seek.dragging) seek.clear()
        }
    }
    return seek
}

/** Initial-pass interception precedes the card's click and cover's draggable handlers. */
internal fun Modifier.miniPlayerSeekInput(
    seek: MiniPlayerSeekState,
    durationMs: Long,
    cornerRadius: Dp,
    enabled: Boolean,
    controlsBounds: () -> Rect,
    onSeek: (Long) -> Unit,
) = pointerInput(seek, durationMs, cornerRadius, enabled) {
    val r = cornerRadius.toPx()
    val edge = r - (r - 1.dp.toPx()) * .70710678f
    fun fraction(x: Float) = ((x - edge) /
        (size.width - 2 * edge).coerceAtLeast(1f)).coerceIn(0f, 1f)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val inBand = down.position.y >= (size.height - 48.dp.toPx()).coerceAtLeast(0f) &&
            down.position.y <= size.height && down.position.x in edge..(size.width - edge)
        // Reserve the entire measured button area, not just the visible white icons.
        if (!enabled || down.isConsumed || !inBand || controlsBounds().contains(down.position)) {
            return@awaitEachGesture
        }
        down.consume()
        seek.begin(fraction(down.position.x))
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                change.consume()
                seek.move(fraction(change.position.x))
                if (!change.pressed) {
                    onSeek(seek.finish(durationMs))
                    break
                }
            }
        } finally {
            // Cancellation, track/duration changes or a second finger never commit a seek.
            if (seek.dragging) seek.clear()
        }
    }
}

/** Separate accessible range; touch is arbitrated by the ancestor, above sibling gestures. */
@Composable
internal fun MiniPlayerSeekTarget(
    position: State<Long>,
    durationMs: Long,
    seek: MiniPlayerSeekState,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(48.dp).semantics(mergeDescendants = true) {
        contentDescription = "Playback progress"
        progressBarRangeInfo = ProgressBarRangeInfo(seek.displayFraction(position.value, durationMs), 0f..1f)
        if (!enabled) disabled()
        setProgress { fraction ->
            if (enabled && fraction.isFinite()) {
                seek.begin(fraction.coerceIn(0f, 1f))
                onSeek(seek.finish(durationMs))
                true
            } else false
        }
    })
}

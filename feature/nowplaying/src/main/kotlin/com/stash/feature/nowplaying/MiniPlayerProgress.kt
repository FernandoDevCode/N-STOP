package com.stash.feature.nowplaying

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Local drag position wins over playback ticks until release and acknowledgement. */
@Stable
internal class MiniPlayerSeekState {
    private var preview by mutableStateOf<Float?>(null)
    var pendingMs by mutableStateOf<Long?>(null)
        private set
    var generation by mutableIntStateOf(0)
        private set
    var dragging by mutableStateOf(false)
        private set

    fun displayFraction(positionMs: Long, durationMs: Long): Float =
        preview ?: progressFraction(positionMs, durationMs)

    fun begin(fraction: Float) {
        generation++
        pendingMs = null
        dragging = true
        preview = fraction
        velocity = 0f
        lastTime = 0L
        lastFraction = fraction
    }
    // Gesture velocity is visual input only; playback still receives a single committed seek.
    var velocity = 0f
        private set
    private var lastTime = 0L
    private var lastFraction = 0f
    fun move(fraction: Float, timeMs: Long = 0L) {
        if (timeMs > lastTime && lastTime > 0L) {
            val measured = (fraction - lastFraction) * 1000f / (timeMs - lastTime)
            velocity = velocity * .35f + measured * .65f
        }
        lastTime = timeMs
        lastFraction = fraction
        preview = fraction
    }
    fun gestureVelocity(nowMs: Long): Float =
        velocity * kotlin.math.exp(-((nowMs - lastTime - 60L).coerceAtLeast(0L)) / 90f)
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

/** Classifies the first down as control, path seek or body tap and never changes ownership. */
internal fun Modifier.miniPlayerGestureInput(
    seek: MiniPlayerSeekState,
    durationMs: Long,
    cornerRadius: Dp,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit,
    bodyInteractions: MutableInteractionSource,
    controlsBounds: () -> Rect = { Rect.Zero },
    artworkBounds: () -> Rect = { Rect.Zero },
) = pointerInput(seek, durationMs, cornerRadius, enabled, onSeek, onExpand, bodyInteractions) {
    val radius = cornerRadius.toPx()
    val rail = MiniPlayerRail(size.width.toFloat(), size.height.toFloat(), radius)
    val corridor = MiniProgressTouchWidth.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
        val insideCard = down.position.x in 0f..size.width.toFloat() &&
            down.position.y in 0f..size.height.toFloat()
        if (!insideCard || controlsBounds().contains(down.position)) return@awaitEachGesture
        // Keep the approved body's upper boundary when the visible line moves inward.
        val belowBody = down.position.x < rail.left || down.position.y >= size.height - corridor
        val ownsSeek = enabled && belowBody && !artworkBounds().contains(down.position) &&
            rail.distanceTo(down.position) <= corridor

        if (ownsSeek) {
            // Classification is final on the first down. Consuming it in Initial prevents the
            // descendant swipe detector from ever promoting this seek to Expand.
            down.consume()
            seek.begin(rail.fraction(down.position))
            seek.move(rail.fraction(down.position), down.uptimeMillis)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                    change.consume()
                    seek.move(rail.fraction(change.position), change.uptimeMillis)
                    if (!change.pressed) {
                        // Tap and drag both commit on successful up. Preview followed every move;
                        // cancellation and multi-touch never commit.
                        onSeek(seek.finish(durationMs))
                        break
                    }
                }
            } finally {
                if (seek.dragging) seek.clear()
            }
            return@awaitEachGesture
        }

        // Body taps are deliberately recognized here rather than by another clickable detector.
        // A drag consumed by swipe-to-skip, a Home-style vertical move, multi-touch or cancellation
        // can therefore never release as an accidental Expand.
        val press = PressInteraction.Press(down.position)
        bodyInteractions.tryEmit(press)
        var finished = false
        var cleanTap = true
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed } ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                    change.position.x !in 0f..size.width.toFloat() ||
                    change.position.y !in 0f..size.height.toFloat()) cleanTap = false
                if (!change.pressed) {
                    if (cleanTap) {
                        bodyInteractions.tryEmit(PressInteraction.Release(press))
                        onExpand()
                    } else {
                        bodyInteractions.tryEmit(PressInteraction.Cancel(press))
                    }
                    finished = true
                    break
                }
            }
        } finally {
            if (!finished) bodyInteractions.tryEmit(PressInteraction.Cancel(press))
        }
    }
}

/** Accessibility range only; allocates no extra height and has no pointer handler. */
@Composable
internal fun MiniPlayerSeekTarget(
    position: State<Long>,
    durationMs: Long,
    seek: MiniPlayerSeekState,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(MiniProgressHeight)
        .semantics(mergeDescendants = true) {
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

// Semantics bounds cover the corner height; this node is overlaid, never a layout row.
internal val MiniProgressHeight = 13.dp
internal val MiniProgressCorner = 13.dp
/** One-sided inward touch corridor; the visible stroke and card geometry are unchanged. */
internal val MiniProgressTouchWidth = 14.dp

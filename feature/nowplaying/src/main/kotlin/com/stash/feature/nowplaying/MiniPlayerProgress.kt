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

/** Owns only downs in the contour corridor, excluding measured controls. Initial-pass
 * arbitration is explicit so child click/swipe handlers never claim a seek. A down
 * anywhere else is untouched and cannot acquire seek ownership later in its gesture. */
internal fun Modifier.miniPlayerSeekInput(
    seek: MiniPlayerSeekState,
    durationMs: Long,
    cornerRadius: Dp,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    controlsBounds: () -> Rect = { Rect.Zero },
) = pointerInput(seek, durationMs, cornerRadius, enabled) {
    val rail = MiniPlayerRail(size.width.toFloat(), size.height.toFloat(), cornerRadius.toPx())
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
        if (!enabled || down.position.x < 0f || down.position.x > size.width ||
            down.position.y < rail.cy || down.position.y > size.height ||
            controlsBounds().contains(down.position) ||
            (rail.point(rail.fraction(down.position)) - down.position).getDistance() > 7.dp.toPx()) return@awaitEachGesture
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

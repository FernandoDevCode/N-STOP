package com.stash.feature.nowplaying.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Height of the track bar in dp. */
private val TrackHeight = 4.dp

/** Additional touch target padding above/below the bar. */
private val TouchPadding = 12.dp

/** Radius of the outer thumb circle (visible during drag). */
private const val THUMB_OUTER_RADIUS = 8f

/** Radius of the inner thumb circle (accent fill). */
private const val THUMB_INNER_RADIUS = 5f

/** Radius of the glow circle at the playhead. */
private const val GLOW_RADIUS = 14f

/**
 * A custom glowing progress bar with drag-to-seek support.
 *
 * Renders a gradient-filled track with round caps, a semi-transparent glow
 * at the playhead position, and an optional thumb that appears while the
 * user is dragging. Elapsed and remaining time labels sit below the bar.
 *
 * @param progress    Current playback progress as a fraction in `[0f, 1f]` (a lambda, read
 *                    inside this composable so the position tick recomposes only the bar).
 * @param accentColor Primary accent color used for the gradient and glow.
 * @param elapsedMs   Current playback position in milliseconds.
 * @param totalMs     Total track duration in milliseconds.
 * @param onSeek      Callback invoked with the target position in ms when the
 *                    user taps or finishes dragging.
 * @param modifier    Standard Compose [Modifier].
 */
@Composable
fun GlowingProgressBar(
    progress: () -> Float,
    accentColor: Color,
    elapsedMs: () -> Long,
    totalMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** False shows progress only (a Listen Together listener can't seek). */
    seekable: Boolean = true,
    /** Centred between the two times, so it costs no height: a listener's LIVE / Back to live. */
    center: @Composable () -> Unit = {},
) {
    // The position is read HERE, through lambdas, so its 250ms tick recomposes only this bar,
    // not the Now Playing screen that hosts it.
    val progressNow = progress()
    val elapsedNow = elapsedMs()
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progressNow) }
    // A released seek's target, held as the display value until the reported progress catches
    // up (or SEEK_HOLD_MS passes): see [seekArrived].
    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    // The gesture detectors are keyed on Unit and read these live. Keyed on totalMs, they were
    // torn down whenever the duration changed mid-drag (a stream's estimate replaced by the
    // prepared duration, an auto-advance): the drag's onDragEnd/onDragCancel never ran, so the
    // scrub was dropped and isDragging stuck true, freezing the bar until the next full drag.
    val currentTotalMs by rememberUpdatedState(totalMs)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentProgress by rememberUpdatedState(progressNow)
    val seekTo: (Float) -> Unit = { fraction ->
        pendingSeek = fraction.coerceIn(0f, 1f)
        currentOnSeek(seekTargetMs(fraction, currentTotalMs))
    }
    LaunchedEffect(pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        withTimeoutOrNull(SEEK_HOLD_MS) {
            snapshotFlow { currentProgress }.first { seekArrived(it, target) }
        }
        pendingSeek = null
    }

    // Ink follows the resolved theme (white on the dark ambient, plum-black
    // on the light pastel wash) — captured here for use inside the Canvas.
    val ink = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White
    } else {
        Color(0xFF241C36)
    }

    // Smoothly animate progress when not dragging to avoid jitter. A just-released seek holds
    // its target (no tween) until the player reports it: retargeting straight back to the old
    // progress made the bar rubber-band backwards, then forwards, a few frames later.
    val heldProgress = pendingSeek
    val displayProgress by animateFloatAsState(
        targetValue = when {
            isDragging -> dragProgress
            heldProgress != null -> heldProgress
            else -> progressNow
        },
        animationSpec = tween(durationMillis = if (isDragging || heldProgress != null) 0 else 150),
        label = "progressAnim",
    )
    val displayMs = when {
        isDragging -> seekTargetMs(dragProgress, totalMs)
        heldProgress != null -> seekTargetMs(heldProgress, totalMs)
        else -> elapsedNow
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrackHeight + TouchPadding * 2)
                // A bare Canvas was invisible to TalkBack: name it, read the position, and let
                // accessibility services seek (setProgress, plus ±10 s actions) when seekable.
                .semantics {
                    contentDescription = "Playback position"
                    stateDescription = "${formatTime(displayMs)} of ${formatTime(totalMs)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(displayProgress.coerceIn(0f, 1f), 0f..1f)
                    if (seekable && totalMs > 0L) {
                        setProgress { target -> seekTo(target); true }
                        customActions = listOf(
                            CustomAccessibilityAction("Back 10 seconds") {
                                seekTo(seekStepMs(displayMs, totalMs, -SEEK_STEP_MS).toFloat() / totalMs); true
                            },
                            CustomAccessibilityAction("Forward 10 seconds") {
                                seekTo(seekStepMs(displayMs, totalMs, SEEK_STEP_MS).toFloat() / totalMs); true
                            },
                        )
                    }
                }
                .then(if (!seekable) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        seekTo(offset.x / size.width)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            seekTo(dragProgress)
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragProgress = (dragProgress + dragAmount / size.width)
                                .coerceIn(0f, 1f)
                        },
                    )
                })
        ) {
            val barY = size.height / 2f
            val barHeight = TrackHeight.toPx()
            val barWidth = size.width

            // --- Background track ---
            drawRoundRect(
                color = ink.copy(alpha = 0.15f),
                topLeft = Offset(0f, barY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )

            // --- Filled (elapsed) track with gradient ---
            val filledWidth = barWidth * displayProgress
            if (filledWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.6f),
                            accentColor,
                        ),
                        startX = 0f,
                        endX = filledWidth,
                    ),
                    topLeft = Offset(0f, barY - barHeight / 2f),
                    size = Size(filledWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f),
                )
            }

            // --- Glow at playhead ---
            val playheadX = filledWidth.coerceIn(0f, barWidth)
            drawCircle(
                color = accentColor.copy(alpha = 0.30f),
                radius = GLOW_RADIUS,
                center = Offset(playheadX, barY),
            )

            // --- Thumb (only visible while dragging) ---
            if (isDragging) {
                // Outer ink ring (white on dark, plum-black on light).
                drawCircle(
                    color = ink,
                    radius = THUMB_OUTER_RADIUS,
                    center = Offset(playheadX, barY),
                )
                // Inner accent fill.
                drawCircle(
                    color = accentColor,
                    radius = THUMB_INNER_RADIUS,
                    center = Offset(playheadX, barY),
                )
            }
        }

        // --- Time labels ---
        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatTime(displayMs),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterStart),
            )
            center()
            Text(
                text = "-${formatTime((totalMs - displayMs).coerceAtLeast(0L))}",
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * Formats milliseconds into `m:ss` or `h:mm:ss` display string.
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

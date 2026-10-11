package com.stash.feature.nowplaying.ui

import kotlin.math.abs
import kotlin.math.roundToLong

/** Step of the seek bar's accessibility "Back / Forward 10 seconds" actions. */
internal const val SEEK_STEP_MS = 10_000L

/**
 * How long the seek bar keeps showing a just-released seek target while the player's position
 * catches up through the repository → combine → StateFlow pipeline (1-3 frames normally, longer
 * on a stream that must rebuffer).
 */
internal const val SEEK_HOLD_MS = 600L

/** How close (as a fraction of the track) the reported progress must come to count as arrived. */
internal const val SEEK_ARRIVED_FRACTION = 0.01f

/** The position for a seek to [fraction] of a [totalMs] track, clamped to the track. */
internal fun seekTargetMs(fraction: Float, totalMs: Long): Long =
    (fraction.coerceIn(0f, 1f) * totalMs.coerceAtLeast(0L)).roundToLong()

/**
 * Whether the reported [progress] has caught up with a seek to [target], so the bar can let go
 * of the target. Until then the bar keeps drawing [target]: retargeting straight to the old
 * progress on release made it rubber-band back, then forward, a frame or three later.
 */
internal fun seekArrived(progress: Float, target: Float): Boolean =
    abs(progress - target) < SEEK_ARRIVED_FRACTION

/** The position [deltaMs] away from [elapsedMs], kept inside a [totalMs] track. */
internal fun seekStepMs(elapsedMs: Long, totalMs: Long, deltaMs: Long): Long =
    (elapsedMs + deltaMs).coerceIn(0L, totalMs.coerceAtLeast(0L))

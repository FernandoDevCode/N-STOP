package com.stash.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One arc-length metric for painting, the needle, particles and touch projection. */
internal class MiniPlayerRail(width: Float, height: Float, radius: Float) {
    private val corner = radius.coerceAtMost(width / 2f).coerceAtLeast(.1f)
    val inset = corner * (2.5f / 13f)
    val strokeWidth = inset
    val r = corner - inset
    val left = corner
    val cy = height - corner
    val bottom = cy + r
    private val innerRadius = corner - strokeWidth / 2f
    val right = (width - corner + sqrt((innerRadius * innerRadius - r * r).coerceAtLeast(0f)))
        .coerceAtLeast(left)
    val arc = (PI * r / 2).toFloat()
    val straight = right - left
    val length = arc + straight

    /** Partial paths use the same distance as point/fraction, without a second length metric. */
    fun writePath(path: Path, from: Float = 0f, until: Float = 1f) {
        path.reset()
        val start = from.coerceIn(0f, 1f) * length
        val end = until.coerceIn(from.coerceIn(0f, 1f), 1f) * length
        val first = point(from)
        path.moveTo(first.x, first.y)
        if (start < arc && end > start) {
            val sweep = (minOf(end, arc) - start) / r * 180f / PI.toFloat()
            path.arcTo(Rect(left - r, cy - r, left + r, cy + r),
                180f - start / r * 180f / PI.toFloat(), -sweep, false)
        }
        if (end > arc) path.lineTo(left + end - arc, bottom)
    }

    fun tangent(fraction: Float): Offset {
        val distance = fraction.coerceIn(0f, 1f) * length
        if (distance >= arc) return Offset(1f, 0f)
        val angle = PI.toFloat() - distance / r
        return Offset(sin(angle), -cos(angle))
    }

    fun point(fraction: Float): Offset {
        val distance = fraction.coerceIn(0f, 1f) * length
        if (distance >= arc) return Offset(left + distance - arc, bottom)
        val angle = PI.toFloat() - distance / r
        return Offset(left + r * cos(angle), cy + r * sin(angle))
    }

    /** Nearest bounded candidate on the arc or horizontal line. */
    fun fraction(point: Offset): Float {
        val angle = atan2((point.y - cy).coerceAtLeast(0f), point.x - left)
            .coerceIn((PI / 2).toFloat(), PI.toFloat())
        val arcFraction = ((PI.toFloat() - angle) * r / length).coerceIn(0f, arc / length)
        val lineFraction = (arc + point.x.coerceIn(left, right) - left) / length
        return if ((this.point(arcFraction) - point).getDistanceSquared() <=
            (this.point(lineFraction) - point).getDistanceSquared()) arcFraction else lineFraction
    }

    fun distanceTo(point: Offset): Float = (this.point(fraction(point)) - point).getDistance()
}

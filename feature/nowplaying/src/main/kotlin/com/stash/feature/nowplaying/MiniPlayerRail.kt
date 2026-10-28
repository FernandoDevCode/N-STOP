package com.stash.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One metric for painting, hit-testing and seeking: two quarter circles and their tangent. */
internal class MiniPlayerRail(
    width: Float,
    height: Float,
    radius: Float,
    measuredLength: Float? = null,
) {
    val r = radius.coerceAtMost(width / 2f).coerceAtLeast(.1f)
    val left = r
    val right = (width - r).coerceAtLeast(left)
    val cy = height - r
    val arc = (PI * r / 2).toFloat()
    val straight = right - left
    /** May be supplied by Compose PathMeasure so rendering and seek share its effective length. */
    val length = measuredLength?.takeIf { it.isFinite() && it > 0f } ?: (2 * arc + straight)

    /** Same circular corners as the card's RoundedCornerShape; no horizontal inset. */
    fun writePath(path: Path) {
        path.reset()
        path.arcTo(Rect(left - r, cy - r, left + r, cy + r), 180f, -90f, true)
        path.lineTo(right, cy + r)
        path.arcTo(Rect(right - r, cy - r, right + r, cy + r), 90f, -90f, false)
    }

    fun tangent(fraction: Float): Offset {
        val distance = fraction.coerceIn(0f, 1f) * length
        val angle = when {
            distance < arc -> PI.toFloat() - distance / r
            distance <= arc + straight -> (PI / 2).toFloat()
            else -> (PI / 2).toFloat() - (distance - arc - straight) / r
        }
        return Offset(sin(angle), -cos(angle))
    }

    fun point(fraction: Float): Offset {
        if (fraction <= 0f) return Offset(left - r, cy)
        if (fraction >= 1f) return Offset(right + r, cy)
        val distance = fraction.coerceIn(0f, 1f) * length
        return when {
            distance < arc -> {
                val angle = PI.toFloat() - distance / r
                Offset(left + r * cos(angle), cy + r * sin(angle))
            }
            distance <= arc + straight -> Offset(left + distance - arc, cy + r)
            else -> {
                val angle = (PI / 2).toFloat() - (distance - arc - straight) / r
                Offset(right + r * cos(angle), cy + r * sin(angle))
            }
        }
    }

    /** Nearest point on the contour, including the actual y coordinate in each curve. */
    fun fraction(point: Offset): Float {
        if (point.y <= cy && point.x <= left) return 0f
        if (point.y <= cy && point.x >= right) return 1f
        val distance = when {
            point.x < left -> {
                val angle = atan2((point.y - cy).coerceAtLeast(0f), point.x - left)
                    .coerceIn((PI / 2).toFloat(), PI.toFloat())
                (PI.toFloat() - angle) * r
            }
            point.x > right -> {
                val angle = atan2((point.y - cy).coerceAtLeast(0f), point.x - right)
                    .coerceIn(0f, (PI / 2).toFloat())
                arc + straight + ((PI / 2).toFloat() - angle) * r
            }
            else -> arc + point.x - left
        }
        return (distance / length).coerceIn(0f, 1f)
    }

    /** Distance to the actual curved rail, not to a rectangular strip around the card. */
    fun distanceTo(point: Offset): Float = (this.point(fraction(point)) - point).getDistance()
}

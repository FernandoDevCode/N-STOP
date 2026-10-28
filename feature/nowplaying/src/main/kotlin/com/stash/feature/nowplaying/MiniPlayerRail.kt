package com.stash.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One metric for painting and seeking: two quarter circles and their common tangent. */
internal class MiniPlayerRail(width: Float, height: Float, radius: Float, inset: Float) {
    val r = (radius - inset).coerceAtLeast(.1f)
    val left = radius
    val right = (width - radius).coerceAtLeast(left)
    val cy = height - radius
    val arc = (PI * r / 2).toFloat()
    val straight = right - left
    val length = 2 * arc + straight

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
}

package com.stash.feature.search

/**
 * Foreground photo: preserve the complete source bitmap and its natural
 * aspect ratio. Tall sources are fitted into a bounded area, not cropped.
 */
internal fun artistPhotoFrameHeightDp(
    widthDp: Float,
    viewportHeightDp: Float,
    sourceAspectRatio: Float,
): Float {
    val aspect = sourceAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1.5f
    val width = widthDp.coerceAtLeast(1f)
    val maxHeight = maxOf(width * .58f, minOf(width * 1.25f, viewportHeightDp * .70f))
    return (width / aspect).coerceIn(1f, maxHeight)
}

/** A continuous circular distance field creates the U contour without strips. */
internal fun artistPhotoFadeRadius(widthPx: Float, photoHeightPx: Float): Float =
    kotlin.math.hypot(widthPx.coerceAtLeast(1f) * .5f, photoHeightPx.coerceAtLeast(1f) * .88f)
        .coerceAtLeast(1f)

internal fun artistPhotoFadeDistanceFraction(
    xFraction: Float, yFraction: Float, widthPx: Float, photoHeightPx: Float,
): Float {
    val dx = (xFraction.coerceIn(0f, 1f) - .5f) * widthPx
    val dy = (yFraction.coerceIn(0f, 1f) + .08f) * photoHeightPx
    return kotlin.math.hypot(dx, dy) / artistPhotoFadeRadius(widthPx, photoHeightPx)
}

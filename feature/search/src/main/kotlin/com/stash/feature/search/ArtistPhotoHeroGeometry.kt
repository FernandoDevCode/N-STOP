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

/** Earliest at left/right, latest at the middle: smooth symmetric U. */
internal fun artistPhotoFadeStartFraction(xFraction: Float): Float {
    val x = xFraction.coerceIn(0f, 1f)
    val centered = 2f * x - 1f
    return .62f + .23f * (1f - centered * centered)
}

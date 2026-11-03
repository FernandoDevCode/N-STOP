package com.stash.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * V4: one photo-derived, heavily blurred and fixed backdrop per artist.
 *
 * The original hero keeps its native Fit crop and U fade; the backdrop is a
 * compact independent center crop of the SAME photo, prepared once off-thread.
 * If loading fails or the photo is undersized, the V3 chromatic canvas remains.
 */
@Composable
internal fun ArtistProfileBackdrop(
    modifier: Modifier = Modifier,
    mid: Color,
    deep: Color,
    bridge: Color,
    photoBackdrop: ImageBitmap?,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBehind {
            // Always render V3 as a fallback, with no flash from app graphite.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to mid,
                    .48f to lerp(mid, deep, .48f),
                    1f to deep,
                ),
            )
            if (photoBackdrop != null) {
                // Fixed to the viewport, not LazyColumn scroll offset. The preblurred
                // image has no recognizable objects or horizontal seams after scaling.
                drawImage(
                    image = photoBackdrop,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(
                        size.width.toInt().coerceAtLeast(1),
                        size.height.toInt().coerceAtLeast(1),
                    ),
                    alpha = .82f,
                )
                // The real photograph supplies all spatial variation. A subtle
                // dark scrim provides contrast without adding fake light or texture.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to deep.copy(alpha = .28f),
                        .48f to deep.copy(alpha = .45f),
                        1f to deep.copy(alpha = .57f),
                    ),
                )
            } else {
                // Only the photo-unavailable fallback retains V3 diffused light.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(bridge.copy(alpha = .42f), Color.Transparent),
                        center = Offset(size.width * .14f, size.height * .04f),
                        radius = size.width * 1.9f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(bridge.copy(alpha = .21f), Color.Transparent),
                        center = Offset(size.width * .87f, size.height * .82f),
                        radius = size.width * 1.65f,
                    ),
                )
            }
        },
        content = content,
    )
}

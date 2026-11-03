package com.stash.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/**
 * Single artist-profile canvas, independent of navigation and content.
 * This is a structural extraction only: rendering is kept byte-for-byte
 * equivalent to the previous screen so the V3 canvas can replace it safely.
 */
@Composable
internal fun ArtistProfileBackdrop(
    modifier: Modifier = Modifier,
    smoothAtmosphere: Color,
    smoothBridge: Color,
    continuation: ImageBitmap?,
    absoluteScrollPx: Float?,
    photoFrameHeightPx: Float,
    showHeroContinuation: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().drawBehind {
                // V2: spatial pools of photo-derived light, not a flat tint.
                drawRect(ArtistNeutralAtmosphere)
                val glow = lerp(smoothAtmosphere, smoothBridge, .86f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = .91f), Color.Transparent),
                        center = Offset(size.width * .12f, size.height * .03f),
                        radius = size.width * 1.50f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = .60f), Color.Transparent),
                        center = Offset(size.width * .94f, size.height * .78f),
                        radius = size.width * 1.17f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to smoothAtmosphere.copy(alpha = .24f),
                    ),
                )
                val frameEnd = if (showHeroContinuation) {
                    absoluteScrollPx?.let { photoFrameHeightPx - it }
                } else null
                if (continuation != null && frameEnd != null && photoFrameHeightPx > 0f) {
                    val bleed = photoFrameHeightPx * .36f
                    val startY = frameEnd - bleed
                    val heightPx = 740.dp.toPx()
                    if (startY < size.height && startY + heightPx > 0f) {
                        // Preserve photo alignment: lateral drift on a
                        // source-derived backdrop produced visible seams and
                        // giant moving columns in the actual Redmi capture.
                        drawImage(
                            image = continuation!!,
                            dstOffset = IntOffset(0, startY.toInt()),
                            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), heightPx.toInt().coerceAtLeast(1)),
                            alpha = 1f,
                        )
                        // Reading scrim joins the photo-derived material
                        // rather than covering it with a second opaque panel.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                .58f to Color(0x3320252B),
                                1f to ArtistNeutralAtmosphere,
                                startY = frameEnd + 60.dp.toPx(),
                                endY = startY + heightPx,
                            ),
                        )
                    }
                }

        },
        content = content,
    )
}

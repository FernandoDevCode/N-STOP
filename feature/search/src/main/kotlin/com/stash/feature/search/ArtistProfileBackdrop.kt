package com.stash.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Continuous photo-derived chromatic atmosphere shared by all artist tabs.
 * No synthesized, repeated or stretched hero bitmap: the original photograph
 * remains owned by ArtistHero and fades naturally onto this procedural canvas.
 */
@Composable
internal fun ArtistProfileBackdrop(
    modifier: Modifier = Modifier,
    mid: Color,
    deep: Color,
    bridge: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to mid,
                    .48f to lerp(mid, deep, .48f),
                    1f to deep,
                ),
            )
            // Diffuse low-contrast illumination; no image-shaped texture.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        bridge.copy(alpha = .42f),
                        mid.copy(alpha = .12f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * .14f, size.height * .04f),
                    radius = size.width * 1.9f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        bridge.copy(alpha = .21f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * .87f, size.height * .82f),
                    radius = size.width * 1.65f,
                ),
            )
        },
        content = content,
    )
}

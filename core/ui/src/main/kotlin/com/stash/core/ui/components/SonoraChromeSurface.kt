package com.stash.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.LocalVisualDark
import com.stash.core.ui.visual.LocalVisualAmoled
import com.stash.core.ui.visual.VisualDesign
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Backdrop-only Haze effect. Children and the outer friction drawing stay sharp.
 * No screenshot capture, timer, noise animation or new source layer is created here.
 * Haze uses its fallback tint below API 31; previews without a source do the same.
 */
@Composable
fun Modifier.sonoraMiniGlass(shape: Shape): Modifier {
    val source = LocalChromeHazeState.current
    val dark = LocalVisualDark.current
    val ground = VisualDesign.Background
    val tint = if (dark) Color(0xFF20202C) else VisualDesign.Surface
    val fallback = tint.copy(alpha = .97f)
    val glass = if (source == null) Modifier.background(fallback) else Modifier.hazeEffect(
        state = source,
        style = HazeStyle(
            backgroundColor = ground,
            tint = HazeTint(tint.copy(alpha = .60f)),
            blurRadius = 20.dp,
            noiseFactor = 0f,
            fallbackTint = HazeTint(fallback),
        ),
    )
    val reflection = if (dark) Color(0xFFF6F5FA) else VisualDesign.Ink
    val edge = VisualDesign.Border.copy(alpha = .65f)
    return clip(shape).then(glass).drawWithCache {
        val sheen = Brush.linearGradient(
            listOf(reflection.copy(alpha = .055f), Color(0x03B8AED5), Color.Transparent),
            start = Offset.Zero, end = Offset(size.width * .8f, size.height),
        )
        onDrawBehind { drawRect(sheen) }
    }.border(.5.dp, Brush.linearGradient(
        listOf(reflection.copy(alpha = .34f), Color(0x24B8AED5), edge.copy(alpha = .16f)),
    ), shape)
}

/** Draw-only fade: no layout, hit target or clipping. Its base includes system insets.
 * Applied to the chrome host so the shoulder is not clipped by bar animations.
 */
@Composable
fun Modifier.sonoraBottomFade(navigationHeight: Dp): Modifier {
    val top = when {
        !LocalVisualDark.current -> VisualDesign.Surface
        LocalVisualAmoled.current -> Color.Black
        else -> Color(0xFF15161D)
    }
    val base = when {
        !LocalVisualDark.current -> VisualDesign.Background
        LocalVisualAmoled.current -> Color.Black
        else -> Color(0xFF0E0F14)
    }
    return drawWithCache {
        val navigationTop = size.height - navigationHeight.toPx()
        // The previous ramp only became opaque 22dp inside the navigation bar,
        // allowing bright artwork and track titles to reach the tab icons. Give
        // the transition more room and complete it at the bar's upper edge: the
        // controls remain crisp while the content disappears before them.
        val start = navigationTop - 64.dp.toPx()
        val end = navigationTop + 2.dp.toPx()
        val shoulder = Brush.verticalGradient(
            0f to top.copy(alpha = 0f),
            .24f to top.copy(alpha = .08f),
            .5f to top.copy(alpha = .34f),
            .72f to top.copy(alpha = .68f),
            .9f to top.copy(alpha = .93f),
            1f to top,
            startY = start, endY = end,
        )
        val floor = Brush.verticalGradient(listOf(top, base), startY = end, endY = size.height)
        onDrawBehind {
            drawRect(shoulder, topLeft = Offset(0f, start), size = Size(size.width, end - start))
            drawRect(floor, topLeft = Offset(0f, end), size = Size(size.width, size.height - end))
        }
    }
}

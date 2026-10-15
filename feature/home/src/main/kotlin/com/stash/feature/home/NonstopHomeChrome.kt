package com.stash.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import com.stash.core.ui.theme.LocalIsDarkTheme
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.stash.core.ui.theme.SpaceGrotesk

private val brandPurple = Color(0xFFAF6AFF)
private val brandBlue = Color(0xFF4FADFF)
private val brandMint = Color(0xFF57F0B6)
private val brandWhite = Color(0xFFF6F5FA)

/**
 * Lightweight Compose rendering of the approved NØNStop wordmark. The special
 * Ø is accented and carries a restrained equalizer; the two Ns stay neutral.
 * Avoids scaling a full-size neon raster down to toolbar dimensions.
 */
@Composable
fun NonstopWordmark(modifier: Modifier = Modifier) {
    val font = SpaceGrotesk
    val dark = LocalIsDarkTheme.current
    val neutral = if (dark) brandWhite else Color(0xFF211833)
    val violet = if (dark) brandPurple else Color(0xFF7A42B2)
    val blue = if (dark) brandBlue else Color(0xFF2469B1)
    val mint = if (dark) brandMint else Color(0xFF127E76)
    val common = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        letterSpacing = (-1.4).sp,
    )
    Row(
        modifier = modifier.semantics { contentDescription = "NØNStop" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text("N", style = common, color = neutral)
        Box {
            // Equalizer is centred over Ø, not an extension of its diagonal.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(31.dp).height(12.dp)) {
                    val bars = listOf(0.32f, 0.56f, 0.82f, 1f, 0.82f, 0.56f, 0.32f)
                    val width = size.width
                    val height = size.height
                    bars.forEachIndexed { index, level ->
                        val x = width * (index + 0.5f) / bars.size
                        val hue = if (index < 3) brandPurple else if (index < 5) brandBlue else brandMint
                        drawLine(
                            color = hue,
                            start = Offset(x, height),
                            end = Offset(x, height * (1 - level)),
                            strokeWidth = 2.3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Text(
                    "Ø",
                    style = common.copy(
                        brush = Brush.linearGradient(listOf(violet, blue, mint)),
                        shadow = if (dark) Shadow(brandBlue.copy(alpha = 0.45f), Offset.Zero, 10f) else null,
                    ),
                )
            }
        }
        Text("N", style = common, color = brandWhite)
        Text(
            "Stop",
            style = common.copy(
                brush = Brush.horizontalGradient(listOf(violet, blue, mint)),
                shadow = if (dark) Shadow(brandPurple.copy(alpha = 0.24f), Offset.Zero, 8f) else null,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/**
 * 0 Música, 1 Podcasts, 2 Audiobooks.
 * Tapped selected tab briefly blooms, then settles to an easy-to-read
 * lilac frosted resting state (not a permanently bright CTA).
 */
@Composable
fun NonstopMediaFilters(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastTapped by remember { mutableIntStateOf(-1) }
    var pulseId by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(0f) }
    val dark = LocalIsDarkTheme.current
    LaunchedEffect(pulseId) {
        if (pulseId > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(130))
            pulse.animateTo(0f, tween(470))
        }
    }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        listOf(
            Triple(R.string.nonstop_filter_music, Icons.Outlined.MusicNote, 0),
            Triple(R.string.nonstop_filter_podcasts, Icons.Filled.Headphones, 1),
            Triple(R.string.nonstop_filter_audiobooks, Icons.Outlined.MenuBook, 2),
        ).forEach { (label, icon, value) ->
            val active = selected == value
            val prominence by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = tween(260),
                label = "media filter selection",
            )
            val burst = if (lastTapped == value) pulse.value else 0f
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .drawBehind {
                        if (active) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        brandPurple.copy(alpha = 0.10f + 0.18f * burst),
                                        brandBlue.copy(alpha = 0.04f + 0.10f * burst),
                                    ),
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                            )
                        }
                    }
.background(
                        if (!dark && active) {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF703C9C).copy(alpha = 0.94f),
                                    Color(0xFF444F8A).copy(alpha = 0.92f),
                                ),
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFAA79CF).copy(alpha = 0.09f + 0.25f * prominence + 0.16f * burst),
                                    Color(0xFF6A6996).copy(alpha = 0.09f + 0.14f * prominence),
                                ),
                            )
                        },
                        shape,
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.12f + 0.21f * prominence + 0.19f * burst),
                        shape,
                    )
                    .clickable {
                        lastTapped = value
                        pulseId += 1
                        onSelect(value)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(label),
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun NonstopOtherMediaNotice(category: Int) {
    val title = if (category == 1) stringResource(R.string.nonstop_filter_podcasts)
        else stringResource(R.string.nonstop_filter_audiobooks)
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 38.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.nonstop_media_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

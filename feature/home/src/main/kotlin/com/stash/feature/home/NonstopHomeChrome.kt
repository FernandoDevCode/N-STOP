package com.stash.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
import com.stash.core.ui.ambient.rememberReducedMotion
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
    com.stash.core.ui.theme.NonstopBrand(modifier)
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
    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(pulseId, reducedMotion) {
        if (pulseId > 0) {
            pulse.snapTo(0f)
            if (!reducedMotion) {
                pulse.animateTo(1f, tween(130))
                pulse.animateTo(0f, tween(470))
            }
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
                animationSpec = tween(if (reducedMotion) 0 else 260),
                label = "media filter selection",
            )
            val burst = if (lastTapped == value) pulse.value else 0f
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
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
                                    Color(0xFF721DFF).copy(alpha = 0.09f + 0.72f * prominence),
                                    Color(0xFF101115).copy(alpha = 0.8f),
                                ),
                            )
                        },
                        shape,
                    )
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
                    .border(
                        1.dp,
                        if (active) brandPurple else Color.White.copy(alpha = 0.04f),
                        shape,
                    )
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = {
                            lastTapped = value
                            pulseId += 1
                            onSelect(value)
                        },
                    )
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

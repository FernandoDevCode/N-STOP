package com.stash.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * Shared NØNStop cover-first layout for downloaded albums and playlists.
 * The media, not a stock-colored card, is the focal point. The title and
 * metadata live in the scrollable feed rather than inside a cramped hero.
 *
 * Callers own [metadata] and [actions] so the original, real playlist/album
 * operations remain wired to their existing ViewModels.
 */
@Composable
internal fun NonstopCollectionHero(
    title: String,
    subtitle: String?,
    artwork: Any?,
    fallbackIcon: ImageVector,
    onBack: () -> Unit,
    metadata: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    val backdrop = MaterialTheme.colorScheme.background
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF34244D),
                        0.48f to Color(0xFF20213A),
                        1f to backdrop,
                    ),
                ),
        ) {
            // The artwork is deliberately separate from the page background:
            // it stays recognizable with white, black, or high-saturation covers.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 35.dp)
                    .size(235.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = Color(0xFF9F6BFF).copy(alpha = 0.34f),
                        spotColor = Color(0xFF4DD8DE).copy(alpha = 0.20f),
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF48346D), Color(0xFF173F4D)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.painter.ColorPainter(Color(0xFF332B4C)),
                    )
                } else {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(76.dp),
                    )
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp)
                    .background(Color(0xA80A0C18), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
        ) {
            Text(
                text = title,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.8).sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            metadata()
            Spacer(Modifier.height(20.dp))
            actions()
            Spacer(Modifier.height(18.dp))
        }
    }
}

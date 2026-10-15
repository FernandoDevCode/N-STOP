package com.stash.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.data.mix.MixBuildState
import com.stash.core.model.MusicSource
import com.stash.core.ui.components.SourceIndicator
import com.stash.core.ui.components.motion.pressScale
import com.stash.core.ui.theme.StashTheme

/**
 * NØNStop editorial rail card. Cover art leads, the title sits directly on
 * the shadowed lower edge, and the whole surface opens the actual playlist.
 * There is intentionally no fake play button: opening and starting playback
 * are different actions in the existing repository.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MixRailCard(
    title: String,
    artUrl: String?,
    source: MusicSource,
    buildState: MixBuildState = MixBuildState.READY,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .width(158.dp)
            .height(188.dp)
            .pressScale(interactionSource)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF46356B), Color(0xFF103F4B))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        if (artUrl != null) {
            AsyncImage(
                model = ArtUrlUpgrader.upgrade(artUrl),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The bottom gradient protects text over bright artwork.
        Box(
            Modifier
                .fillMaxWidth()
                .height(97.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xDB080A16)),
                    ),
                ),
        )
        SourceIndicator(
            source = source,
            size = 8.dp,
            modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val label = when (buildState) {
                MixBuildState.BUILDING -> "Preparing"
                MixBuildState.EMPTY -> "No tracks yet"
                MixBuildState.READY -> null
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.77f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MixRailCardPreview() {
    StashTheme {
        MixRailCard(title = "Redescoberta", artUrl = null, source = MusicSource.SPOTIFY, onClick = {})
    }
}

package com.stash.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stash.core.ui.ambient.LocalAlbumChrome

/** Real navigation tiles, always on screen even when personalized data are empty. */
@Composable
internal fun NonstopEditorialShortcuts(
    onExplore: () -> Unit,
    onRadio: () -> Unit,
    onSearch: () -> Unit,
) {
    val titles = listOf(R.string.nonstop_home_shortcut_explore, R.string.nonstop_home_shortcut_radio, R.string.nonstop_home_shortcut_search)
    val subtitles = listOf(R.string.nonstop_home_shortcut_explore_subtitle, R.string.nonstop_home_shortcut_radio_subtitle, R.string.nonstop_home_shortcut_search_subtitle)
    val icons = listOf(Icons.Outlined.Explore, Icons.Outlined.Radio, Icons.Outlined.Search)
    val actions = listOf(onExplore, onRadio, onSearch)
    val accent = LocalAlbumChrome.current.selectedTab
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(3) { index ->
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier.width(150.dp).height(140.dp).clip(shape)
                    .background(Brush.linearGradient(listOf(Color(0xFF303A43), Color(0xFF121519))))
                    .clickable(onClick = actions[index]),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width * 0.86f, size.height * 0.28f)
                    for (i in 0..2) drawCircle(
                        color = accent.copy(alpha = 0.14f - 0.035f * i),
                        center = center,
                        radius = size.minDimension * (0.3f + i * 0.16f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(icons[index], contentDescription = null, tint = Color(0xFFE2E7EA), modifier = Modifier.size(23.dp))
                    Text(stringResource(titles[index]), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(subtitles[index]), color = Color(0xFFB4BEC5), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun NonstopEmptyDiscoveryHero(onExplore: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAlbumChrome.current.selectedTab
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier.fillMaxWidth().height(250.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF222C32), Color(0xFF101418))))
            .clickable(onClick = onExplore),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.90f, size.height * 0.27f)
            for (i in 0..5) drawCircle(
                color = accent.copy(alpha = 0.24f - 0.032f * i),
                center = center,
                radius = size.minDimension * (0.23f + i * 0.14f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color(0xFF121619), Color(0xD5121619), Color.Transparent),
        )))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(stringResource(R.string.nonstop_home_empty_eyebrow),
                color = Color(0xFFBDC8CE), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.nonstop_home_empty_title),
                color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(stringResource(R.string.nonstop_home_empty_subtitle),
                color = Color(0xFFDBE0E3), style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                    .clickable(onClick = onExplore).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.nonstop_home_empty_action), color = Color(0xFF131719),
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF131719), modifier = Modifier.size(18.dp))
            }
        }
    }
}

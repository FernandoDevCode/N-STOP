package com.stash.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.VisualDesign

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
 * Centers the group when it fits, otherwise scrolls at its natural text size.
 * A static cool-white halo respects reduced motion without a looping animation.
 */
@Composable
fun NonstopMediaFilters(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(R.string.nonstop_filter_music),
        stringResource(R.string.nonstop_filter_podcasts),
        stringResource(R.string.nonstop_filter_audiobooks),
    )
    val icons = listOf(Icons.Outlined.MusicNote, Icons.Outlined.Mic, Icons.Outlined.MenuBook)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth
        Row(
            Modifier.horizontalScroll(rememberScrollState()).widthIn(min = available)
                .selectableGroup().padding(horizontal = VisualDesign.Gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                val shape = RoundedCornerShape(VisualDesign.Radius.Filter)
                val border = if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = .88f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = .45f)
                Row(
                    Modifier
                        .drawBehind {
                            if (active) {
                                // Small, static white-cool halo, including the pre-31 fallback.
                                listOf(12f to .025f, 8f to .045f, 4f to .10f).forEach { (width, alpha) ->
                                    drawRoundRect(border.copy(alpha = alpha),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                                        style = Stroke(width.dp.toPx()))
                                }
                            }
                        }
                        .background(Brush.verticalGradient(if (active)
                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                            else listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)), shape)
                        .border(if (active) 1.dp else .5.dp, border, shape)
                        .selectable(active, role = Role.Tab, onClick = { onSelect(index) })
                        .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(icons[index], null, Modifier.size(20.dp),
                        tint = if (active) VisualDesign.Ink else VisualDesign.Muted)
                    Text(label, style = VisualDesign.Body,
                        color = if (active) VisualDesign.Ink else VisualDesign.Muted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, softWrap = false)
                }
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

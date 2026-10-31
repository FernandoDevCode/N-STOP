package com.stash.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
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
 * 0 Música, 1 Podcasts, 2 Audiolivros.
 * Regular text uses three equal optical columns. Enlarged text reflows instead
 * of shrinking or clipping, and the internal tab identifiers stay unchanged.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(horizontal = VisualDesign.Gutter, vertical = 0.dp),
    ) {
        val fontScale = LocalDensity.current.fontScale
        val reflow = fontScale >= 1.3f
        val showIcons = maxWidth >= 310.dp && fontScale <= 1.2f
        if (reflow) {
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    SonoraMediaTab(index, label, selected == index, false, onSelect)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    SonoraMediaTab(
                        index = index,
                        label = label,
                        active = selected == index,
                        showIcon = showIcons,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SonoraMediaTab(
    index: Int,
    label: String,
    active: Boolean,
    showIcon: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .drawBehind {
                if (active) {
                    val y = size.height - 3.dp.toPx()
                    val start = Offset(8.dp.toPx(), y)
                    val end = Offset((size.width - 8.dp.toPx()).coerceAtLeast(start.x), y)
                    // The wide lavender reflection and cool-white core retain
                    // Sonora's selected energy without a generic button shell.
                    drawLine(VisualDesign.Light.Wide.copy(alpha = .22f), start, end,
                        strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(VisualDesign.Light.Near.copy(alpha = .34f), start, end,
                        strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(VisualDesign.Light.Core.copy(alpha = .92f), start, end,
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            .selectable(active, role = Role.Tab, onClick = { onSelect(index) })
            .heightIn(min = 48.dp)
            .padding(horizontal = if (showIcon) 4.dp else 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            SonoraMediaGlyph(index, if (active) VisualDesign.Ink else VisualDesign.Muted)
        }
        Text(
            text = label,
            style = VisualDesign.Body,
            color = if (active) VisualDesign.Ink else VisualDesign.Muted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Three bespoke optical-line glyphs with one coherent stroke weight. */
@Composable
private fun SonoraMediaGlyph(index: Int, tint: Color) {
    Canvas(Modifier.size(17.dp)) {
        val u = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * u, y * u)
        val stroke = Stroke(width = 1.65f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (index) {
            0 -> {
                val path = Path().apply {
                    moveTo(9f * u, 16f * u)
                    lineTo(9f * u, 6f * u)
                    lineTo(19f * u, 4f * u)
                    lineTo(19f * u, 14f * u)
                    moveTo(9f * u, 8f * u)
                    lineTo(19f * u, 6f * u)
                }
                drawPath(path, tint, style = stroke)
                drawOval(tint, topLeft = point(4.2f, 15.4f), size = Size(5.2f * u, 3.9f * u))
                drawOval(tint, topLeft = point(14.2f, 13.4f), size = Size(5.2f * u, 3.9f * u))
            }
            1 -> {
                drawRoundRect(tint, topLeft = point(9f, 3.5f), size = Size(6f * u, 11f * u),
                    cornerRadius = CornerRadius(3f * u), style = stroke)
                val arc = Path().apply {
                    moveTo(6.4f * u, 11f * u)
                    cubicTo(6.4f * u, 20f * u, 17.6f * u, 20f * u, 17.6f * u, 11f * u)
                }
                drawPath(arc, tint, style = stroke)
                drawLine(tint, point(12f, 18.2f), point(12f, 21f), 1.65f * u, StrokeCap.Round)
                drawLine(tint, point(9f, 21f), point(15f, 21f), 1.65f * u, StrokeCap.Round)
            }
            else -> {
                val path = Path().apply {
                    moveTo(12f * u, 6.4f * u)
                    cubicTo(9.8f * u, 4.5f * u, 6f * u, 4.3f * u, 3f * u, 5.4f * u)
                    lineTo(3f * u, 17.8f * u)
                    cubicTo(6.5f * u, 16.8f * u, 9.5f * u, 17.2f * u, 12f * u, 19f * u)
                    cubicTo(14.5f * u, 17.2f * u, 17.5f * u, 16.8f * u, 21f * u, 17.8f * u)
                    lineTo(21f * u, 5.4f * u)
                    cubicTo(18f * u, 4.3f * u, 14.2f * u, 4.5f * u, 12f * u, 6.4f * u)
                    lineTo(12f * u, 19f * u)
                }
                drawPath(path, tint, style = stroke)
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

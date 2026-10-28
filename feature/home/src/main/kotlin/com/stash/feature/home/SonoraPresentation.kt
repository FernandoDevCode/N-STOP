package com.stash.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stash.core.data.mix.MixBuildState
import com.stash.core.model.MusicSource
import com.stash.core.ui.visual.*

/** Home-only palette. Shared typography, artwork loader and host chrome keep their contracts. */
@Composable
internal fun SonoraHomeTheme(content: @Composable () -> Unit) {
    val scheme = if (!LocalVisualDark.current) MaterialTheme.colorScheme else MaterialTheme.colorScheme.copy(
        background = if (LocalVisualAmoled.current) Color.Black else Color(0xFF0E1115),
        surface = Color(0xFF191D23), surfaceVariant = Color(0xFF343944),
        onBackground = Color(0xFFF4F5F7), onSurface = Color(0xFFF4F5F7),
        onSurfaceVariant = Color(0xFFB4B8C4), outline = Color(0xFF343944),
        primary = Color(0xFFB8AED5), onPrimary = Color(0xFF0E1115),
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

@Composable
internal fun Modifier.sonoraHomeBackground(): Modifier {
    val illuminated = LocalVisualDark.current && !LocalVisualAmoled.current
    return background(VisualDesign.Background).drawWithCache {
        val light = Brush.radialGradient(listOf(Color(0x0DD4D8E0), Color.Transparent),
            center = Offset(size.width * .75f, 0f), radius = size.width * 1.2f)
        onDrawBehind { if (illuminated) drawRect(light) }
    }
}

@Composable
internal fun SonoraMixCard(title: String, artUrl: String?, source: MusicSource,
    buildState: MixBuildState = MixBuildState.READY, onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    SonoraContentCard(VisualCard("mix", title, when (buildState) {
        MixBuildState.BUILDING -> "Preparando…"
        MixBuildState.EMPTY -> "Vazio — edite para adicionar músicas"
        else -> when (source) { MusicSource.SPOTIFY -> "Spotify"; MusicSource.YOUTUBE -> "YouTube Music"; MusicSource.LOCAL -> "Neste dispositivo"; MusicSource.BOTH -> "Sua biblioteca" }
    }, artUrl), onClick, onLongPress, modifier)
}

/** Artwork keeps its square proportion; card height grows with the user's font size. */
@Composable
internal fun SonoraContentCard(card: VisualCard, onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.width(136.dp).combinedClickable(onClick = onClick, onLongClick = onLongPress)
        .semantics(mergeDescendants = true) { contentDescription = listOf(card.title, card.subtitle).filter { it.isNotBlank() }.joinToString(", ") }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(VisualDesign.Radius.Card))
            .background(VisualDesign.Surface).border(.5.dp, VisualDesign.Border, RoundedCornerShape(VisualDesign.Radius.Card))) {
            if (card.liked) Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(VisualDesign.Surface, VisualDesign.Accent.copy(alpha = .18f)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, null, Modifier.size(38.dp), tint = VisualDesign.Accent)
            } else SonoraArtwork(card.artwork, Modifier.fillMaxSize())
        }
        Text(card.title, Modifier.padding(top = 8.dp), style = VisualDesign.CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (card.subtitle.isNotBlank()) Text(card.subtitle, Modifier.padding(top = 3.dp), style = VisualDesign.Caption,
            color = VisualDesign.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SonoraCardRail(title: String, modifier: Modifier = Modifier, actionText: String? = null,
    onActionClick: (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    Column(modifier) {
        Spacer(Modifier.height(VisualDesign.Space.Section))
        SonoraSectionTitle(title, actionText = actionText, onActionClick = onActionClick)
        LazyRow(contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
            horizontalArrangement = Arrangement.spacedBy(VisualDesign.Space.Rail), content = content)
    }
}

@Composable
internal fun SonoraGenreFilters(filters: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it }) { label ->
            val active = selected == label
            val displayLabel = when (label.trim().lowercase()) {
                "all" -> stringResource(R.string.nonstop_genre_all)
                "electronic" -> stringResource(R.string.nonstop_genre_electronic)
                "classical" -> stringResource(R.string.nonstop_genre_classical)
                else -> label
            }
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(label) })
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(34.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.surfaceVariant
                            else VisualDesign.Surface.copy(alpha = .38f),
                            shape,
                        )
                        .border(
                            if (active) 1.dp else .5.dp,
                            if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = .52f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = .27f),
                            shape,
                        )
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        displayLabel,
                        style = VisualDesign.Caption,
                        color = if (active) VisualDesign.Ink else VisualDesign.Muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Supports real section actions, including expand/collapse, without shrinking a heading. */
@Composable
internal fun SonoraSectionTitle(title: String, modifier: Modifier = Modifier, actionText: String? = null,
    onActionClick: (() -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = VisualDesign.Gutter).padding(bottom = 10.dp)) {
        val density = LocalDensity.current
        val actionWidth = with(density) { rememberTextMeasurer().measure(actionText.orEmpty(), VisualDesign.Caption).size.width.toDp() + 24.dp }
        val stacked = actionText != null && maxWidth - actionWidth < 160.dp * density.fontScale
        val titleContent: @Composable (Modifier) -> Unit = { Text(title, it.semantics { heading() }, style = VisualDesign.Heading, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        val action: @Composable () -> Unit = { if (actionText != null && onActionClick != null)
            TextButton(onActionClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionText, style = VisualDesign.Caption, color = VisualDesign.Muted)
            }
        }
        if (stacked) Column(Modifier.fillMaxWidth()) {
            titleContent(Modifier.fillMaxWidth())
            Box(Modifier.align(Alignment.End)) { action() }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            titleContent(Modifier.weight(1f)); action()
        }
    }
}

/** Real discovery artwork and actions. Height follows text; there is no progress or luminous edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SonoraHeroCard(label: String, title: String, subtitle: String, artUrl: String?,
    onPlay: () -> Unit, onOpen: () -> Unit, onCreateMix: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(VisualDesign.Radius.Hero)
    Box(modifier.fillMaxWidth().heightIn(min = VisualDesign.Size.Hero).clip(shape)
        .background(VisualDesign.Surface).border(.5.dp, VisualDesign.Border.copy(alpha = .65f), shape)
        .combinedClickable(onClick = onOpen, onLongClick = onLongPress)) {
        SonoraArtwork(artUrl, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x20101115), Color(0xEE101115), Color(0xFF101115)))))
        Column(Modifier.fillMaxWidth().padding(VisualDesign.Size.HeroPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = VisualDesign.Caption, color = Color(0xFFD4D8E0))
                onLongPress?.let { IconButton(it) { Icon(Icons.Outlined.MoreHoriz, "Opções de $title", tint = Color.White) } }
            }
            Spacer(Modifier.height(80.dp))
            Text(title, style = VisualDesign.HeroTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = title })
            if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(top = 6.dp), style = VisualDesign.Body,
                color = Color(0xFFD4D8E0), maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onPlay, shape = CircleShape) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp)); Text("Ouvir agora", style = VisualDesign.Body)
                }
                TextButton(onOpen) { Text("Abrir playlist", color = Color.White) }
                onCreateMix?.let { TextButton(it) { Text("Criar mix", color = Color.White) } }
            }
        }
    }
}

/** Reuses the image pipeline for real covers; missing artwork stays neutral. */
@Composable
private fun SonoraArtwork(url: String?, modifier: Modifier = Modifier) {
    if (!url.isNullOrBlank()) VisualCover(url, modifier)
    else Box(modifier.background(Brush.verticalGradient(listOf(VisualDesign.Surface, VisualDesign.Background))),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.MusicNote, null, Modifier.size(32.dp), tint = VisualDesign.Muted.copy(alpha = .6f))
    }
}

@Composable
internal fun SonoraLikedCard(trackCount: Int, onClick: () -> Unit) {
    SonoraContentCard(VisualCard("liked", "Músicas curtidas", "$trackCount músicas", liked = true), onClick)
}

package com.stash.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.core.data.mix.MixBuildState
import com.stash.core.model.Track
import com.stash.core.ui.ambient.LocalAlbumChrome
import com.stash.core.ui.visual.VisualDesign

/** Exactly one available ready, image-backed mix from each source category.
 * The full configured mix rails stay intact below. */
internal fun homeQuickMixes(state: HomeUiState): List<HomeMix> = listOfNotNull(
    state.yourMixes.firstOrNull { !it.artUrl.isNullOrBlank() && it.buildState == MixBuildState.READY },
    state.madeForYou.firstOrNull { !it.artUrl.isNullOrBlank() && it.buildState == MixBuildState.READY },
    state.radios.firstOrNull { !it.artUrl.isNullOrBlank() && it.buildState == MixBuildState.READY },
    state.moodDecades.firstOrNull { !it.artUrl.isNullOrBlank() && it.buildState == MixBuildState.READY },
).distinctBy { it.id }.take(4)

/** Deliberately compact. Existing Daily Discover hero remains above it. */
@Composable
internal fun SonoraDiscoveryShortcuts(
    onExplore: () -> Unit,
    onRadio: () -> Unit,
    onSearch: () -> Unit,
) {
    val accent = LocalAlbumChrome.current.selectedTab
    val labels = listOf(
        stringResource(R.string.sonora_shortcut_explore),
        stringResource(R.string.sonora_shortcut_radio),
        stringResource(R.string.sonora_shortcut_search),
    )
    val icons = listOf(Icons.Outlined.Explore, Icons.Outlined.Radio, Icons.Outlined.Search)
    val actions = listOf(onExplore, onRadio, onSearch)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VisualDesign.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Row(
                modifier = Modifier.weight(1f).height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VisualDesign.Surface)
                    .border(.5.dp, VisualDesign.Border, RoundedCornerShape(14.dp))
                    .clickable(onClick = actions[index])
                    .semantics { contentDescription = label }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icons[index], null, Modifier.size(17.dp), tint = accent.copy(alpha = .82f))
                Text(label, style = VisualDesign.Caption, color = VisualDesign.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SonoraQuickMixRow(mixes: List<HomeMix>, onMixClick: (HomeMix) -> Unit) {
    SonoraSectionTitle(stringResource(R.string.sonora_quick_mixes))
    LazyRow(
        contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(mixes, key = { it.id }) { mix ->
            Row(
                modifier = Modifier.width(185.dp).height(74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VisualDesign.Surface)
                    .border(.5.dp, VisualDesign.Border, RoundedCornerShape(14.dp))
                    .clickable { onMixClick(mix) }
                    .semantics { contentDescription = mix.title },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = mix.artUrl, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(74.dp),
                )
                Text(mix.title, Modifier.padding(horizontal = 11.dp), style = VisualDesign.CardTitle,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SonoraRediscoveryRow(
    tracks: List<Track>,
    onArtistClick: (String) -> Unit,
) {
    SonoraSectionTitle(stringResource(R.string.sonora_rediscovery_artists))
    LazyRow(
        contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tracks, key = { it.artist.trim().lowercase() }) { track ->
            Column(
                modifier = Modifier.width(92.dp).clickable { onArtistClick(track.artist) }
                    .semantics { contentDescription = track.artist },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape)
                        .background(VisualDesign.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = track.albumArtPath ?: track.albumArtUrl
                    if (art.isNullOrBlank()) {
                        Icon(Icons.Outlined.MusicNote, null, Modifier.size(26.dp),
                            tint = VisualDesign.Muted)
                    } else {
                        AsyncImage(
                            model = art, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(track.artist, style = VisualDesign.Caption, color = VisualDesign.Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

package com.stash.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.ui.components.AlbumSquareCard
import com.stash.core.ui.components.CrispChipRow
import com.stash.core.ui.components.bottomChromePadding
import com.stash.data.ytmusic.model.AlbumSummary

private val DiscoveryViolet = Color(0xFFB16BFF)
private val DiscoveryCyan = Color(0xFF54CDEB)

@Composable
fun NonstopExploreScreen(
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenMix: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomChromePadding(20.dp)),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            DiscoveryScreenHeading(
                title = stringResource(R.string.nonstop_explore_title),
                subtitle = stringResource(R.string.nonstop_explore_subtitle),
                radio = false,
            )
        }
        if (state.qobuzDiscoveryEnabled) {
            item {
                CrispChipRow(
                    chips = state.genres.map { it.label },
                    selected = state.selectedGenre,
                    onSelect = viewModel::onSelectGenre,
                )
            }
        }
        if (state.newReleases.isNotEmpty()) {
            item {
                DiscoverySectionLabel(stringResource(R.string.nonstop_explore_releases), stringResource(R.string.nonstop_explore_arrivals))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.newReleases, key = { it.id }) { album ->
                        AlbumSquareCard(title = album.title, artist = album.artist, thumbnailUrl = album.thumbnailUrl, year = null, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }
        if (state.topAlbums.isNotEmpty()) {
            item {
                DiscoverySectionLabel(stringResource(R.string.nonstop_explore_featured), stringResource(R.string.nonstop_explore_albums))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.topAlbums, key = { it.id }) { album ->
                        AlbumSquareCard(title = album.title, artist = album.artist, thumbnailUrl = album.thumbnailUrl, year = null, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }
        if (state.moodDecades.isNotEmpty()) {
            item {
                DiscoverySectionLabel(stringResource(R.string.nonstop_explore_rediscovery), stringResource(R.string.nonstop_explore_rediscovery_subtitle))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.moodDecades, key = { it.id }) { mix ->
                        MixRailCard(
                            title = mix.title,
                            artUrl = mix.artUrl,
                            source = mix.source,
                            buildState = mix.buildState,
                            onClick = { onOpenMix(mix.id) },
                        )
                    }
                }
            }
        }
        // Show a quiet placeholder until the initial catalog request resolves.
        if (state.isLoading && state.newReleases.isEmpty() &&
            state.topAlbums.isEmpty() && state.moodDecades.isEmpty()
        ) {
            item { DiscoveryLoadingRail(squareArtwork = true) }
        }
        if (!state.isLoading && state.newReleases.isEmpty() &&
            state.topAlbums.isEmpty() && state.moodDecades.isEmpty()
        ) {
            item {
                DiscoveryEmptyMessage(
                    stringResource(R.string.nonstop_explore_empty),
                )
            }
        }
    }
}

@Composable
fun NonstopRadioScreen(
    onOpenMix: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomChromePadding(20.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            DiscoveryScreenHeading(
                title = stringResource(R.string.nonstop_radio_title),
                subtitle = stringResource(R.string.nonstop_radio_subtitle),
                radio = true,
            )
        }
        if (state.radios.isNotEmpty()) {
            item {
                DiscoverySectionLabel(stringResource(R.string.nonstop_radio_your_stations), stringResource(R.string.nonstop_radio_stations_subtitle))
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.radios, key = { it.id }) { mix ->
                        MixRailCard(
                            title = mix.title,
                            artUrl = mix.artUrl,
                            source = mix.source,
                            buildState = mix.buildState,
                            onClick = { onOpenMix(mix.id) },
                        )
                    }
                }
            }
        }
        if (state.madeForYou.isNotEmpty()) {
            item {
                DiscoverySectionLabel(stringResource(R.string.nonstop_radio_listen_again), stringResource(R.string.nonstop_radio_listen_subtitle))
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.madeForYou, key = { it.id }) { mix ->
                        MixRailCard(
                            title = mix.title,
                            artUrl = mix.artUrl,
                            source = mix.source,
                            buildState = mix.buildState,
                            onClick = { onOpenMix(mix.id) },
                        )
                    }
                }
            }
        }
        if (state.isLoading && state.radios.isEmpty() && state.madeForYou.isEmpty()) {
            item { DiscoveryLoadingRail(squareArtwork = false) }
        }
        if (!state.isLoading && state.radios.isEmpty() && state.madeForYou.isEmpty()) {
            item {
                DiscoveryEmptyMessage(
                    stringResource(R.string.nonstop_radio_empty),
                )
            }
        }
    }
}

@Composable
private fun DiscoveryScreenHeading(title: String, subtitle: String, radio: Boolean) {
    val glow = if (radio) DiscoveryCyan else DiscoveryViolet
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(glow.copy(alpha = 0.15f), Color.Transparent),
                ),
            )
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = if (radio) Icons.Outlined.Radio else Icons.Outlined.Explore,
            contentDescription = null,
            tint = glow,
            modifier = Modifier.size(27.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoverySectionLabel(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Static, decorative placeholders for initial Explore/Radio loading.
 * They reveal no fabricated catalog data and honor reduced-motion preferences.
 */
@Composable
private fun DiscoveryLoadingRail(squareArtwork: Boolean) {
    val tileWidth = if (squareArtwork) 152.dp else 158.dp
    val tileHeight = if (squareArtwork) 152.dp else 188.dp
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Column(modifier = Modifier.clearAndSetSemantics {}) {
        Box(
            Modifier.padding(horizontal = 20.dp)
                .size(width = 154.dp, height = 19.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(placeholder),
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(3) {
                Column(Modifier.width(tileWidth)) {
                    Box(
                        Modifier.size(width = tileWidth, height = tileHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(placeholder),
                    )
                    if (squareArtwork) {
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier.size(width = 112.dp, height = 12.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(placeholder),
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.size(width = 74.dp, height = 10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(placeholder),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryEmptyMessage(message: String) {
    Text(
        message,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

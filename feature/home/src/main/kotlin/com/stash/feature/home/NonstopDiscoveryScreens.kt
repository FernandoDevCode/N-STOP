package com.stash.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
                title = "Explorar",
                subtitle = "Novos sons, artistas e caminhos para ouvir.",
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
                DiscoverySectionLabel("Novos lançamentos", "Acabaram de chegar")
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.newReleases, key = { it.id }) { album ->
                        DiscoveryAlbumTile(album, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }
        if (state.topAlbums.isNotEmpty()) {
            item {
                DiscoverySectionLabel("Em destaque", "Álbuns para descobrir")
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.topAlbums, key = { it.id }) { album ->
                        DiscoveryAlbumTile(album, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }
        if (state.moodDecades.isNotEmpty()) {
            item {
                DiscoverySectionLabel("Redescoberta", "Outras formas de ouvir o que você gosta")
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
        if (!state.isLoading && state.newReleases.isEmpty() &&
            state.topAlbums.isEmpty() && state.moodDecades.isEmpty()
        ) {
            item {
                DiscoveryEmptyMessage(
                    "Conecte uma fonte musical para explorar lançamentos e sugestões.",
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
                title = "Rádio",
                subtitle = "Deixe a próxima música surpreender você.",
                radio = true,
            )
        }
        if (state.radios.isNotEmpty()) {
            item {
                DiscoverySectionLabel("Suas rádios", "Estações geradas a partir da sua música")
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
                DiscoverySectionLabel("Continue ouvindo", "Misturas feitas para o seu gosto")
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
        if (!state.isLoading && state.radios.isEmpty() && state.madeForYou.isEmpty()) {
            item {
                DiscoveryEmptyMessage(
                    "Suas estações vão aparecer aqui quando houver músicas suficientes para recomendar.",
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

@Composable
private fun DiscoveryAlbumTile(album: AlbumSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = 152.dp, height = 198.dp)
            .clickable(onClick = onClick),
    ) {
        if (album.thumbnailUrl != null) {
            AsyncImage(
                model = album.thumbnailUrl,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(152.dp).clip(RoundedCornerShape(10.dp)),
            )
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(152.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(DiscoveryViolet.copy(alpha = 0.65f), DiscoveryCyan.copy(alpha = 0.45f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Album, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(
            album.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

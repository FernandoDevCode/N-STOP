package com.stash.feature.library.share

import com.stash.core.common.BrazilianDisplayFormat
import com.stash.feature.library.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

import com.stash.core.model.share.ShareConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedMixScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    /** Follow / Save a copy: opens the new playlist in place of this screen. */
    onJoinedPlaylist: (Long) -> Unit = onOpenPlaylist,
    viewModel: SharedMixViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.shared_back)) }
        when (val s = state) {
            SharedMixUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is SharedMixUiState.Error -> Column(
                Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, style = MaterialTheme.typography.titleMedium)
                if (s.retryable) { Spacer(Modifier.height(16.dp)); Button(onClick = viewModel::load) { Text(stringResource(R.string.shared_retry)) } }
            }
            is SharedMixUiState.Loaded -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Docs stored before the Worker allowlist may carry any host: never load an off-list one.
                        s.doc.covers.firstOrNull(ShareConfig::isAllowedCover)?.let {
                            AsyncImage(it, null, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.size(16.dp))
                        }
                        Column {
                            Text(s.doc.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val by = s.doc.sharedBy?.let { stringResource(R.string.shared_mix_by, it) }.orEmpty()
                            val updated = s.doc.updatedAt.takeIf { it > 0 }?.let {
                                stringResource(R.string.shared_mix_updated,
                                    BrazilianDisplayFormat.date(it * 1000L))
                            }.orEmpty()
                            Text("${if (s.doc.tracks.size == 1) stringResource(R.string.shared_mix_tracks_one) else stringResource(R.string.shared_mix_tracks_many, s.doc.tracks.size)}$by$updated", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::play, enabled = !s.busy) { Text(stringResource(R.string.shared_play)) }
                        when {
                            s.isOwnMix -> Text(stringResource(R.string.shared_mix_owned), Modifier.align(Alignment.CenterVertically))
                            s.followedPlaylistId != null -> {
                                OutlinedButton(onClick = { onOpenPlaylist(s.followedPlaylistId) }) { Text(stringResource(R.string.shared_mix_following)) }
                                var confirmUnfollow by remember { mutableStateOf(false) }
                                OutlinedButton(onClick = { if (confirmUnfollow) viewModel.unfollow() else confirmUnfollow = true }, enabled = !s.busy) {
                                    Text(stringResource(if (confirmUnfollow) R.string.shared_mix_unfollow_confirmation else R.string.shared_mix_unfollow))
                                }
                            }
                            else -> {
                                Button(onClick = { viewModel.follow(onJoinedPlaylist) }, enabled = !s.busy) { Text(stringResource(R.string.shared_mix_follow)) }
                                OutlinedButton(onClick = { viewModel.saveCopy(onJoinedPlaylist) }, enabled = !s.busy) { Text(stringResource(R.string.shared_mix_save_copy)) }
                            }
                        }
                    }
                    s.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                itemsIndexed(s.doc.tracks) { i, t ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${i + 1}. ${t.title}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

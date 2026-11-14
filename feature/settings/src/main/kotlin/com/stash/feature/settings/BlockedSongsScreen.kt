package com.stash.feature.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.ui.theme.StashTheme
import com.stash.feature.settings.components.SettingsPageHeader
import com.stash.feature.settings.components.SettingsPageSurface
import com.stash.feature.settings.R
import androidx.compose.ui.res.stringResource

/**
 * Settings → Blocked Songs viewer.
 *
 * Lists every track the user has blocked from future syncs (via the
 * "Delete & Block" path on playlists or single tracks). Each row can be
 * unblocked with one tap — the next sync will re-queue that track's
 * identity normally. Empty-state is friendly because "no blocked songs"
 * is the expected state for most users.
 */
@Composable
fun BlockedSongsScreen(
    onBack: () -> Unit,
    viewModel: BlockedSongsViewModel = hiltViewModel(),
) {
    val blocked by viewModel.blockedTracks.collectAsStateWithLifecycle()
    val extendedColors = StashTheme.extendedColors
    SettingsPageSurface {
        Column(Modifier.fillMaxSize()) {
            SettingsPageHeader(
                title = stringResource(R.string.sonora_blocked_title),
                onBack = onBack,
                // This deep route draws edge-to-edge; keep its back target below
                // the system status bar just as the previous header did.
                modifier = Modifier.statusBarsPadding(),
            )
            if (blocked.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .48f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.sonora_blocked_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.sonora_blocked_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    item(key = "description") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.sonora_blocked_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.sonora_blocked_count, blocked.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(blocked, key = { it.canonicalKey }) { row ->
                        BlockedTrackListItem(
                            row = row,
                            onUnblock = { viewModel.unblock(row.canonicalKey) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 80.dp, end = 20.dp),
                            thickness = .5.dp,
                            color = extendedColors.glassBorder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedTrackListItem(
    row: BlockedTrackRow,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // No Coil dependency in :feature:settings — skip the album art
            // thumbnail. A blocked song is an exceptional row type anyway,
            // so the monochrome icon reads as intentional rather than
            // missing artwork.
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(onClick = onUnblock) {
            Text(
                text = stringResource(R.string.sonora_blocked_unblock),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

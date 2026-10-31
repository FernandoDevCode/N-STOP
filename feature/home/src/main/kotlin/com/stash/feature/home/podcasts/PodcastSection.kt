package com.stash.feature.home.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stash.feature.home.R
import com.stash.core.common.BrazilianDisplayFormat

/**
 * Embedded under the existing Home media tabs, above the persistent music
 * chrome. Real RSS episodes only: no invented catalogue tiles or Spotify extraction.
 */
@Composable
fun PodcastSection(viewModel: PodcastViewModel = LocalPodcastViewModel.current ?: hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.podcast_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.podcast_source_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.podcast_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(
                    onClick = viewModel::search,
                    enabled = state.query.trim().length >= 2 && !state.busy,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.podcast_search_button),
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        )

        if (state.error != null) {
            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
        if (state.busy) CircularProgressIndicator(modifier = Modifier.size(28.dp))

        val show = state.selected
        if (show != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::back) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.podcast_back))
                }
                Text(stringResource(R.string.podcast_episodes), fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
            }
            val heroShape = RoundedCornerShape(18.dp)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(heroShape)
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF362447), Color(0xFF151723), Color(0xFF12323D)),
                    ))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), heroShape)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(show.imageUrl, show.title, 102)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(show.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3,
                        overflow = TextOverflow.Ellipsis)
                    if (show.author.isNotBlank()) {
                        Text(show.author, style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.70f), maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                    val follows = state.followed.any { it.feedUrl == show.feedUrl }
                    OutlinedButton(
                        onClick = viewModel::toggleFollow,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text(stringResource(if (follows) R.string.podcast_unfollow else R.string.podcast_follow))
                    }
                }
            }
            if (state.episodes.isEmpty() && !state.busy && state.error == null) {
                Text(stringResource(R.string.podcast_no_episodes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.episodes.take(60).forEach { episode ->
                PodcastEpisodeRow(
                    episode = episode,
                    active = state.playingId == episode.id,
                    playing = state.isPlaying,
                    downloaded = episode.id in state.downloaded,
                    progress = state.downloading[episode.id],
                    onPlay = { if (state.playingId == episode.id) viewModel.togglePause() else viewModel.play(episode) },
                    onDownload = { viewModel.download(episode) },
                    onRemove = { viewModel.removeDownload(episode) },
                )
            }
        } else {
            if (state.followed.isNotEmpty()) {
                Text(stringResource(R.string.podcast_following),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.followed, key = { it.feedUrl }) { followed ->
                        PodcastShowTile(followed) { viewModel.open(followed) }
                    }
                }
            }
            if (state.results.isNotEmpty()) {
                Text(stringResource(R.string.podcast_results),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.results, key = { it.feedUrl }) { result ->
                        PodcastShowTile(result) { viewModel.open(result) }
                    }
                }
            } else if (!state.busy && state.followed.isEmpty()) {
                Text(stringResource(R.string.podcast_empty_search),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Cover(url: String?, name: String, size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Headphones, contentDescription = null,
            modifier = Modifier.size((size / 3).dp),
            tint = MaterialTheme.colorScheme.primary)
        if (url != null) AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp),
        )
    }
}

@Composable
private fun PodcastShowTile(show: PodcastShow, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(138.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Cover(show.imageUrl, show.title, 138)
        Text(show.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(show.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    active: Boolean,
    playing: Boolean,
    downloaded: Boolean,
    progress: Int?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (active) 0.58f else 0.32f))
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(episode.title, style = MaterialTheme.typography.titleMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        if (episode.published.isNotBlank()) {
            Text(formatPodcastDate(episode.published), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (episode.description.isNotBlank()) {
            Text(episode.description, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPlay) {
                Icon(if (active && playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(if (active && playing) R.string.podcast_pause else R.string.podcast_play))
            }
            when {
                downloaded -> TextButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.podcast_delete))
                }
                progress != null -> Text(
                    if (progress <= 0) stringResource(R.string.podcast_preparing)
                    else stringResource(R.string.podcast_downloading, progress),
                    style = MaterialTheme.typography.labelMedium,
                )
                else -> TextButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.podcast_download))
                }
            }
        }
        if (progress != null) {
            if (progress <= 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


/** Display only; RFC 1123 RSS dates remain unchanged in feeds/cache. */
private fun formatPodcastDate(raw: String): String = BrazilianDisplayFormat.rssDate(raw)

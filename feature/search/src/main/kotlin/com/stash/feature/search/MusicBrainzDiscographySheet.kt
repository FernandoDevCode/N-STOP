package com.stash.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stash.core.data.musicbrainz.MusicBrainzAlbumGroup
import com.stash.core.data.musicbrainz.MusicBrainzEdition

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MusicBrainzDiscographySheet(
    state: MusicBrainzDiscoveryState,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onGroup: (MusicBrainzAlbumGroup) -> Unit,
    onEdition: (MusicBrainzEdition) -> Unit,
    onTrack: (Int, Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (state.selectedGroup != null) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                }
                Text(
                    text = state.selectedEdition?.let { it.description?.let { label -> "${it.title} · $label" } ?: it.title }
                        ?: state.selectedGroup?.title ?: "Discografia completa",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (state.selectedEdition != null) {
                Text(
                    "Edições adicionais: a reprodução depende da disponibilidade de cada gravação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (state.loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 510.dp)) {
                    when {
                        state.selectedEdition != null -> {
                            if (state.tracks.isEmpty()) item { Text("Esta edição não possui faixas cadastradas.") }
                            itemsIndexed(state.tracks, key = { index, track -> "${track.disc}:${track.position}:$index" }) { index, track ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onTrack(index, false) },
                                            onLongClick = { onTrack(index, true) },
                                        )
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction("Baixar esta faixa") {
                                                    onTrack(index, true)
                                                    true
                                                }
                                            )
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${track.position}. ${track.title}", style = MaterialTheme.typography.bodyLarge)
                                        val caption = when {
                                            state.resolved[index] != null -> "Gravação encontrada"
                                            index in state.unavailable -> "Sem correspondência confirmada"
                                            else -> "Toque para ouvir · segure para baixar"
                                        }
                                        Text(caption, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (state.resolving == index) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                                }
                            }
                        }
                        state.selectedGroup != null -> {
                            if (state.editions.isEmpty()) item { Text("Nenhuma edição cadastrada.") }
                            items(state.editions, key = { it.id }) { edition ->
                                Column(
                                    Modifier.fillMaxWidth().clickable { onEdition(edition) }
                                        .padding(vertical = 12.dp),
                                ) {
                                    Text(edition.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        listOfNotNull(edition.description, edition.date, edition.country)
                                            .joinToString(" · ").ifBlank { "Edição sem descrição" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        else -> {
                            if (state.groups.isEmpty()) item { Text("Nenhum álbum cadastrado para este artista.") }
                            items(state.groups, key = { it.id }) { group ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { onGroup(group) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(group.title, style = MaterialTheme.typography.titleMedium)
                                        group.year?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text("Edições", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

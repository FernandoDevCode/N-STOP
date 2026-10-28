package com.stash.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.ui.components.bottomChromePadding
import com.stash.core.ui.visual.*
import com.stash.data.ytmusic.model.AlbumSummary

internal fun HomeUiState.discoveryPresentation(radio: Boolean): VisualScreenState {
    fun mixes(id: String, title: String, values: List<HomeMix>) = VisualSection(id, title,
        values.map { VisualCard("mix:${it.id}", it.title, when (it.buildState) { com.stash.core.data.mix.MixBuildState.BUILDING -> "Preparando…"; com.stash.core.data.mix.MixBuildState.EMPTY -> "Mix vazio"; else -> "" }, it.artUrl) })
    val rails = if (radio) listOf(mixes("radio", "Suas estações", radios), mixes("again", "Ouvir novamente", madeForYou))
    else listOf(
        VisualSection("new", "Novos lançamentos", newReleases.map { VisualCard("new:${it.id}", it.title, it.artist, it.thumbnailUrl) }, plain = true),
        VisualSection("top", "Álbuns em destaque", topAlbums.map { VisualCard("top:${it.id}", it.title, it.artist, it.thumbnailUrl) }),
        mixes("mood", "Redescobrir", moodDecades))
    val empty = rails.all { it.cards.isEmpty() }
    return VisualScreenState(page = if (radio) VisualPage.Radio else VisualPage.Explore,
        filters = if (!radio && qobuzDiscoveryEnabled) genres.map { it.label } else emptyList(),
        selectedFilter = selectedGenre, sections = rails,
        status = when { !empty -> VisualStatus.Content; isLoading -> VisualStatus.Loading; else -> VisualStatus.Empty },
        message = if (radio) "Suas rádios e mixes aparecerão aqui conforme você ouvir músicas."
            else "Nenhum conteúdo disponível. Verifique sua conexão e os provedores nas configurações.",
        emptyAction = "Configurações")
}

@Composable
fun NonstopExploreScreen(onOpenAlbum: (AlbumSummary) -> Unit, onOpenMix: (Long) -> Unit,
    onSettings: () -> Unit = {}, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VisualScreen(state.discoveryPresentation(false), VisualActions(
        onSettings = onSettings, onEmpty = onSettings, onFilter = viewModel::onSelectGenre,
        onCard = { key -> when {
            key.startsWith("mix:") -> onOpenMix(key.substringAfter(':').toLong())
            key.startsWith("new:") -> state.newReleases.firstOrNull { it.id == key.substringAfter(':') }?.let(onOpenAlbum)
            else -> state.topAlbums.firstOrNull { it.id == key.substringAfter(':') }?.let(onOpenAlbum)
        } }), Modifier.padding(bottom = bottomChromePadding(0.dp)))
}

@Composable
fun NonstopRadioScreen(onOpenMix: (Long) -> Unit, onSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VisualScreen(state.discoveryPresentation(true), VisualActions(onSettings = onSettings, onEmpty = onSettings,
        onCard = { onOpenMix(it.substringAfter(':').toLong()) }),
        Modifier.padding(bottom = bottomChromePadding(0.dp)))
}

package com.stash.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stash.core.data.mix.MixBuildState
import com.stash.core.model.MusicSource
import com.stash.core.ui.visual.*

@Composable
internal fun SonoraMixCard(title: String, artUrl: String?, source: MusicSource,
    buildState: MixBuildState = MixBuildState.READY, onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier) {
        VisualRailCard(VisualCard("mix", title, when (buildState) {
            MixBuildState.BUILDING -> "Preparando…"
            MixBuildState.EMPTY -> "Vazio — edite para adicionar músicas"
            else -> when (source) { MusicSource.SPOTIFY -> "Spotify"; MusicSource.YOUTUBE -> "YouTube Music"; MusicSource.LOCAL -> "Neste dispositivo"; MusicSource.BOTH -> "Sua biblioteca" }
        }, artUrl), onClick, onLongPress)
    }
}

@Composable
internal fun SonoraCardRail(title: String, modifier: Modifier = Modifier, actionText: String? = null,
    onActionClick: (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    Column(modifier) {
        Spacer(Modifier.height(VisualDesign.Space.Section))
        VisualSectionTitle(title, onActionClick.takeIf { actionText != null })
        LazyRow(contentPadding = PaddingValues(horizontal = VisualDesign.Gutter),
            horizontalArrangement = Arrangement.spacedBy(VisualDesign.Space.Rail), content = content)
    }
}

/** Retains Android's open/create/context actions around the shared hero. */
@Composable
internal fun SonoraHeroCard(label: String, title: String, subtitle: String, artUrl: String?,
    onPlay: () -> Unit, onOpen: () -> Unit, onCreateMix: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null, modifier: Modifier = Modifier, loading: Boolean = false) {
    Column(modifier) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        VisualHeroCard(VisualHero(VisualCard("hero", title, subtitle, artUrl), label),
            onPlay = { if (!loading) onPlay() }, onMore = onLongPress ?: onOpen)
        FlowRow(Modifier.padding(horizontal = VisualDesign.Gutter)) {
            TextButton(onOpen, enabled = !loading) { Text("Abrir playlist") }
            onCreateMix?.let { TextButton(it) { Text("Criar mix") } }
        }
    }
}

@Composable
internal fun SonoraLikedCard(trackCount: Int, onClick: () -> Unit) {
    VisualRailCard(VisualCard("liked", "Músicas curtidas", "$trackCount músicas", liked = true), onClick)
}

package com.stash.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stash.core.data.artistphotos.SelectedArtistPhoto
import com.stash.core.ui.visual.VisualDesign
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.DefaultSearchSources
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.UniversalSearchActions
import com.stash.feature.search.contract.UniversalSearchResult

@Composable
internal fun SearchSourcesSheet(
    selection: SearchSourceSelection,
    actions: UniversalSearchActions,
    onDismiss: () -> Unit,
) {
    val visible = selection.normalizedVisibleFilters()
    SearchSheet(onDismiss) {
        Text(
            stringResource(R.string.universal_search_sources),
            Modifier.semantics { heading() },
            style = VisualDesign.HeroTitle,
        )
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(searchSurface(.09f)),
        ) {
            SourceToggle(
                R.string.universal_search_spotify, R.string.universal_search_spotify_description,
                CatalogSource.SPOTIFY in visible.enabled, "searchFilterSPOTIFY",
            ) { actions.onSourceToggled(CatalogSource.SPOTIFY) }
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = VisualDesign.Ink.copy(alpha = .12f))
        }
        SourceToggle(
            R.string.universal_search_pin_sources,
            if (visible.isPinned) R.string.universal_search_pinned else R.string.universal_search_temporary,
            visible.isPinned, "searchPin",
        ) { actions.onPinToggled() }
        TextButton(
            actions::onRestoreDefaultSources,
            Modifier.align(Alignment.Start).testTag("searchRestoreDefaults"),
            enabled = visible.enabled != DefaultSearchSources,
        ) {
            Text(stringResource(R.string.universal_search_restore))
        }
    }
}

@Composable
private fun SourceToggle(label: Int, supporting: Int, selected: Boolean, tag: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag(tag)
            .toggleable(selected, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(label), style = VisualDesign.Heading)
            Text(stringResource(supporting), style = VisualDesign.Body, color = VisualDesign.Muted)
        }
        Switch(
            checked = selected,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(uncheckedTrackColor = searchSurface(.09f)),
        )
    }
}

@Composable
internal fun SearchResultSheet(
    result: UniversalSearchResult,
    shownArtwork: String?,
    artistPhotos: Map<String, SelectedArtistPhoto>,
    preparing: Boolean,
    onDismiss: () -> Unit,
    onAction: (SearchResultAction) -> Unit,
) {
    SearchSheet(onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(
                displayedSearchArtwork(result.identity, result.type, shownArtwork ?: result.artworkUrl, artistPhotos),
                result.type.icon,
                result.type == SearchResultType.ARTIST,
                width = 64.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(result.title, Modifier.semantics { heading() }, style = VisualDesign.Heading)
                val description = result.musicMetadata.joinToString(" · ")
                if (description.isNotEmpty()) Text(description, style = VisualDesign.Body, color = VisualDesign.Muted)
            }
        }
        val duration = result.durationMs?.takeIf { it >= 1_000 }?.let { formatSearchDuration(it) }
        val explicit = if (result.isExplicit) stringResource(R.string.universal_search_explicit) else null
        Text(
            listOfNotNull(
                stringResource(result.identity.source.nameRes), stringResource(result.resultLabelRes), duration, explicit,
            ).joinToString(" · "),
            color = VisualDesign.Muted,
            style = VisualDesign.Body,
        )
        val action = result.primarySearchAction
        if (action != null) {
            TextButton({ onAction(action) }, enabled = !preparing) {
                Text(stringResource(action.searchLabel))
            }
        } else {
            Text(stringResource(R.string.universal_search_catalog_only), style = VisualDesign.Body)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = searchSurface(.045f),
        contentColor = VisualDesign.Ink,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = VisualDesign.Gutter, end = VisualDesign.Gutter, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

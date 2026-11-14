package com.stash.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stash.core.ui.components.bottomChromePadding
import com.stash.core.ui.visual.VisualDesign

/** A fixed Sonora navigation header above a separately scrolling Settings spoke. */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsPageSurface(modifier) {
        Column(Modifier.fillMaxSize()) {
            SettingsPageHeader(title = title, onBack = onBack)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VisualDesign.Gutter)
                    .padding(bottom = bottomChromePadding()),
                verticalArrangement = Arrangement.spacedBy(SettingsGroupGap),
                content = content,
            )
        }
    }
}

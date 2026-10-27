package com.stash.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stash.core.ui.theme.NonstopDesign

/** Original app tab bar; the host owns routes and localized labels on each platform. */
@Composable
fun NonstopBottomNavigation(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(NonstopDesign.Background)
        .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 2.dp, vertical = 4.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Column(
                Modifier.weight(1f).height(48.dp).selectable(selected,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                NonstopNavIcon(index, selected, Modifier.size(26.dp))
                Text(label, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                        shadow = if (selected) Shadow(NonstopDesign.Violet, Offset.Zero, 16f) else null),
                    color = if (selected) NonstopDesign.ActiveInk else NonstopDesign.Secondary)
            }
        }
    }
}

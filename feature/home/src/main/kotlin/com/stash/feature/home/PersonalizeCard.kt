package com.stash.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Cold-start state for Home (spec §5): shown when there aren't enough discovery
 * sections to fill the screen — a brand-new or thin-library user. Prompts them
 * to connect/sync so discovery can be tailored, instead of rendering a near-blank
 * Home.
 */
@Composable
fun PersonalizeCard(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Personalize your Home",
    description: String = "Connect services and sync your library to feed music discovery as you listen.",
    actionLabel: String = "Get started",
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outline)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConnect) {
                Text(actionLabel)
            }
        }
    }
}

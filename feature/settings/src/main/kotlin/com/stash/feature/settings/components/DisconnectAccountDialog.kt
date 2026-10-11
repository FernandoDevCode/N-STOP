package com.stash.feature.settings.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Confirms disconnecting a source account (Spotify, YouTube Music) before it
 * happens. Disconnect is not just a sign-out: it also deletes every queued
 * and in-progress download of that source's songs
 * (`MusicRepository.cancelPendingDownloadsForSource`), which reconnecting does
 * not bring back until the next sync re-queues them. A stray tap on the red
 * button used to do all of that silently, so the dialog says what goes and
 * what stays.
 */
@Composable
fun DisconnectAccountDialog(
    serviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        title = { Text("Disconnect $serviceName?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Stash signs out of $serviceName on this phone and stops syncing it. " +
                    "Songs from $serviceName still waiting to download are dropped from " +
                    "the queue.\n\nSongs you've already downloaded stay in your library, " +
                    "and you can reconnect any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Disconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

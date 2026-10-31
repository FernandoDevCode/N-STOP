package com.stash.core.ui.selection

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stash.core.ui.R

/**
 * The one confirmation every multi-select list (Library Songs/Liked, album, artist, playlist and
 * Liked detail) shows before a batch "Remove download" (see [removeDownloadsNeedsConfirm]).
 * Shared so the copy, and the guard itself, can't drift between the five screens.
 *
 * @param count Songs whose downloads would be removed.
 * @param totalBytes Their combined file size; 0 when unknown (the size is then left out).
 */
@Composable
fun RemoveDownloadsConfirmDialog(
    count: Int,
    totalBytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) stringResource(R.string.remove_download_title_one) else stringResource(R.string.remove_download_title_many, count)) },
        text = { Text(if (totalBytes > 0) stringResource(R.string.remove_download_file_body, formatDownloadBytes(totalBytes)) else stringResource(R.string.remove_download_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.remove_download_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.remove_download_cancel)) }
        },
    )
}

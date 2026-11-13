package com.stash.feature.library.share

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Local-only Sonora share flow. Never sends playlist identities or account
 * data to the legacy Stash share Worker. Existing share links are not erased.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMixSheet(
    playlistId: Long,
    playlistName: String,
    trackCount: Int,
    onDismiss: () -> Unit,
    viewModel: ShareMixViewModel = hiltViewModel(key = "share-$playlistId"),
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Compartilhar mix", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "O compartilhamento por link não está disponível sem uma infraestrutura Sonora própria. " +
                    "Você ainda pode compartilhar as informações do seu mix.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val text = "$playlistName · $trackCount faixas"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartilhar mix"))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Compartilhar informações") }
        }
    }
}

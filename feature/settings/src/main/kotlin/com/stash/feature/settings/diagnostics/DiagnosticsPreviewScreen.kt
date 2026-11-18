package com.stash.feature.settings.diagnostics

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.feature.settings.components.SonoraSettingsCard as GlassCard
import com.stash.feature.settings.components.SettingsPageHeader
import com.stash.feature.settings.components.SettingsPageSurface
import com.stash.feature.settings.R
import androidx.compose.ui.res.stringResource

/**
 * Diagnostics preview — shows the user the full, already-redacted diagnostics
 * report before they share it, so "no passwords or tokens" is verifiable rather
 * than asserted. The text + shareable bundle come from
 * [DiagnosticsPreviewViewModel] (a thin wrapper over `DiagnosticsBundleBuilder`).
 *
 * Share mirrors Settings' crash-report ACTION_SEND exactly (content:// URI with
 * FLAG_GRANT_READ, wrapped in a chooser, Toast fallback). Copy drops the plain
 * text onto the clipboard for pasting into chat / a GitHub issue.
 */
@Composable
fun DiagnosticsPreviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val shareChooserTitle = stringResource(R.string.sonora_diagnostics_share_chooser)
    val unavailableShareApp = stringResource(R.string.sonora_diagnostics_no_share_app)
    val copiedMessage = stringResource(R.string.sonora_diagnostics_copied)

    SettingsPageSurface {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SettingsPageHeader(
                title = stringResource(R.string.nostop_about_diagnostics),
                onBack = onNavigateBack,
            )
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
            ) {
        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error ?: stringResource(R.string.sonora_diagnostics_build_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::rebuild) {
                        Text(stringResource(R.string.sonora_diagnostics_retry))
                    }
                }
            }

            else -> {
                Text(
                    text = stringResource(R.string.sonora_diagnostics_review),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Spacer(Modifier.height(12.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(
                        text = state.text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        enabled = state.bundle != null,
                        onClick = {
                            val uri = state.bundle?.contentUri ?: return@Button
                            // Mirrors SettingsScreen's crash-report ACTION_SEND:
                            // content:// URI + FLAG_GRANT_READ behind a chooser,
                            // with a Toast fallback when nothing can handle it.
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = state.bundle?.mimeType ?: "text/plain"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                putExtra(
                                    android.content.Intent.EXTRA_SUBJECT,
                                    context.getString(R.string.sonora_diagnostics_share_subject),
                                )
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = android.content.Intent.createChooser(send, shareChooserTitle)
                                .apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                            runCatching { context.startActivity(chooser) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        unavailableShareApp,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.sonora_diagnostics_share))
                    }

                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(state.text))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.sonora_diagnostics_copy))
                    }
                }

                // Home's "Report an issue" lands here first, so the report can
                // travel with the bug instead of being asked for afterwards.
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(ISSUE_TRACKER_URL) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sonora_diagnostics_open_issue))
                }

                Spacer(Modifier.height(8.dp))
            }
        }
            }
        }
    }
}

/** Same page Home's "Report an issue" opened directly before the preview sat in front of it. */
private const val ISSUE_TRACKER_URL = "https://github.com/FernandoDevCode/N-STOP/issues/new"

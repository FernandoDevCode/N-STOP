package com.stash.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.feature.settings.components.AudioQualityPicker
import com.stash.feature.settings.components.SettingsNavRow
import com.stash.feature.settings.components.SettingsScaffold
import com.stash.feature.settings.components.SettingsSectionLabel
import com.stash.feature.settings.components.SettingsToggleRow
import com.stash.feature.settings.components.SonoraSettingsCard

/**
 * Sonora playback quality controls. We do not expose Qobuz login,
 * FLAC/Hi-Res acquisition or third-party relays: the approved YouTube
 * transport cannot promise lossless output. Already-downloaded files
 * retain their formats; no storage migration or deletion occurs here.
 */
@Composable
fun SettingsAudioQualityScreen(
    onBack: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSquidWtfCaptcha: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(
        title = stringResource(R.string.nostop_nav_audio),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsSectionLabel(stringResource(R.string.nostop_quality_title))
        SonoraSettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.sonora_audio_quality_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AudioQualityPicker(
                    selected = uiState.audioQuality,
                    onSelected = viewModel::onQualityChanged,
                )
                Spacer(Modifier.height(12.dp))
                SettingsToggleRow(
                    title = stringResource(R.string.nostop_quality_save_data),
                    subtitle = stringResource(R.string.nostop_quality_save_youtube_hint),
                    checked = uiState.streamingSaveData,
                    onCheckedChange = viewModel::onStreamingSaveDataChanged,
                )
            }
        }

        SettingsSectionLabel(stringResource(R.string.nostop_quality_effects))
        SonoraSettingsCard {
            SettingsNavRow(
                title = stringResource(R.string.nostop_quality_equalizer),
                onClick = onNavigateToEqualizer,
            )
        }
    }
}

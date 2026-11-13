package com.stash.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.dp
import com.stash.core.data.prefs.LyricsSourcePreference
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.common.constants.StashConstants
import com.stash.feature.settings.components.SettingsGroupCard
import com.stash.feature.settings.components.SettingsRowPadH
import com.stash.feature.settings.components.SettingsRowPadV
import com.stash.core.media.SleepTimerController
import com.stash.feature.settings.components.SettingsScaffold
import com.stash.feature.settings.components.SettingsSectionLabel
import com.stash.feature.settings.components.SettingsSegmented
import com.stash.feature.settings.components.SettingsToggleRow
import kotlin.math.roundToInt

/**
 * The Playback spoke of the hub-and-spoke Settings redesign.
 *
 * This re-homes the original `StashConstants.STREAMING_ENGINE_ENABLED` block from
 * the monolithic `SettingsScreen.kt`: the Online/Offline mode picker plus the
 * streaming toggles (cellular, YouTube fallback, antra-only). This is a
 * behavior-preserving relocation + restyle — every control calls the SAME
 * [SettingsViewModel] method the old screen used; no logic is changed.
 */
@Composable
fun SettingsPlaybackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    lyricsViewModel: LyricsSettingsViewModel = hiltViewModel(),
) {
    val lyricsSourcePreference by lyricsViewModel.sourcePreference.collectAsStateWithLifecycle()
    val streamingEnabled by viewModel.streamingEnabled.collectAsStateWithLifecycle()
    val streamOnCellular by viewModel.streamOnCellular.collectAsStateWithLifecycle()
    val forceYouTubeFallback by viewModel.forceYouTubeFallback.collectAsStateWithLifecycle()
    val forceQbdlxOnly by viewModel.forceQbdlxOnly.collectAsStateWithLifecycle()
    // Gates developer instruments out of release builds. Read from the installed
    // app's own flags rather than a module BuildConfig: it is the actual property we
    // care about ("is this a debuggable install"), and it needs no build-file change.
    val context = LocalContext.current
    val isDebuggableBuild = remember(context) {
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val autoplayRadioEnabled by viewModel.autoplayRadioEnabled.collectAsStateWithLifecycle()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.nostop_nav_playback), onBack = onBack, modifier = modifier) {
        if (StashConstants.STREAMING_ENGINE_ENABLED) {
            SettingsSectionLabel(stringResource(R.string.nostop_playback_mode))
            SettingsSegmented(
                options = listOf(stringResource(R.string.nostop_stream_only), stringResource(R.string.nostop_download)),
                selectedIndex = if (streamingEnabled) 0 else 1,
                onSelect = { viewModel.onStreamingToggle(it == 0) },
            )

            SettingsSectionLabel(stringResource(R.string.nostop_streaming))
            SettingsGroupCard(
                rows = buildList {
                    add {
                        SettingsToggleRow(
                            title = stringResource(R.string.nostop_cellular),
                            subtitle = stringResource(R.string.nostop_cellular_desc),
                            checked = streamOnCellular,
                            onCheckedChange = viewModel::onStreamOnCellularToggle,
                        )
                    }
                },
            )
        } else {
            Text(
                text = stringResource(R.string.nostop_stream_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Autoplay: the queue's end seeds a song radio (streams, so it needs a connection).
        SettingsSectionLabel(stringResource(R.string.nostop_autoplay))
        SettingsGroupCard(
            rows = listOf {
                SettingsToggleRow(
                    title = stringResource(R.string.nostop_autoplay_radio),
                    subtitle = stringResource(R.string.nostop_autoplay_radio_desc),
                    checked = autoplayRadioEnabled,
                    onCheckedChange = viewModel::onAutoplayRadioToggle,
                )
            },
        )
        // Crossfade applies to both streamed and downloaded tracks, so it sits
        // outside the streaming-engine gate.
        SettingsSectionLabel(stringResource(R.string.nostop_crossfade))
        SettingsGroupCard(
            rows = buildList {
                add {
                    SettingsToggleRow(
                        title = stringResource(R.string.nostop_crossfade),
                        subtitle = stringResource(R.string.nostop_crossfade_desc),
                        checked = crossfadeEnabled,
                        onCheckedChange = viewModel::onCrossfadeToggle,
                    )
                }
                if (crossfadeEnabled) {
                    add {
                        CrossfadeDurationRow(
                            seconds = (crossfadeDurationMs / 1000L).toInt().coerceIn(1, 12),
                            onSecondsChange = { viewModel.onCrossfadeDurationChange(it * 1000L) },
                        )
                    }
                }
            },
        )

        // Lyrics remain available via the app's own LRC flow. The legacy
        // Apple Music setting is intentionally not exposed as a Sonora provider.
        SettingsSectionLabel(stringResource(R.string.nostop_lyrics))
        Text(
            text = stringResource(R.string.sonora_lyrics_lrc_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SettingsRowPadH, vertical = 8.dp),
        )
    }
}

/** One tappable sleep-timer choice row. */
@Composable
private fun SleepTimerChoiceRow(
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SettingsRowPadH, vertical = SettingsRowPadV),
    )
}

/**
 * Duration slider row for the Crossfade section: 1–12 s in whole-second steps
 * (Material [Slider] `steps` counts internal stops, so 10 → 12 positions).
 * Stateless; the caller owns persistence.
 */
@Composable
private fun CrossfadeDurationRow(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsRowPadH, vertical = SettingsRowPadV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nostop_duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.nostop_duration_seconds, seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onSecondsChange(it.roundToInt()) },
            valueRange = 1f..12f,
            steps = 10,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

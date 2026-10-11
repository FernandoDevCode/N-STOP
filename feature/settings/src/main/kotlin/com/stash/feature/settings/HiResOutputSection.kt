package com.stash.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.data.prefs.HiResOutputMode
import com.stash.core.media.output.SignalQuality
import com.stash.core.ui.components.GlassCard
import com.stash.feature.settings.components.SettingsPickerRow
import com.stash.feature.settings.components.SettingsSectionLabel

/**
 * Audio & Quality › Hi-Res Output.
 *
 * Three honest choices, each saying what it costs:
 *  - **Standard** — Android's shared mixer (usually 48 kHz): what every app gets.
 *  - **Match DAC to each song** — a USB DAC is reopened at the song's sample
 *    rate, so nothing is resampled; EQ, crossfade and notifications still work.
 *  - **Bit-perfect** — the samples reach the DAC unaltered; EQ, normalization
 *    and crossfade step aside, and volume becomes the DAC's job (confirmed
 *    with a warning, since some DACs then play at full level).
 *
 * The live line on top is the same verdict the Now Playing signal-path sheet
 * shows, so the user can see the setting take effect.
 */
@Composable
fun HiResOutputSection(viewModel: HiResOutputViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val verdict by viewModel.signalPath.collectAsStateWithLifecycle()
    val supported = viewModel.isSupported
    var confirmBitPerfect by remember { mutableStateOf(false) }

    SettingsSectionLabel("Hi-Res Output")
    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            val now = verdict
            Text(
                text = when {
                    now == null -> "Play a song to see what it goes through on the way to your ears."
                    else -> "Now: ${now.headline}" + (now.deviceName?.let { " · $it" } ?: "")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (now?.quality == SignalQuality.RESAMPLED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsPickerRow(
                selected = mode == HiResOutputMode.STANDARD,
                title = "Standard",
                subtitle = "Android's shared mixer, like every app. It resamples songs that " +
                    "don't match its own rate (usually 48 kHz).",
                onClick = { viewModel.setMode(HiResOutputMode.STANDARD) },
            )
            val hiResRowModifier = Modifier.alpha(if (supported) 1f else 0.38f)
            SettingsPickerRow(
                selected = mode == HiResOutputMode.MATCH_RATE,
                title = "Match USB DAC to each song",
                subtitle = if (supported) {
                    "Your DAC runs at the song's own sample rate — 44.1, 96, 192 kHz — so nothing " +
                        "is resampled. EQ, crossfade and notifications still work."
                } else {
                    "Needs Android 14 or newer."
                },
                onClick = { if (supported) viewModel.setMode(HiResOutputMode.MATCH_RATE) },
                modifier = hiResRowModifier,
            )
            SettingsPickerRow(
                selected = mode == HiResOutputMode.BIT_PERFECT,
                title = "Bit-perfect USB",
                subtitle = if (supported) {
                    "Every sample reaches your DAC unaltered. EQ, normalization and crossfade are " +
                        "bypassed, and volume is set on your DAC. Falls back to matching the sample " +
                        "rate where your phone doesn't offer it."
                } else {
                    "Needs Android 14 or newer."
                },
                onClick = {
                    if (supported && mode != HiResOutputMode.BIT_PERFECT) confirmBitPerfect = true
                },
                modifier = hiResRowModifier,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "These apply to USB DACs and USB-C dongles. The phone's own headphone jack, " +
                    "speaker and Bluetooth always go through Android's mixer. Bluetooth also " +
                    "re-encodes the audio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmBitPerfect) {
        AlertDialog(
            onDismissRequest = { confirmBitPerfect = false },
            title = { Text("Turn your DAC down first") },
            text = {
                Text(
                    "In bit-perfect mode Android doesn't touch the volume: your DAC sets it. " +
                        "If your DAC has no volume control of its own, the phone's volume keys may " +
                        "stop working and music can play at full level.\n\n" +
                        "Turn the volume down before you press play. EQ, normalization and " +
                        "crossfade are bypassed while bit-perfect is active.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBitPerfect = false
                    viewModel.setMode(HiResOutputMode.BIT_PERFECT)
                }) { Text("Use bit-perfect") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBitPerfect = false }) { Text("Cancel") }
            },
        )
    }
}

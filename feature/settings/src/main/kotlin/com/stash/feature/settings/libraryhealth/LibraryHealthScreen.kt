package com.stash.feature.settings.libraryhealth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.data.db.dao.LibraryHealthBucket
import com.stash.feature.settings.components.SonoraSettingsCard as GlassCard
import com.stash.feature.settings.components.SettingsPageHeader
import com.stash.feature.settings.components.SettingsPageSurface
import com.stash.feature.settings.R
import androidx.compose.ui.res.stringResource
import com.stash.core.ui.components.bottomChromePadding
import com.stash.feature.settings.components.SettingsSectionLabel
import com.stash.core.ui.theme.StashTheme
import com.stash.data.download.lyrics.LyricsFetchStatus

/**
 * Library Health screen — answers "what's actually in my library?" by
 * histograming downloaded tracks across (codec, bitrate-band) buckets read
 * straight from the DB.
 *
 * Reports the codecs and bitrates of existing files, including previously
 * downloaded FLAC. The retired lossless upgrade service must not be offered
 * as a new download or streaming option; only existing local metadata is shown.
 */
@Composable
fun LibraryHealthScreen(
    onNavigateBack: () -> Unit,
    viewModel: LibraryHealthViewModel = hiltViewModel(),
    lyricsViewModel: LyricsFetchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lyricsStatus by lyricsViewModel.status.collectAsStateWithLifecycle()

    SettingsPageSurface {
        Column(Modifier.fillMaxSize()) {
            SettingsPageHeader(
                title = stringResource(R.string.nostop_storage_library_health),
                onBack = onNavigateBack,
            )
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomChromePadding()),
            ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.sonora_health_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SettingsSectionLabel(stringResource(R.string.sonora_health_breakdown))

        if (state.buckets.isEmpty()) {
            EmptyHint()
        } else {
            HistogramCard(buckets = state.buckets)
        }

        Spacer(Modifier.height(20.dp))

        BackfillSection(
            status = state.backfill,
            buckets = state.buckets,
            onRunBackfill = viewModel::runBackfill,
        )

        Spacer(Modifier.height(20.dp))

        if (state.restorableDownloads > 0 || state.restoreQueued != null) {
            RestoreDownloadsSection(
                missing = state.restorableDownloads,
                queued = state.restoreQueued,
                onRestore = viewModel::restoreMissingDownloads,
                onDismiss = viewModel::dismissRestoreResult,
            )
            Spacer(Modifier.height(20.dp))
        }

        VerifyLibrarySection(
            status = state.verification,
            onRunVerification = viewModel::runVerification,
        )

        Spacer(Modifier.height(20.dp))

        QualityInfoRefreshSection(onClick = viewModel::runQualityInfoBackfill)

        Spacer(Modifier.height(20.dp))

        LyricsFetchSection(
            status = lyricsStatus,
            onFetch = lyricsViewModel::fetchLyrics,
        )

        Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    GlassCard {
        Text(
            text = stringResource(R.string.sonora_health_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistogramCard(buckets: List<LibraryHealthBucket>) {
    val total = buckets.sumOf { it.trackCount }.coerceAtLeast(1)

    GlassCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Total summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sonora_health_downloaded_total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            buckets.forEach { bucket ->
                BucketRow(bucket = bucket, totalForPercent = total)
            }
        }
    }
}

@Composable
private fun BucketRow(bucket: LibraryHealthBucket, totalForPercent: Int) {
    val pct = bucket.trackCount.toFloat() / totalForPercent.toFloat()
    val pctLabel = "%.1f%%".format(pct * 100)
    val avgKbpsLabel = if (bucket.avgKbps > 0.0) stringResource(R.string.sonora_health_average_bitrate, bucket.avgKbps.toInt()) else ""
    val formatLabel = if (bucket.format.equals("unknown", ignoreCase = true)) stringResource(R.string.sonora_health_unknown) else humanizeFormat(bucket.format)
    val bitrateBucketLabel = if (bucket.kbpsBucket == "unknown") stringResource(R.string.sonora_health_unknown) else bucket.kbpsBucket

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$formatLabel $bitrateBucketLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${bucket.trackCount} · $pctLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Percentage bar — solid fill, no shimmer.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(StashTheme.extendedColors.elevatedSurface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = pct)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        if (avgKbpsLabel.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = avgKbpsLabel.trimStart(' ', '·', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackfillSection(
    status: BackfillStatus,
    buckets: List<LibraryHealthBucket>,
    onRunBackfill: () -> Unit,
) {
    // Only worth showing the backfill button if there's at least one
    // legacy "unknown" row left to clean up.
    val unknownCount = buckets
        .filter { it.format == "opus" && it.kbpsBucket == "unknown" }
        .sumOf { it.trackCount }

    if (unknownCount == 0 && status is BackfillStatus.Idle) return

    SettingsSectionLabel(stringResource(R.string.sonora_health_metadata_scan))

    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when (status) {
                BackfillStatus.Idle -> {
                    Text(
                        text = stringResource(R.string.sonora_health_scan_intro, unknownCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRunBackfill) {
                        Text(stringResource(R.string.sonora_health_scan_files))
                    }
                }
                is BackfillStatus.Running -> {
                    val total = status.total.coerceAtLeast(1)
                    Text(
                        text = stringResource(R.string.sonora_health_scanning, status.processed, status.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { status.processed.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is BackfillStatus.Done -> {
                    Text(
                        text = stringResource(R.string.sonora_health_scan_complete, status.processed, status.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (unknownCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.sonora_health_scan_unreadable, unknownCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRunBackfill) {
                            Text(stringResource(R.string.sonora_health_retry_scan))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Offered only when the library remembers downloads this device no longer
 * has — restoring a backup onto a fresh install is the case that produces
 * them in bulk, since the archive carries the library and not the audio.
 */
@Composable
private fun RestoreDownloadsSection(
    missing: Int,
    queued: Int?,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSectionLabel(stringResource(R.string.sonora_health_missing_downloads))

    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (queued != null) {
                Text(
                    text = stringResource(
                        if (queued == 1) R.string.sonora_health_restore_queued_one else R.string.sonora_health_restore_queued_many,
                        queued,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.sonora_health_done)) }
            } else {
                Text(
                    text = stringResource(
                        if (missing == 1) R.string.sonora_health_restore_missing_one else R.string.sonora_health_restore_missing_many,
                        missing,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRestore) {
                    Text(stringResource(R.string.sonora_health_download_again, missing))
                }
            }
        }
    }
}

@Composable
private fun VerifyLibrarySection(
    status: LibraryVerificationStatus,
    onRunVerification: () -> Unit,
) {
    SettingsSectionLabel(stringResource(R.string.sonora_health_verify_title))

    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when (status) {
                LibraryVerificationStatus.Idle -> {
                    Text(
                        text = stringResource(R.string.sonora_health_verify_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRunVerification) {
                        Text(stringResource(R.string.sonora_health_verify))
                    }
                }
                is LibraryVerificationStatus.Running -> {
                    val total = status.total.coerceAtLeast(1)
                    Text(
                        text = stringResource(R.string.sonora_health_verifying, status.step, status.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { status.step.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is LibraryVerificationStatus.Done -> {
                    val r = status.reconciliation
                    val a = status.adoption
                    Text(
                        text = stringResource(R.string.sonora_health_verify_complete, r.filesMissing, r.orphansSwept),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (a.adopted > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.sonora_health_adopted, a.adopted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else if (a.scanned > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.sonora_health_checked, a.scanned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRunVerification) {
                        Text(stringResource(R.string.sonora_health_verify_again))
                    }
                }
                is LibraryVerificationStatus.Failed -> {
                    Text(
                        text = stringResource(R.string.sonora_health_verify_failed, status.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRunVerification) {
                        Text(stringResource(R.string.sonora_health_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityInfoRefreshSection(onClick: () -> Unit) {
    val context = LocalContext.current
    val refreshMessage = stringResource(R.string.sonora_health_refresh_started)
    SettingsSectionLabel(stringResource(R.string.sonora_health_quality_info))
    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.sonora_health_refresh_quality),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sonora_health_refresh_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onClick()
                    android.widget.Toast.makeText(
                        context,
                        refreshMessage,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text(stringResource(R.string.sonora_health_refresh))
            }
        }
    }
}

@Composable
private fun LyricsFetchSection(
    status: LyricsFetchStatus,
    onFetch: () -> Unit,
) {
    SettingsSectionLabel(stringResource(R.string.sonora_health_lyrics_title))
    GlassCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when (status) {
                LyricsFetchStatus.Idle, LyricsFetchStatus.Queued -> {
                    Text(
                        text = stringResource(R.string.sonora_health_fetch_lyrics),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (status == LyricsFetchStatus.Queued) {
                            stringResource(R.string.sonora_health_lyrics_queued)
                        } else {
                            stringResource(R.string.sonora_health_lyrics_intro)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onFetch) { Text(stringResource(R.string.sonora_health_fetch_lyrics)) }
                }
                is LyricsFetchStatus.Running -> {
                    Text(
                        text = if (status.total > 0) {
                            stringResource(R.string.sonora_health_lyrics_progress, status.done, status.total)
                        } else {
                            stringResource(R.string.sonora_health_lyrics_fetching)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (status.total > 0) {
                        LinearProgressIndicator(
                            progress = { status.done.toFloat() / status.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                is LyricsFetchStatus.Done -> {
                    val updated = status.fetched + status.upgraded
                    Text(
                        text = if (updated > 0) {
                            stringResource(R.string.sonora_health_lyrics_updated, updated)
                        } else {
                            stringResource(R.string.sonora_health_lyrics_no_updates)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (status.notFound > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.sonora_health_lyrics_not_found, status.notFound),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (status.bailed) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.sonora_health_lyrics_stopped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onFetch) { Text(stringResource(R.string.sonora_health_lyrics_fetch_again)) }
                }
            }
        }
    }
}

private fun humanizeFormat(format: String): String = when (format.lowercase()) {
    "aac" -> "AAC"
    "opus" -> "Opus"
    "mp3" -> "MP3"
    "flac" -> "FLAC"
    "vorbis" -> "Vorbis"
    "unknown" -> "Unknown"
    else -> format
}

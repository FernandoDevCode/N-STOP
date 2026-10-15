package com.stash.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.core.ui.components.GlassCard
import com.stash.core.ui.theme.StashTheme
import com.stash.data.download.lossless.LosslessQualityTier
import com.stash.feature.settings.components.AudioQualityPicker
import com.stash.feature.settings.components.BetaPill
import com.stash.feature.settings.components.LosslessRoutingStatus
import com.stash.feature.settings.components.SettingsNavRow
import com.stash.feature.settings.components.SettingsPickerRow
import com.stash.feature.settings.components.SettingsScaffold
import com.stash.feature.settings.components.SettingsSectionLabel
import com.stash.feature.settings.components.SettingsToggleRow

/**
 * The Audio & Quality spoke of the hub-and-spoke Settings redesign.
 *
 * This re-homes the original "Audio Quality" + "Lossless audio card" block
 * from the monolithic `SettingsScreen.kt`: the download-tier picker (shown
 * only when lossless is off), the lossless toggle, the routing status,
 * the lossless-quality picker, the YouTube-fallback
 * expander, and the Advanced (captcha cookie + reset) expander — plus the
 * Equalizer nav. This is a behavior-preserving relocation + restyle: every
 * control calls the SAME [SettingsViewModel] method the old screen used; no
 * logic is changed. The legacy `bringIntoViewRequester` (deep-link scroll
 * affordance) is dropped — it is not needed on a dedicated category screen.
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
    val qbdlxExpired by viewModel.qbdlxExpired.collectAsStateWithLifecycle()
    val qobuzConnectedEmail by viewModel.qobuzConnectedEmail.collectAsStateWithLifecycle()
    val qobuzHasLogin by viewModel.qobuzHasLogin.collectAsStateWithLifecycle()
    val losslessRouting by viewModel.losslessRouting.collectAsStateWithLifecycle()
    val qobuzConnecting by viewModel.qobuzConnecting.collectAsStateWithLifecycle()
    val qobuzConnectError by viewModel.qobuzConnectError.collectAsStateWithLifecycle()
    val customEndpoint by viewModel.customEndpoint.collectAsStateWithLifecycle()
    val customEndpointError by viewModel.customEndpointError.collectAsStateWithLifecycle()
    val customEndpointTest by viewModel.customEndpointTest.collectAsStateWithLifecycle()
    val flacViewModel: FlacUpgradeViewModel = hiltViewModel()
    val autoFlacUpgrade by flacViewModel.autoEnabled.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.nostop_nav_audio), onBack = onBack, modifier = modifier) {
        // (a) Download tier — only when lossless OFF. The standalone yt-dlp
        // tier picker governs downloads when lossless routing is disabled.
        if (!uiState.losslessEnabled) {
            SettingsSectionLabel(stringResource(R.string.nostop_quality_title))
            GlassCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                ) {
                    Text(
                        text = stringResource(R.string.nostop_quality_download),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.nostop_quality_bitrate_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AudioQualityPicker(
                        selected = uiState.audioQuality,
                        onSelected = viewModel::onQualityChanged,
                    )
                    // Save Data lives here too: with lossless off it means "the lowest
                    // JioSaavn and YouTube audio quality", and the toggle must not vanish
                    // with the lossless card — that is exactly the user it is for.
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = stringResource(R.string.nostop_quality_save_data),
                        subtitle = stringResource(R.string.nostop_quality_save_youtube_hint),
                        checked = uiState.streamingSaveData,
                        onCheckedChange = viewModel::onStreamingSaveDataChanged,
                        titleTrailing = { BetaPill() },
                    )
                }
            }
        }

        // (b) Lossless card.
        SettingsSectionLabel(stringResource(R.string.nostop_quality_lossless))
        GlassCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = stringResource(R.string.nostop_quality_lossless),
                    // Three-way, because keying "FLAC routing active" on the toggle
                    // alone asserted it for every user with nothing configured — the
                    // day-one state — twenty lines above three "not connected" rows.
                    subtitle = when {
                        !uiState.losslessEnabled ->
                            stringResource(R.string.nostop_quality_lossless_off)
                        qbdlxExpired ->
                            stringResource(R.string.nostop_quality_lossless_missing)
                        else -> stringResource(R.string.nostop_quality_lossless_on)
                    },
                    checked = uiState.losslessEnabled,
                    onCheckedChange = viewModel::onLosslessEnabledChanged,
                )

                AnimatedVisibility(
                    visible = uiState.losslessEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // ROUTING block — one row per configured lossless path,
                        // built in LosslessAvailability so this list and the
                        // resolver read the same predicates.
                        LosslessRoutingStatus(rows = losslessRouting)

                        // Direct Qobuz — direct www.qobuz.com Hi-Res FLAC, the
                        // primary lossless source. The badge shows when no
                        // lossless path is configured (`LosslessAvailability
                        // .qbdlxEnabled`). No per-source toggle: a stale saved `false` with
                        // no UI to flip it back would kill lossless silently.
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Same gate as the card subtitle.
                            if (qbdlxExpired) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.nostop_quality_qobuz_missing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            // -- Bring your own Qobuz account -------------
                            // The user's own paid subscription: the one
                            // credential guaranteed to serve FLAC, since we
                            // mint + sign its token under a matching app_id.
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.nostop_quality_qobuz_account),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            // Keyed on hasLogin, NOT on the email: a token migrated
                            // from the old paste field has no email, and keying on
                            // that showed its owner a sign-in form with no way to
                            // remove the token when it went dead.
                            if (qobuzHasLogin) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = qobuzConnectedEmail?.let { stringResource(R.string.nostop_quality_connected_as, it) }
                                        ?: stringResource(R.string.nostop_quality_connected_token),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TextButton(onClick = viewModel::onDisconnectQobuz) {
                                    Text(stringResource(R.string.nostop_quality_disconnect))
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.nostop_quality_qobuz_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                var qobuzEmail by remember { mutableStateOf("") }
                                var qobuzPassword by remember { mutableStateOf("") }
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = qobuzEmail,
                                    onValueChange = { qobuzEmail = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.nostop_quality_email)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = qobuzPassword,
                                    onValueChange = { qobuzPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.nostop_quality_password)) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                )
                                qobuzConnectError?.let { err ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { viewModel.onConnectQobuz(qobuzEmail, qobuzPassword) },
                                    enabled = !qobuzConnecting,
                                ) {
                                    if (qobuzConnecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.nostop_quality_connecting))
                                    } else {
                                        Text(stringResource(R.string.nostop_quality_connect))
                                    }
                                }
                            }
                        }

                        // -- Download quality picker --------------------------
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.nostop_quality_download),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Column(modifier = Modifier.selectableGroup()) {
                            // Order top-down: MAX → HI_RES → CD (best-quality first).
                            listOf(
                                LosslessQualityTier.MAX,
                                LosslessQualityTier.HI_RES,
                                LosslessQualityTier.CD,
                            ).forEach { tier ->
                                SettingsPickerRow(
                                    selected = uiState.losslessQualityTier == tier,
                                    title = tier.displayLabel,
                                    subtitle = tier.sizeHint,
                                    onClick = { viewModel.onLosslessQualityTierChanged(tier) },
                                )
                            }
                        }

                        // -- Auto-upgrade to FLAC after sync ------------------
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow(
                            title = stringResource(R.string.nostop_quality_auto_flac),
                            subtitle = stringResource(R.string.nostop_quality_auto_flac_hint),
                            checked = autoFlacUpgrade,
                            onCheckedChange = flacViewModel::setAutoEnabled,
                        )

                        // -- Streaming quality block --------------------------
                        // Per-network tier for *streaming* playback (distinct
                        // from the download tier above). Save Data is the master
                        // override: when on, both pickers are dimmed + inert and
                        // policy forces CD — the lowest lossless tier — on every network.
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingsSectionLabel(stringResource(R.string.nostop_streaming))
                        GlassCard {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val saveData = uiState.streamingSaveData
                                val pickerAlpha = if (saveData) 0.4f else 1f

                                Text(
                                    text = stringResource(R.string.nostop_quality_wifi),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Column(
                                    modifier = Modifier
                                        .alpha(pickerAlpha)
                                        .selectableGroup(),
                                ) {
                                    listOf(
                                        LosslessQualityTier.MAX,
                                        LosslessQualityTier.HI_RES,
                                        LosslessQualityTier.CD,
                                    ).forEach { tier ->
                                        SettingsPickerRow(
                                            selected = uiState.streamingWifiTier == tier,
                                            title = tier.displayLabel,
                                            subtitle = tier.sizeHint,
                                            onClick = {
                                                if (!saveData) viewModel.onStreamingWifiTierChanged(tier)
                                            },
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.nostop_quality_cellular),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Column(
                                    modifier = Modifier
                                        .alpha(pickerAlpha)
                                        .selectableGroup(),
                                ) {
                                    listOf(
                                        LosslessQualityTier.MAX,
                                        LosslessQualityTier.HI_RES,
                                        LosslessQualityTier.CD,
                                    ).forEach { tier ->
                                        SettingsPickerRow(
                                            selected = uiState.streamingCellularTier == tier,
                                            title = tier.displayLabel,
                                            subtitle = tier.sizeHint,
                                            onClick = {
                                                if (!saveData) viewModel.onStreamingCellularTierChanged(tier)
                                            },
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                SettingsToggleRow(
                                    title = stringResource(R.string.nostop_quality_save_data),
                                    // Lossless stays lossless: Save Data is the LOWEST lossless
                                    // tier, not a drop to lossy. Less than FLAC is the lossless
                                    // toggle's job, and then this same switch trims JioSaavn and YouTube.
                                    subtitle = stringResource(R.string.nostop_quality_save_lossless_hint),
                                    checked = uiState.streamingSaveData,
                                    onCheckedChange = viewModel::onStreamingSaveDataChanged,
                                    titleTrailing = { BetaPill() },
                                )
                            }
                        }

                        // -- YouTube fallback expander row (v0.9.17) -----------
                        // Hosts the relocated yt-dlp tier picker plus the
                        // master fallback toggle. Re-keyed on the losslessEnabled
                        // flip so it collapses cleanly when toggled.
                        var fallbackExpanded by remember(uiState.losslessEnabled) { mutableStateOf(false) }
                        val fallbackChevronRotation by animateFloatAsState(
                            targetValue = if (fallbackExpanded) 90f else 0f,
                            label = "fallback-chevron",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fallbackExpanded = !fallbackExpanded }
                                .semantics {
                                    role = Role.Button
                                    stateDescription = if (fallbackExpanded) "expanded" else "collapsed"
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer(rotationZ = fallbackChevronRotation),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.nostop_quality_fallback),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (uiState.youtubeFallbackEnabled) stringResource(R.string.nostop_quality_on) else stringResource(R.string.nostop_quality_off),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        AnimatedVisibility(
                            visible = fallbackExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.stash.core.ui.components.StashSwitch(
                                        checked = uiState.youtubeFallbackEnabled,
                                        onCheckedChange = viewModel::onYoutubeFallbackChanged,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.nostop_quality_fallback_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                if (uiState.youtubeFallbackEnabled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.nostop_quality_bitrate_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AudioQualityPicker(
                                        selected = uiState.audioQuality,
                                        onSelected = viewModel::onQualityChanged,
                                    )
                                }
                            }
                        }

                        // -- Advanced expander row (chevron + label) -----------
                        var advancedExpanded by remember(uiState.losslessEnabled) { mutableStateOf(false) }
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (advancedExpanded) 90f else 0f,
                            label = "advancedChevron",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { advancedExpanded = !advancedExpanded }
                                .semantics {
                                    role = Role.Button
                                    // Spec §Accessibility: announce collapsed/expanded
                                    // state to screen readers.
                                    stateDescription = if (advancedExpanded) "expanded" else "collapsed"
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null, // parent Row carries role + stateDescription + label
                                modifier = Modifier.graphicsLayer(rotationZ = chevronRotation),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.nostop_quality_advanced),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        AnimatedVisibility(
                            visible = advancedExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // -- Custom lossless endpoint -----------------
                                // Outranks every relay from runtime config in
                                // QbdlxFileUrlRouter, so a typo here silently
                                // costs the user their first lossless attempt —
                                // hence Test, and hence commit-on-Done.
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.nostop_quality_relay_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // The VM's committed value is the seed AND the
                                // comparison for commit(): re-keying on it means the
                                // field snaps to what was actually stored WHEN THE
                                // STORED VALUE CHANGES — a trailing slash normalises
                                // to the same string, so that edit is swallowed by
                                // distinctUntilChanged and the slash stays on screen.
                                // Saveable, not remember: MainActivity declares no
                                // configChanges, so a rotation mid-URL threw the
                                // half-typed draft away. A relay base is not a secret.
                                var endpointDraft by rememberSaveable(customEndpoint) {
                                    mutableStateOf(customEndpoint.orEmpty())
                                }
                                // The error lives in the VM and outlives this subtree:
                                // collapsing Advanced and reopening re-seeds the draft
                                // from storage but left the rejection on screen — an
                                // empty field painted red. Clear it on (re)entry.
                                LaunchedEffect(Unit) { viewModel.onCustomEndpointEdited() }
                                var endpointWasFocused by remember { mutableStateOf(false) }
                                val focusManager = LocalFocusManager.current
                                val commitEndpoint = {
                                    val draft = endpointDraft.trim()
                                    // Per keystroke this would persist `https://re` as a
                                    // base and route every resolve at it. The guard skips
                                    // the common no-op; it does NOT make this idempotent,
                                    // since the Done path commits and then clears focus
                                    // before `customEndpoint` has re-emitted. The repeat
                                    // is an identical write, collapsed downstream.
                                    if (draft != customEndpoint.orEmpty()) {
                                        viewModel.onCustomEndpointCommitted(draft)
                                    }
                                }
                                OutlinedTextField(
                                    value = endpointDraft,
                                    onValueChange = {
                                        endpointDraft = it
                                        // Otherwise the red "Must be an https:// URL"
                                        // outlives the text it judged — including onto
                                        // an empty field after a collapse/reopen.
                                        viewModel.onCustomEndpointEdited()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // `endpointWasFocused` gates the initial unfocused
                                        // callback, which would otherwise commit "".
                                        .onFocusChanged { state ->
                                            if (endpointWasFocused && !state.isFocused) commitEndpoint()
                                            endpointWasFocused = state.isFocused
                                        },
                                    label = { Text(stringResource(R.string.nostop_quality_custom_endpoint)) },
                                    singleLine = true,
                                    isError = customEndpointError != null,
                                    placeholder = { Text("https://…") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            commitEndpoint()
                                            focusManager.clearFocus()
                                        },
                                    ),
                                )
                                customEndpointError?.let { err ->
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val testing =
                                        customEndpointTest == SettingsViewModel.EndpointTestState.TESTING
                                    TextButton(
                                        onClick = viewModel::onTestCustomEndpoint,
                                        enabled = customEndpoint != null && !testing,
                                    ) {
                                        Text(stringResource(R.string.nostop_quality_test))
                                    }
                                    // Reachability, not health: any HTTP reply counts.
                                    when (customEndpointTest) {
                                        SettingsViewModel.EndpointTestState.TESTING -> Text(
                                            text = stringResource(R.string.nostop_quality_testing),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        SettingsViewModel.EndpointTestState.REACHABLE -> Text(
                                            text = stringResource(R.string.nostop_quality_reachable),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StashTheme.extendedColors.success,
                                        )
                                        SettingsViewModel.EndpointTestState.UNREACHABLE -> Text(
                                            text = stringResource(R.string.nostop_quality_unreachable),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        SettingsViewModel.EndpointTestState.IDLE -> Unit
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.nostop_quality_captcha_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = uiState.squidWtfCaptchaCookie,
                                    onValueChange = viewModel::onSquidWtfCaptchaCookieChanged,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.nostop_quality_captcha_value)) },
                                    singleLine = true,
                                    placeholder = { Text("e.g. 1777687404951") },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = viewModel::onResetLosslessRateLimiter,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.nostop_quality_reset),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // (c) Hi-Res output: how the audio leaves the phone (USB DAC sample-rate
        // matching / bit-perfect on Android 14+), with a live signal-path line.
        HiResOutputSection()

        // (d) Effects.
        SettingsSectionLabel(stringResource(R.string.nostop_quality_effects))
        SettingsNavRow(
            title = stringResource(R.string.nostop_quality_equalizer),
            onClick = onNavigateToEqualizer,
        )
    }
}

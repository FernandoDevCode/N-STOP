package com.stash.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.feature.settings.components.SonoraSettingsCard as GlassCard
import com.stash.feature.settings.components.AccountConnectionCard
import com.stash.feature.settings.components.SettingsScaffold
import com.stash.feature.settings.components.SettingsSectionLabel

/**
 * The Accounts & Sync spoke of the hub-and-spoke Settings redesign.
 *
 * Behavior-preserving relocation of the original "Accounts" section from the
 * monolithic [SettingsScreen]: the Spotify and YouTube Music connection cards
 * (each with their inline auto-save / history-sync extras), the Last.fm card,
 * and the like-mirroring opt-ins (plus the mirror-warning ack dialog). Every
 * control calls the SAME [SettingsViewModel] method the old screen used; no
 * logic is changed. The `bringIntoViewRequester` on the Last.fm card (a
 * deep-link affordance) is intentionally dropped here.
 */
@Composable
fun SettingsAccountsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val extendedColors = com.stash.core.ui.theme.StashTheme.extendedColors
    // The last sync's verdict on each stored sign-in: an expired card leads with
    // Reconnect (the Sync tab's "Re-authenticate" lands here).
    val authExpiry by viewModel.authExpiry.collectAsStateWithLifecycle()

    // The sign-in flags live in the Settings-wide ViewModel, which outlives this
    // screen: leaving Accounts with a sign-in open (any route Back doesn't
    // cover, e.g. switching tabs) would otherwise reopen it on the next visit.
    // Not on rotation, which disposes this too but should keep the sign-in up.
    val activity = androidx.activity.compose.LocalActivity.current
    androidx.compose.runtime.DisposableEffect(viewModel) {
        onDispose {
            if (activity?.isChangingConfigurations != true) viewModel.onWebLoginsClosed()
        }
    }

    // Spotify WebView login (full-screen overlay). Replaces the screen content
    // while active — the connect flow on the Spotify card flips this flag.
    if (uiState.showSpotifyWebLogin) {
        com.stash.feature.settings.components.SpotifyLoginWebView(
            onCookieExtracted = viewModel::onSpotifyWebLoginCookieExtracted,
            onDismiss = viewModel::onDismissSpotifyWebLogin,
            onManualFallback = viewModel::onConnectSpotifyManual,
        )
        return
    }

    // YouTube Music WebView login (full-screen overlay).
    if (uiState.showYouTubeWebLogin) {
        com.stash.feature.settings.components.YouTubeLoginWebView(
            onCookieExtracted = viewModel::onYouTubeWebLoginCookieExtracted,
            onDismiss = viewModel::onDismissYouTubeWebLogin,
            onManualFallback = viewModel::onConnectYouTubeManual,
        )
        return
    }

    // Spotify sp_dc cookie input dialog (manual fallback).
    if (uiState.showSpotifyCookieDialog) {
        com.stash.feature.settings.components.SpotifyCookieDialog(
            isValidating = uiState.isSpotifyCookieValidating,
            errorMessage = uiState.spotifyCookieError,
            onConnect = { cookie, username -> viewModel.onConnectSpotifyWithCookie(cookie, username) },
            onDismiss = viewModel::onDismissSpotifyCookieDialog,
        )
    }

    // YouTube Music cookie input dialog.
    if (uiState.showYouTubeCookieDialog) {
        com.stash.feature.settings.components.YouTubeCookieDialog(
            isValidating = uiState.isYouTubeCookieValidating,
            errorMessage = uiState.youTubeCookieError,
            onConnect = viewModel::onConnectYouTubeWithCookie,
            onDismiss = viewModel::onDismissYouTubeCookieDialog,
        )
    }

    // YouTube error dialog (missing credentials, network failure, etc.).
    if (uiState.youTubeError != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissYouTubeError,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    text = "YouTube Music",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = uiState.youTubeError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissYouTubeError) {
                    Text("OK")
                }
            },
        )
    }

    SettingsScaffold(title = stringResource(R.string.nostop_nav_accounts), onBack = onBack, modifier = modifier) {
        // Like-mirroring opt-in ack. Rendered at the top of the scaffold so it
        // overlays the content. Confirming writes the pref; dismissing leaves
        // mirroring off (no writes without this ack).
        uiState.pendingMirrorWarning?.let { dest ->
            com.stash.feature.settings.components.LikeMirrorWarningDialog(
                serviceName = if (dest == com.stash.core.data.social.Destination.SPOTIFY) "Spotify" else "YouTube Music",
                onConfirm = viewModel::onMirrorWarningConfirmed,
                onDismiss = viewModel::onMirrorWarningDismissed,
            )
        }

        SettingsSectionLabel(stringResource(R.string.nostop_accounts_connections))

        // Disconnecting a source also drops its queued downloads, so each asks
        // first (as Discord's connect does). Saveable, like showDiscordRisk, so
        // a rotation keeps the question up.
        var confirmSpotifyDisconnect by rememberSaveable { mutableStateOf(false) }
        var confirmYouTubeDisconnect by rememberSaveable { mutableStateOf(false) }
        if (confirmSpotifyDisconnect) {
            com.stash.feature.settings.components.DisconnectAccountDialog(
                serviceName = "Spotify",
                onConfirm = {
                    confirmSpotifyDisconnect = false
                    // Also sign the WebView out, or the next Connect re-submits the old sp_dc.
                    com.stash.feature.settings.components.clearSpotifyWebSession()
                    viewModel.onDisconnectSpotify()
                },
                onDismiss = { confirmSpotifyDisconnect = false },
            )
        }
        if (confirmYouTubeDisconnect) {
            com.stash.feature.settings.components.DisconnectAccountDialog(
                serviceName = "YouTube Music",
                onConfirm = {
                    confirmYouTubeDisconnect = false
                    // Also sign the WebView out of Google, so the account isn't left behind in it.
                    com.stash.feature.settings.components.clearYouTubeWebSession()
                    viewModel.onDisconnectYouTube()
                },
                onDismiss = { confirmYouTubeDisconnect = false },
            )
        }

        AccountConnectionCard(
            serviceName = "Spotify",
            icon = Icons.Rounded.MusicNote,
            accentColor = extendedColors.spotifyGreen,
            authState = uiState.spotifyAuthState,
            expired = authExpiry.spotifyExpired,
            onConnect = viewModel::onConnectSpotify,
            onDisconnect = { confirmSpotifyDisconnect = true },
            extraContent = {
                com.stash.feature.settings.components.SpotifyAutoSaveSection(
                    enabled = uiState.autoSaveEnabled,
                    threshold = uiState.autoSaveThreshold,
                    autoSavedCountLast7Days = uiState.autoSavedCountLast7Days,
                    spotifyConnected = uiState.spotifyAuthState is com.stash.core.auth.model.AuthState.Connected,
                    onToggle = viewModel::onAutoSaveEnabledChanged,
                    onThresholdChanged = viewModel::onAutoSaveThresholdChanged,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
        )

        AccountConnectionCard(
            serviceName = "YouTube Music",
            icon = Icons.Rounded.PlayCircle,
            accentColor = extendedColors.youtubeRed,
            authState = uiState.youTubeAuthState,
            expired = authExpiry.youtubeExpired,
            onConnect = viewModel::onConnectYouTube,
            onDisconnect = { confirmYouTubeDisconnect = true },
            extraContent = {
                com.stash.feature.settings.components.YouTubeHistorySyncSection(
                    enabled = uiState.ytHistoryEnabled,
                    health = uiState.ytHistoryHealth,
                    pendingCount = uiState.ytPendingCount,
                    ytConnected = uiState.youTubeAuthState is com.stash.core.auth.model.AuthState.Connected,
                    onToggle = viewModel::onYouTubeHistoryEnabledChanged,
                    onRetry = viewModel::onRetryYouTubeHistory,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
        )

        SettingsSectionLabel(stringResource(R.string.nostop_accounts_mirror_likes), beta = true)

        GlassCard {
            val mirrorLastFm by viewModel.mirrorLikesLastFm.collectAsStateWithLifecycle()
            com.stash.feature.settings.components.LikeMirrorSection(
                spotifyEnabled = uiState.mirrorLikesSpotify,
                ytMusicEnabled = uiState.mirrorLikesYtMusic,
                lastFmEnabled = mirrorLastFm,
                spotifyConnected = uiState.spotifyAuthState is com.stash.core.auth.model.AuthState.Connected,
                ytConnected = uiState.youTubeAuthState is com.stash.core.auth.model.AuthState.Connected,
                lastFmConnected = uiState.lastFmState is LastFmAuthState.Connected,
                onSpotifyToggle = { viewModel.onMirrorToggleRequested(com.stash.core.data.social.Destination.SPOTIFY, it) },
                onYtMusicToggle = { viewModel.onMirrorToggleRequested(com.stash.core.data.social.Destination.YT_MUSIC, it) },
                onLastFmToggle = { viewModel.onMirrorToggleRequested(com.stash.core.data.social.Destination.LAST_FM, it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

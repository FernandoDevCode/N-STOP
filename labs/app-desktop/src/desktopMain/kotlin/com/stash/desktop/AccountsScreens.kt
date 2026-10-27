package com.stash.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stash.core.auth.model.*
import com.stash.core.ui.theme.*
import com.stash.feature.settings.components.*
import java.awt.Desktop
import java.net.URI

@Composable
internal fun DesktopAccountsPage(controller: AppController, onBack: () -> Unit, onSync: () -> Unit) {
    val accounts = controller.accounts
    val spotify by accounts.tokens.spotifyAuthState.collectAsState()
    val youtube by accounts.tokens.youTubeAuthState.collectAsState()
    val discord by accounts.tokens.discordAuthState.collectAsState()
    val lastfm by accounts.lastFmState.collectAsState()
    val ready by accounts.ready.collectAsState()
    val busy by accounts.busy.collectAsState()
    val error by accounts.error.collectAsState()
    val lastfmConfigured by accounts.lastFmConfigured.collectAsState()
    val authUrl by accounts.lastFmAuthorizationUrl.collectAsState()
    val unreadable by accounts.vault.unreadableServices.collectAsState()
    val lastFmBlocked by accounts.lastFmBlocked.collectAsState()
    var dialog by remember { mutableStateOf<AuthService?>(null) }
    var credential by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var lastFmSetup by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    val enabled = ready && busy == null
    ScrollPage("Contas", onBack) {
        Text("Conecte seus serviços para sincronizar esta biblioteca.")
        if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (busy != null) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(busy!!) }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        unreadable.forEach { service ->
            OutlinedButton(onClick = { accounts.disconnect(service) }, enabled = enabled) {
                Text("Remover credencial inacessível · ${when (service) { AuthService.SPOTIFY -> "Spotify"; AuthService.YOUTUBE_MUSIC -> "YouTube Music"; AuthService.DISCORD -> "Discord" }}")
            }
        }
        if (lastFmBlocked) OutlinedButton(onClick = accounts::disconnectLastFm, enabled = enabled) { Text("Remover sessão Last.fm inacessível") }
        AccountConnectionCard("Spotify", Icons.Default.MusicNote, StashSpotifyGreen, spotify,
            onConnect = { dialog = AuthService.SPOTIFY }, onDisconnect = { accounts.disconnect(AuthService.SPOTIFY) }, enabled = enabled && AuthService.SPOTIFY !in unreadable)
        AccountConnectionCard("YouTube Music", Icons.Default.PlayCircle, StashYouTubeRed, youtube,
            onConnect = { dialog = AuthService.YOUTUBE_MUSIC }, onDisconnect = { accounts.disconnect(AuthService.YOUTUBE_MUSIC) }, enabled = enabled && AuthService.YOUTUBE_MUSIC !in unreadable)
        AccountConnectionCard("Last.fm", Icons.Default.GraphicEq, StashLastfmRed, lastfm,
            onConnect = { if (lastfmConfigured) accounts.beginLastFm() else lastFmSetup = true },
            onDisconnect = accounts::disconnectLastFm, enabled = enabled && !lastFmBlocked)
        if (authUrl != null) {
            Text("Autorize o NØNSTOP no Last.fm e depois conclua a conexão aqui.")
            OutlinedButton(onClick = { openAccountBrowser(authUrl!!) }, enabled = enabled) { Text("Abrir autorização Last.fm") }
            Button(onClick = accounts::finishLastFm, enabled = enabled) { Text("Concluir conexão Last.fm") }
        }
        if (lastFmSetup) {
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("Chave da API Last.fm") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(apiSecret, { apiSecret = it }, label = { Text("Segredo da API Last.fm") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(onClick = {
                accounts.beginLastFm(apiKey, apiSecret); apiKey = ""; apiSecret = ""; lastFmSetup = false
            }, enabled = enabled && apiKey.isNotBlank() && apiSecret.isNotBlank()) { Text("Iniciar autorização Last.fm") }
        }
        Text("Last.fm: a conexão fica disponível nesta etapa; envio de scrobbles e histórico ainda não foi migrado.", style = MaterialTheme.typography.bodySmall)
        AccountConnectionCard("Discord", Icons.Default.Forum, Color(0xFF5865F2), discord,
            onConnect = { dialog = AuthService.DISCORD }, onDisconnect = { accounts.disconnect(AuthService.DISCORD) }, enabled = enabled && AuthService.DISCORD !in unreadable)
        Text("Discord: conectar a conta ainda não ativa Rich Presence no Desktop.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onSync, enabled = enabled) { Text("Abrir sincronização") }
    }
    dialog?.let { service ->
        val name = when (service) { AuthService.SPOTIFY -> "Spotify"; AuthService.YOUTUBE_MUSIC -> "YouTube Music"; AuthService.DISCORD -> "Discord" }
        AlertDialog(onDismissRequest = { dialog = null; credential = ""; username = "" },
            title = { Text("Conectar $name") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Entre na sua conta no navegador e use a credencial da sua própria sessão para conectar este app.")
                    OutlinedButton(onClick = { openAccountBrowser(when (service) {
                        AuthService.SPOTIFY -> "https://open.spotify.com/"
                        AuthService.YOUTUBE_MUSIC -> "https://music.youtube.com/"
                        AuthService.DISCORD -> "https://discord.com/app"
                    }) }) { Text("Abrir $name no navegador") }
                    Text(when (service) {
                        AuthService.SPOTIFY -> "No navegador, abra as ferramentas de desenvolvedor → Application/Storage → Cookies de open.spotify.com e copie o valor de sp_dc."
                        AuthService.YOUTUBE_MUSIC -> "No navegador, abra as ferramentas de desenvolvedor → Network, recarregue music.youtube.com e copie o cabeçalho Cookie de uma requisição autenticada ao YouTube."
                        AuthService.DISCORD -> "Este modo avançado usa o token da sessão da sua própria conta Discord, como a opção manual do app Android. Não é um token de bot."
                    }, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(credential, { credential = it }, label = { Text(when (service) {
                        AuthService.SPOTIFY -> "Cookie sp_dc"
                        AuthService.YOUTUBE_MUSIC -> "Cabeçalho Cookie"
                        AuthService.DISCORD -> "Token da sessão"
                    }) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    if (service == AuthService.SPOTIFY) OutlinedTextField(username, { username = it }, label = { Text("Usuário Spotify (opcional)") }, singleLine = true)
                }
            }, confirmButton = { TextButton(onClick = {
                accounts.connect(service, credential, username); credential = ""; username = ""; dialog = null
            }, enabled = credential.isNotBlank() && enabled) { Text("Conectar") } },
            dismissButton = { TextButton(onClick = { dialog = null; credential = ""; username = "" }) { Text("Cancelar") } })
    }
}

@Composable
internal fun DesktopSyncPage(controller: AppController, onBack: () -> Unit, onAccounts: () -> Unit) {
    val spotify by controller.accounts.tokens.spotifyAuthState.collectAsState()
    val youtube by controller.accounts.tokens.youTubeAuthState.collectAsState()
    val ready by controller.accounts.ready.collectAsState()
    val accountBusy by controller.accounts.busy.collectAsState()
    val state by controller.syncState.collectAsState()
    var spotifyOn by remember { mutableStateOf(true) }
    var youtubeOn by remember { mutableStateOf(true) }
    var liked by remember { mutableStateOf(true) }
    var playlists by remember { mutableStateOf(true) }
    var mixes by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf(false) }
    val spotifyConnected = spotify is AuthState.Connected
    val youtubeConnected = youtube is AuthState.Connected
    val supportedSelection = (spotifyOn && spotifyConnected && (liked || playlists || mixes)) ||
        (youtubeOn && youtubeConnected && (liked || playlists || mixes || albums))
    ScrollPage("Sincronização", onBack) {
        Text("Adicionar músicas e playlists dos serviços à biblioteca deste PC.")
        Text("Esta sincronização preserva suas músicas e playlists existentes. Remoções feitas nos serviços não são espelhadas nesta etapa.", style = MaterialTheme.typography.bodySmall)
        SettingsGroupCard(rows = listOf(
            { SettingsToggleRow("Spotify", spotifyOn && spotifyConnected, { spotifyOn = it }, enabled = spotifyConnected && !state.running,
                subtitle = if (spotifyConnected) "Conta conectada" else "Conecte a conta em Contas") },
            { SettingsToggleRow("YouTube Music", youtubeOn && youtubeConnected, { youtubeOn = it }, enabled = youtubeConnected && !state.running,
                subtitle = if (youtubeConnected) "Conta conectada" else "Conecte a conta em Contas") },
        ))
        SettingsGroupCard(rows = listOf(
            { SettingsToggleRow("Músicas curtidas", liked, { liked = it }, enabled = !state.running) },
            { SettingsToggleRow("Playlists", playlists, { playlists = it }, enabled = !state.running) },
            { SettingsToggleRow("Mixes dos serviços", mixes, { mixes = it }, enabled = !state.running) },
            { SettingsToggleRow("Álbuns salvos do YouTube", albums, { albums = it }, enabled = !state.running && youtubeConnected) },
        ))
        if (state.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = controller::cancelSync, enabled = !state.committing) { Text("Cancelar sincronização") }
        } else Button(onClick = { controller.synchronize(DesktopSyncOptions(liked, playlists, mixes, albums), spotifyOn, youtubeOn) },
            enabled = ready && accountBusy == null && supportedSelection) { Text("Sincronizar agora") }
        if (state.label.isNotEmpty()) Text(state.label)
        if (state.tracks > 0 || state.playlists > 0) Text("${state.tracks} faixas e ${state.playlists} playlists lidas")
        state.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onAccounts) { Text("Gerenciar contas") }
    }
}
private fun openAccountBrowser(url: String) {
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host in setOf("open.spotify.com", "music.youtube.com", "discord.com", "www.last.fm"))
    runCatching { Desktop.getDesktop().browse(uri) }
}

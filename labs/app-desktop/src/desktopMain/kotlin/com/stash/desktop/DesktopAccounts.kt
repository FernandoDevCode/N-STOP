package com.stash.desktop

import com.stash.core.auth.TokenManagerImpl
import com.stash.core.auth.model.*
import com.stash.core.auth.spotify.SpotifyAuthManager
import com.stash.core.auth.youtube.YouTubeCookieHelper
import com.stash.core.data.lastfm.LastFmCredentials
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.ytmusic.InnerTubeClient
import com.stash.data.ytmusic.YTMusicApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Native account host. Service auth/refresh and library clients remain the original app code. */
class DesktopAccounts(directory: File,
    val client: OkHttpClient = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).followRedirects(false).build(),
    cipher: CredentialCipher = WindowsDpapi,
    initialLastFmCredentials: LastFmCredentials = loadDesktopLastFmCredentials(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val operationLock = Mutex()
    val vault = WindowsTokenStore(directory, cipher)
    private val lastFmStore = DesktopLastFmStore(directory, cipher)
    private val helper = YouTubeCookieHelper()
    private val spotifyAuth = SpotifyAuthManager(client)
    val tokens = TokenManagerImpl(vault, spotifyAuth, helper, client)
    val spotify = SpotifyApiClient(client, tokens, spotifyAuth)
    val innerTube = InnerTubeClient(client, tokens, helper)
    val youtube = YTMusicApiClient(innerTube)
    val ready = MutableStateFlow(false)
    val busy = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val lastFmState = MutableStateFlow<AuthState>(AuthState.NotConnected)
    val lastFmConfigured = MutableStateFlow(initialLastFmCredentials.isConfigured)
    val lastFmBlocked = MutableStateFlow(false)
    val lastFmAuthorizationUrl = MutableStateFlow<String?>(null)
    private var lastFmCredentials = initialLastFmCredentials
    private var pendingLastFmToken: String? = null
    private var lastFmSession: DesktopLastFmSession? = null

    init {
        scope.launch {
            try {
                vault.initialize()
                vault.errors.value?.let { error.value = it }
                runCatching { lastFmStore.load() }.onSuccess { session ->
                    if (session != null) {
                        lastFmSession = session
                        lastFmCredentials = LastFmCredentials(session.apiKey, session.apiSecret)
                        lastFmConfigured.value = true
                        lastFmState.value = AuthState.Connected(UserInfo(session.username, session.username))
                    }
                }.onFailure { lastFmBlocked.value = true; error.value = "Não foi possível abrir a sessão Last.fm. O arquivo foi preservado." }
            } catch (_: Exception) { error.value = "Não foi possível abrir o cofre de contas do Windows." }
            finally { ready.value = true }
        }
    }
    private fun operation(label: String, action: suspend () -> Unit) {
        scope.launch {
            ready.first { it }
            operationLock.withLock {
                busy.value = label; error.value = null
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: AccountInputException) { error.value = failure.message }
                catch (_: Exception) { error.value = "O serviço não confirmou a conexão. Verifique a sessão e tente novamente." }
                finally { busy.value = null }
            }
        }
    }
    fun connect(service: AuthService, credential: String, username: String = "") = operation("Conectando…") {
        val input = credential.trim()
        if (input.isBlank() || input.length > 131072 || '\r' in input || '\n' in input)
            throw AccountInputException("Informe uma credencial de sessão válida em uma única linha.")
        when (service) {
            AuthService.SPOTIFY -> {
                val cookie = if (input.contains("sp_dc=")) input.split(';').firstOrNull { it.trim().startsWith("sp_dc=") }
                    ?.trim()?.removePrefix("sp_dc=").orEmpty() else input
                if (cookie.isBlank()) throw AccountInputException("O cookie sp_dc está vazio.")
                check(tokens.connectSpotifyWithCookie(cookie, username.trim()))
                if (tokens.getSpotifyUsername().isNullOrBlank() || tokens.getSpotifyUsername() == "spotify_user") {
                    tokens.clearAuth(service)
                    throw AccountInputException("O Spotify não informou seu identificador. Conecte novamente preenchendo também o nome de usuário.")
                }
            }
            AuthService.YOUTUBE_MUSIC -> {
                val profile = try { YouTubeSessionValidator(client).validate(input) }
                    catch (_: Exception) { throw AccountInputException("O YouTube não confirmou uma conta autenticada. Copie novamente a sessão do seu navegador.") }
                currentCoroutineContext().ensureActive()
                vault.saveYouTubeToken(ServiceToken("cookie_auth", input, System.currentTimeMillis() / 1000 + 31536000), profile)
            }
            AuthService.DISCORD -> check(tokens.connectDiscordWithToken(input))
        }
    }
    fun disconnect(service: AuthService) = operation("Desconectando…") {
        innerTube.endSyncSession()
        tokens.clearAuth(service)
    }
    fun beginLastFm(apiKey: String = "", apiSecret: String = "") = operation("Preparando autorização Last.fm…") {
        val credentials = if (apiKey.isNotBlank() || apiSecret.isNotBlank()) LastFmCredentials(apiKey.trim(), apiSecret.trim()) else lastFmCredentials
        if (!credentials.isConfigured) throw AccountInputException("Configure a chave e o segredo da API Last.fm nesta tela ou no build local.")
        val token = lastFmClient(client, credentials).getAuthToken().getOrThrow()
        currentCoroutineContext().ensureActive()
        lastFmCredentials = credentials
        lastFmConfigured.value = true
        pendingLastFmToken = token
        fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
        lastFmAuthorizationUrl.value = "https://www.last.fm/api/auth/?api_key=${enc(credentials.apiKey)}&token=${enc(token)}"
    }
    fun finishLastFm() = operation("Concluindo conexão Last.fm…") {
        val token = pendingLastFmToken ?: throw AccountInputException("Inicie a conexão Last.fm primeiro.")
        val (username, sessionKey) = lastFmClient(client, lastFmCredentials).getSession(token).getOrThrow()
        check(username.isNotBlank() && sessionKey.isNotBlank())
        currentCoroutineContext().ensureActive()
        val session = DesktopLastFmSession(username, sessionKey, lastFmCredentials.apiKey, lastFmCredentials.apiSecret)
        lastFmStore.save(session)
        lastFmSession = session
        lastFmState.value = AuthState.Connected(UserInfo(username, username))
        lastFmAuthorizationUrl.value = null; pendingLastFmToken = null
    }
    fun disconnectLastFm() = operation("Desconectando Last.fm…") {
        lastFmStore.clear(); lastFmSession = null; lastFmState.value = AuthState.NotConnected
        lastFmBlocked.value = false
        lastFmAuthorizationUrl.value = null; pendingLastFmToken = null
    }
    override fun close() {
        scope.cancel(); tokens.close(); innerTube.endSyncSession(); client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }
}
internal class AccountInputException(message: String) : IllegalArgumentException(message)

package com.stash.desktop

import com.stash.core.auth.model.*
import com.stash.core.auth.store.TokenStore
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface CredentialCipher {
    fun protect(cleartext: ByteArray): ByteArray
    fun unprotect(ciphertext: ByteArray): ByteArray
}
object WindowsDpapi : CredentialCipher {
    override fun protect(cleartext: ByteArray): ByteArray = Crypt32Util.cryptProtectData(cleartext)
    override fun unprotect(ciphertext: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(ciphertext)
}
@Serializable
internal data class StoredAccount(val access: String, val refresh: String, val expiry: Long,
    val scope: String, val id: String?, val name: String?, val image: String?) {
    fun token() = ServiceToken(access, refresh, expiry, scope)
    fun user() = id?.let { UserInfo(it, name ?: it, image) }
    companion object {
        fun of(token: ServiceToken, user: UserInfo?) = StoredAccount(token.accessToken, token.refreshToken,
            token.expiresAtEpoch, token.scope, user?.id, user?.displayName, user?.imageUrl)
    }
}

/** One atomic encrypted blob per service; no plaintext credential or recovery fallback on disk. */
class WindowsTokenStore(private val directory: File, private val cipher: CredentialCipher = WindowsDpapi) : TokenStore {
    val initialized = MutableStateFlow(false)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val values = MutableStateFlow<Map<AuthService, StoredAccount>>(emptyMap())
    val errors = MutableStateFlow<String?>(null)
    val unreadableServices = MutableStateFlow<Set<AuthService>>(emptySet())
    private val blocked = mutableSetOf<AuthService>()
    private fun file(service: AuthService) = File(directory, "${service.name.lowercase()}.dpapi")
    suspend fun initialize() = withContext(Dispatchers.IO) { lock.withLock {
        val loaded = mutableMapOf<AuthService, StoredAccount>()
        for (service in AuthService.entries) {
            val file = file(service)
            if (!file.exists()) continue
            try {
                require(file.length() in 1..1048576) { "Invalid account file" }
                val bytes = cipher.unprotect(file.readBytes())
                try { loaded[service] = json.decodeFromString<StoredAccount>(bytes.decodeToString()) }
                finally { bytes.fill(0) }
            } catch (_: Exception) {
                blocked.add(service)
                errors.value = "Não foi possível abrir uma credencial salva neste Windows. O arquivo foi preservado; desconecte a conta antes de conectá-la novamente."
            }
        }
        values.value = loaded
        unreadableServices.value = blocked.toSet()
        initialized.value = true
    } }
    private suspend fun save(service: AuthService, token: ServiceToken, user: UserInfo?) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(initialized.value) { "Aguarde a abertura das credenciais do Windows." }
            check(service !in blocked) { "A credencial anterior não pôde ser aberta. Desconecte esta conta antes de substituí-la." }
            val account = StoredAccount.of(token, user)
            val cleartext = json.encodeToString(account).encodeToByteArray()
            val encrypted = try { cipher.protect(cleartext) } finally { cleartext.fill(0) }
            directory.mkdirs()
            val staging = Files.createTempFile(directory.toPath(), "account-", ".tmp")
            try {
                Files.write(staging, encrypted)
                Files.move(staging, file(service).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                values.value = values.value + (service to account)
            } finally { Files.deleteIfExists(staging) }
        }
    }
    private suspend fun clear(service: AuthService) = withContext(Dispatchers.IO) { lock.withLock {
        Files.deleteIfExists(file(service).toPath())
        blocked.remove(service)
        unreadableServices.value = blocked.toSet()
        values.value = values.value - service
    } }
    override val spotifyToken = values.map { it[AuthService.SPOTIFY]?.token() }
    override val spotifyUser = values.map { it[AuthService.SPOTIFY]?.user() }
    override val youTubeToken = values.map { it[AuthService.YOUTUBE_MUSIC]?.token() }
    override val youTubeUser = values.map { it[AuthService.YOUTUBE_MUSIC]?.user() }
    override val discordToken = values.map { it[AuthService.DISCORD]?.token() }
    override val discordUser = values.map { it[AuthService.DISCORD]?.user() }
    override suspend fun saveSpotifyToken(token: ServiceToken, user: UserInfo?) = save(AuthService.SPOTIFY, token, user)
    override suspend fun saveYouTubeToken(token: ServiceToken, user: UserInfo?) = save(AuthService.YOUTUBE_MUSIC, token, user)
    override suspend fun saveDiscordToken(token: ServiceToken, user: UserInfo?) = save(AuthService.DISCORD, token, user)
    override suspend fun clearSpotify() = clear(AuthService.SPOTIFY)
    override suspend fun clearYouTube() = clear(AuthService.YOUTUBE_MUSIC)
    override suspend fun clearDiscord() = clear(AuthService.DISCORD)
}

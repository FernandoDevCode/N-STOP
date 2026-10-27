package com.stash.desktop

import com.stash.core.data.db.dao.LastFmCacheDao
import com.stash.core.data.db.entity.LastFmCacheEntity
import com.stash.core.data.lastfm.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

@Serializable
data class DesktopLastFmSession(val username: String, val sessionKey: String, val apiKey: String, val apiSecret: String)

internal class DesktopLastFmStore(private val directory: File, private val cipher: CredentialCipher) {
    private val file = File(directory, "lastfm.dpapi")
    var blocked = false
        private set
    fun load(): DesktopLastFmSession? {
        if (!file.exists()) return null
        return try {
            require(file.length() in 1..1048576)
            val bytes = cipher.unprotect(file.readBytes())
            try { Json.decodeFromString<DesktopLastFmSession>(bytes.decodeToString()) } finally { bytes.fill(0) }
        } catch (_: Exception) { blocked = true; error("Não foi possível abrir a sessão Last.fm deste Windows. O arquivo foi preservado.") }
    }
    fun save(session: DesktopLastFmSession) {
        check(!blocked) { "Desconecte a sessão Last.fm anterior antes de substituí-la." }
        directory.mkdirs()
        val bytes = Json.encodeToString(session).encodeToByteArray()
        val encrypted = try { cipher.protect(bytes) } finally { bytes.fill(0) }
        val temporary = Files.createTempFile(directory.toPath(), "lastfm-", ".tmp")
        try {
            Files.write(temporary, encrypted)
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    fun clear() { Files.deleteIfExists(file.toPath()); blocked = false }
}
internal class DesktopLastFmCache : LastFmCacheDao {
    private val entries = object : LinkedHashMap<String, LastFmCacheEntity>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LastFmCacheEntity>?) = size > 128
    }
    override suspend fun get(key: String) = synchronized(entries) { entries[key] }
    override suspend fun upsert(entity: LastFmCacheEntity) { synchronized(entries) { entries[entity.cacheKey] = entity } }
    override suspend fun prune(olderThanMs: Long) { synchronized(entries) { entries.values.removeIf { it.fetchedAt < olderThanMs } } }
    override suspend fun clear() { synchronized(entries) { entries.clear() } }
}
internal fun loadDesktopLastFmCredentials(): LastFmCredentials {
    val properties = Properties()
    runCatching {
        listOf(File("../../local.properties"), File("local.properties")).firstOrNull { it.isFile }
            ?.inputStream()?.use(properties::load)
    }
    return LastFmCredentials(System.getenv("LASTFM_API_KEY") ?: properties.getProperty("lastfm.apiKey", ""),
        System.getenv("LASTFM_API_SECRET") ?: properties.getProperty("lastfm.apiSecret", ""))
}
internal fun lastFmClient(client: OkHttpClient, credentials: LastFmCredentials) =
    LastFmApiClient(client, credentials, LastFmRateLimitGate(), DesktopLastFmCache())

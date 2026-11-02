package com.stash.feature.search

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-device, per-artist customization. Imported photos are COPIED into private
 * files; temporary picker URIs are never persisted and no storage permission is needed.
 */
@Singleton
class ArtistHeroPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences("sonora_artist_hero_override_v1", Context.MODE_PRIVATE) }
    private val folder by lazy { File(context.filesDir, "sonora_artist_hero_photos") }
    private fun key(artistId: String): String =
        MessageDigest.getInstance("SHA-256").digest(artistId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

    fun get(artistId: String): String? {
        val value = prefs.getString(key(artistId), null) ?: return null
        if (!value.startsWith("file:")) return value.takeIf(::isValidPhotoUrl)
        val file = runCatching { File(URI(value)) }.getOrNull() ?: return null
        return value.takeIf { file.isFile && file.parentFile == folder }
    }

    fun saveLink(artistId: String, rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!isValidPhotoUrl(url)) return false
        val previous = get(artistId)
        prefs.edit().putString(key(artistId), url).apply()
        deleteOldLocal(previous)
        return true
    }

    suspend fun saveImage(artistId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val temp = File.createTempFile("import-", ".tmp", folder)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(32768)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMAGE_BYTES) throw IOException("Imagem muito grande (máximo 15 MB).")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("Não foi possível abrir a imagem.")
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, opts)
            if (opts.outWidth !in 1..MAX_DIMENSION || opts.outHeight !in 1..MAX_DIMENSION ||
                opts.outWidth.toLong() * opts.outHeight > MAX_PIXELS ||
                opts.outMimeType?.startsWith("image/") != true
            ) throw IOException("Selecione uma imagem válida de até 40 MP.")
            val final = File(folder, key(artistId) + "-" + System.nanoTime() + ".img")
            if (!temp.renameTo(final)) throw IOException("Não foi possível guardar a imagem.")
            val previous = get(artistId)
            val result = final.toURI().toString()
            prefs.edit().putString(key(artistId), result).apply()
            deleteOldLocal(previous)
            result
        } finally {
            temp.delete()
        }
    }

    fun clear(artistId: String) {
        val previous = get(artistId)
        prefs.edit().remove(key(artistId)).apply()
        deleteOldLocal(previous)
    }

    private fun deleteOldLocal(previous: String?) {
        if (previous?.startsWith("file:") != true) return
        val file = runCatching { File(URI(previous)) }.getOrNull() ?: return
        if (file.parentFile == folder) file.delete()
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 15L * 1024 * 1024
        private const val MAX_DIMENSION = 8192
        private const val MAX_PIXELS = 40_000_000L

        /** Avoid loading local-network URLs or unsupported schemes in Coil. */
        fun isValidPhotoUrl(raw: String): Boolean = try {
            val uri = URI(raw.trim())
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null && host.isNotBlank() &&
                host != "localhost" && !host.endsWith(".local") &&
                !host.endsWith(".internal") &&
                !host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) &&
                !host.contains(":") && raw.length <= 2048
        } catch (_: Exception) {
            false
        }
    }
}

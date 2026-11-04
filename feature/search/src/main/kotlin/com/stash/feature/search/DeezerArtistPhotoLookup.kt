package com.stash.feature.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * Public Deezer artist portrait lookup. Photos are used only for the artist hero,
 * never as album covers or as evidence that the user follows the artist.
 * Exact normalized name matching avoids showing another artist's photograph.
 */
@Singleton
class DeezerArtistPhotoLookup @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences("sonora_deezer_artist_photos_v1", Context.MODE_PRIVATE)
    }

    suspend fun find(name: String): String? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext null
        val key = normalizedName(name)
        val cached = prefs.getString("$key.url", null)?.takeIf(::isSafePhotoUrl)
        val age = System.currentTimeMillis() - prefs.getLong("$key.updated", 0L)
        if (cached != null && age in 0 until CACHE_MS) return@withContext cached

        val result = try {
            val url = "https://api.deezer.com/search/artist?q=" +
                URLEncoder.encode(name.trim(), "UTF-8") + "&limit=8"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val artists = JSONObject(body).optJSONArray("data") ?: return@use null
                (0 until artists.length()).asSequence()
                    .mapNotNull { artists.optJSONObject(it) }
                    .firstOrNull { normalizedName(it.optString("name")) == key }
                    ?.optString("picture_xl")
                    ?.takeIf(::isSafePhotoUrl)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        if (result != null) {
            prefs.edit().putString("$key.url", result)
                .putLong("$key.updated", System.currentTimeMillis()).apply()
        }
        result ?: cached
    }

    private fun isSafePhotoUrl(url: String): Boolean =
        url.startsWith("https://cdn-images.dzcdn.net/") ||
            url.startsWith("https://e-cdns-images.dzcdn.net/")

    private fun normalizedName(raw: String): String =
        Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

    private companion object {
        const val CACHE_MS = 7L * 24 * 60 * 60 * 1000
    }
}

package com.stash.feature.search

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Experimental artist wordmark lookup, development use only.
 *
 * TheAudioDB's free API is permitted for development but its terms require a
 * paid subscription before an app-store publication. This does NOT certify
 * that community artwork is an artist-authorized official logo; the renderer
 * must always fall back to plain typographic text.
 *
 * Exact artist-name matching prevents using another musician's identity for
 * names with partial overlaps. No fuzzy matching or arbitrary image search.
 */
internal object ArtistWordmarkLookup {
    private val checked = ConcurrentHashMap<String, String>()

    internal fun matchingArtist(requested: String, returned: String): Boolean =
        requested.trim().equals(returned.trim(), ignoreCase = true)

    suspend fun resolve(artistName: String): String? = withContext(Dispatchers.IO) {
        val name = artistName.trim()
        if (name.isBlank()) return@withContext null
        checked[name]?.let { return@withContext it }

        val result = runCatching {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val conn = URL("https://www.theaudiodb.com/api/v1/json/123/search.php?s=$encoded")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val artists = JSONObject(raw).optJSONArray("artists") ?: return@runCatching null
                for (index in 0 until artists.length()) {
                    val entry = artists.optJSONObject(index) ?: continue
                    if (!matchingArtist(name, entry.optString("strArtist"))) continue
                    val logo = entry.optString("strArtistLogo")
                    val url = URL(logo)
                    if (url.protocol != "https" ||
                        url.host.lowercase() !in setOf(
                            "www.theaudiodb.com", "theaudiodb.com", "r2.theaudiodb.com",
                        ) ||
                        !url.path.startsWith("/images/media/artist/logo/") ||
                        !url.path.endsWith(".png", ignoreCase = true)
                    ) continue
                    return@runCatching url.toString()
                }
                null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        // Do not permanently cache failures: network errors and rate limiting
        // must be retried when the user reopens the artist.
        if (result != null) checked[name] = result
        result
    }
}

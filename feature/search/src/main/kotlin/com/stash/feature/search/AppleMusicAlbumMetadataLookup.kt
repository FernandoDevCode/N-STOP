package com.stash.feature.search

import com.stash.data.ytmusic.model.AlbumSummary
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AppleAlbumMetadata(
    val title: String,
    val year: String?,
    val artworkUrl: String?,
)

/**
 * Apple Music as primary *metadata* for already resolved YouTube/Qobuz releases.
 * Never inserts unplayable Apple albums into the playable catalog, changes IDs,
 * alters playback providers or requires a subscription/developer token.
 */
@Singleton
internal class AppleMusicAlbumMetadataLookup @Inject constructor() {
    companion object {
        internal fun normalize(title: String) = title.trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

        internal fun parseAlbumLookup(json: String, artistId: Long): Map<String, AppleAlbumMetadata> {
            val records = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull()
                ?: return emptyMap()
            val results = linkedMapOf<String, AppleAlbumMetadata>()
            for (i in 0 until minOf(records.length(), 200)) {
                val item = records.optJSONObject(i) ?: continue
                if (item.optString("wrapperType") != "collection" ||
                    item.optLong("artistId") != artistId ||
                    item.optString("collectionType") != "Album"
                ) continue
                val name = item.optString("collectionName").trim()
                if (name.isBlank() || name.length > 180) continue
                val key = normalize(name)
                // Ambiguous editions cannot be resolved by title alone.
                // First candidate retained rather than silently swapping versions.
                if (key in results) continue
                val date = item.optString("releaseDate")
                val year = date.take(4).takeIf { it.matches(Regex("[0-9]{4}")) }
                results[key] = AppleAlbumMetadata(name, year, appleArtwork(item.optString("artworkUrl100")))
            }
            return results
        }

        internal fun appleArtwork(raw: String): String? = runCatching {
            val url = URL(raw)
            if (url.protocol != "https" || url.userInfo != null || url.port != -1 ||
                url.query != null || url.ref != null ||
                !Regex("^is[0-9]+-ssl\\.mzstatic\\.com$").matches(url.host.lowercase()) ||
                !url.path.startsWith("/image/thumb/")
            ) return@runCatching null
            // Official iTunes artworkUrl100 uses a 100x100bb thumbnail.
            val updated = raw.replace(Regex("/100x100bb\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE),
                "/600x600bb.jpg")
            if (updated == raw) return@runCatching null
            updated
        }.getOrNull()

        internal fun enrich(
            albums: List<AlbumSummary>,
            metadata: Map<String, AppleAlbumMetadata>,
        ): List<AlbumSummary> = albums.map { album ->
            val apple = metadata[normalize(album.title)] ?: return@map album
            // Preserve the canonical playable ID, source, and track counts.
            album.copy(
                year = apple.year ?: album.year,
                thumbnailUrl = apple.artworkUrl ?: album.thumbnailUrl,
            )
        }
    }

    suspend fun find(artistName: String): Map<String, AppleAlbumMetadata> = withContext(Dispatchers.IO) {
        val name = artistName.trim().takeIf { it.isNotEmpty() } ?: return@withContext emptyMap()
        try {
            val q = URLEncoder.encode(name, "UTF-8")
            val search = fetch("https://itunes.apple.com/search?term=$q&entity=musicArtist&limit=12")
                ?: return@withContext emptyMap()
            val artist = AppleMusicWordmarkLookup.parseSearch(search, name)
                ?: return@withContext emptyMap()
            val albums = fetch("https://itunes.apple.com/lookup?id=${artist.id}&entity=album&limit=200")
                ?: return@withContext emptyMap()
            parseAlbumLookup(albums, artist.id)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun fetch(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3500
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Sonora/0.9 (album metadata)")
            if (connection.responseCode != 200) return null
            // Bounded response size, unlike readText().take() which reads all bytes first.
            val content = java.io.ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > 700_000) return null
                    content.write(chunk, 0, n)
                }
            }
            return content.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }
}

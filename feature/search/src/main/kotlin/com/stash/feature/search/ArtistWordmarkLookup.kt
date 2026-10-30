package com.stash.feature.search

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Artist wordmarks only: never substitute album artwork, artist photography,
 * cutouts or a visually similar artist's logo.
 *
 * Apple artistLogo is the preferred wordmark. Fanart.tv HD music logos
 * (when a project key is configured) are ranked by source likes. TheAudioDB's
 * exact-name artist logo is the final image fallback.
 *
 * Apple Music's public artist pages may expose an artistLogo metadata image.
 * AppleMusicWordmarkLookup reads this image only if the official iTunes search
 * resolves one exact artist; no Google scraping or private API calls.
 */
internal object ArtistWordmarkLookup {
    private val checked = ConcurrentHashMap<String, String>()
    private val mbidRegex = Regex("^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")

    internal data class AudioDbCandidate(val mbid: String?, val logoUrl: String?)

    internal fun matchingArtist(requested: String, returned: String): Boolean =
        requested.trim().equals(returned.trim(), ignoreCase = true)

    private fun validLogoUrl(raw: String, source: String): String? = runCatching {
        val uri = URL(raw)
        if (uri.protocol != "https" || uri.userInfo != null ||
            uri.port != -1 || uri.query != null || uri.ref != null
        ) return@runCatching null

        val host = uri.host.lowercase()
        val path = uri.path.lowercase()
        val allowed = when (source) {
            "theaudiodb" -> host in setOf("www.theaudiodb.com", "theaudiodb.com", "r2.theaudiodb.com") &&
                path.startsWith("/images/media/artist/logo/")
            "fanart" -> host == "assets.fanart.tv" &&
                path.startsWith("/fanart/music/") &&
                (path.contains("/hdmusiclogo/") || path.contains("/musiclogo/"))
            else -> false
        }
        uri.toString().takeIf { allowed && path.endsWith(".png") }
    }.getOrNull()

    internal fun parseAudioDb(raw: String, artistName: String): AudioDbCandidate? {
        val array = JSONObject(raw).optJSONArray("artists") ?: return null
        for (i in 0 until array.length()) {
            val candidate = array.optJSONObject(i) ?: continue
            if (!matchingArtist(artistName, candidate.optString("strArtist"))) continue
            val mbid = candidate.optString("strMusicBrainzID").takeIf {
                it.matches(mbidRegex)
            }
            val logo = validLogoUrl(candidate.optString("strArtistLogo"), "theaudiodb")
            return AudioDbCandidate(mbid, logo)
        }
        return null
    }

    internal fun parseMusicBrainzId(raw: String, artistName: String): String? {
        val artists = JSONObject(raw).optJSONArray("artists") ?: return null
        val matches = mutableSetOf<String>()
        for (i in 0 until artists.length()) {
            val artist = artists.optJSONObject(i) ?: continue
            if (!matchingArtist(artistName, artist.optString("name"))) continue
            val id = artist.optString("id")
            if (id.matches(mbidRegex)) matches += id.lowercase()
        }
        // MusicBrainz can have multiple distinct artists with the same name.
        // No guesswork: ambiguous identities keep the existing text fallback.
        return matches.singleOrNull()
    }

    internal fun parseFanartLogo(raw: String, artistName: String, mbid: String): String? {
        val root = JSONObject(raw)
        // Require BOTH identity fields: the live Fanart endpoint has returned
        // a different artist's name for a valid MBID query, so MBID alone is
        // insufficient to prevent wrong-band logos.
        if (!matchingArtist(artistName, root.optString("name"))) return null
        val returnedId = root.optString("mbid_id").ifBlank { root.optString("mbid") }
        if (!returnedId.equals(mbid, ignoreCase = true)) return null

        fun ranked(type: String): String? {
            val list = root.optJSONArray(type) ?: return null
            val candidates = mutableListOf<Pair<String, Int>>()
            for (i in 0 until list.length()) {
                val asset = list.optJSONObject(i) ?: continue
                val url = validLogoUrl(asset.optString("url"), "fanart") ?: continue
                val likes = asset.optString("likes").toIntOrNull()?.coerceAtLeast(0) ?: 0
                candidates += url to likes
            }
            return candidates.maxByOrNull { it.second }?.first
        }
        return ranked("hdmusiclogo") ?: ranked("musiclogo")
    }

    private fun getJson(url: String, apiKey: String? = null): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "SonoraArtistLogos/1.0 (github.com/FernandoDevCode/N-STOP)")
            if (apiKey != null) conn.setRequestProperty("api-key", apiKey)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun musicBrainzId(name: String): String? = runCatching {
        val query = URLEncoder.encode("artist:\"$name\"", "UTF-8")
        val raw = getJson("https://musicbrainz.org/ws/2/artist/?query=$query&fmt=json&limit=10")
            ?: return@runCatching null
        parseMusicBrainzId(raw, name)
    }.getOrNull()

    suspend fun resolve(artistName: String): String? = withContext(Dispatchers.IO) {
        val name = artistName.trim()
        if (name.isBlank()) return@withContext null
        checked[name]?.let { return@withContext it }

        // Source priority is a product decision: official artist-submitted
        // Apple wordmark first, then source-rated Fanart, then TheAudioDB.
        // Only an explicit artistLogo may win; a page's og:image is not a logo.
        val appleLogo = AppleMusicWordmarkLookup.resolve(name)
        if (appleLogo != null) {
            checked[name] = appleLogo
            return@withContext appleLogo
        }

        val raw = runCatching {
            getJson("https://www.theaudiodb.com/api/v1/json/123/search.php?s=" +
                URLEncoder.encode(name, "UTF-8"))
        }.getOrNull()
        val audioDb = runCatching { raw?.let { parseAudioDb(it, name) } }.getOrNull()

        val fanartKey = BuildConfig.FANART_TV_API_KEY
        val fanartUrl = if (fanartKey.isNotBlank()) {
            val id = audioDb?.mbid ?: musicBrainzId(name)
            if (id != null) runCatching {
                getJson("https://webservice.fanart.tv/v3.2/music/$id", fanartKey)
                    ?.let { parseFanartLogo(it, name, id) }
            }.getOrNull() else null
        } else null

        val chosen = fanartUrl ?: audioDb?.logoUrl
        if (chosen != null) checked[name] = chosen
        chosen
    }
}

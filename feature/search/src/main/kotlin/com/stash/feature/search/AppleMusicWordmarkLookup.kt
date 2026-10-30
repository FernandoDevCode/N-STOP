package com.stash.feature.search

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Optional, best-effort lookup of Apple's public *artistLogo* web metadata.
 *
 * IMPORTANT: an artist photograph, og:image, album artwork or share preview
 * is never a wordmark. Apple Music's documented catalog API does not expose
 * artistLogo, so a change to the public site's embedded metadata must fail
 * closed without affecting other Sonora logo sources.
 *
 * The official iTunes search service resolves the Apple artist page; Google
 * results are neither required nor scraped. No private Apple APIs or tokens.
 */
internal object AppleMusicWordmarkLookup {
    internal data class Artist(val id: Long, val pageUrl: String)

    internal fun parseSearch(json: String, artistName: String): Artist? {
        val hits = JSONObject(json).optJSONArray("results") ?: return null
        val matches = mutableMapOf<Long, Artist>()
        for (index in 0 until hits.length()) {
            val item = hits.optJSONObject(index) ?: continue
            if (item.optString("wrapperType") != "artist" ||
                !ArtistWordmarkLookup.matchingArtist(artistName, item.optString("artistName"))
            ) continue
            val id = item.optLong("artistId", 0)
            if (id <= 0) continue
            val candidateUrl = item.optString("artistLinkUrl")
            val page = validateArtistPage(candidateUrl, id) ?: continue
            matches[id] = Artist(id, page)
        }
        // Never guess between unrelated artists with an identical name.
        return matches.values.singleOrNull()
    }

    private fun validateArtistPage(raw: String, id: Long): String? = runCatching {
        val url = URL(raw)
        if (url.protocol != "https" || url.host.lowercase() != "music.apple.com" ||
            url.userInfo != null || url.port != -1
        ) return@runCatching null
        val segments = url.path.trim('/').split('/')
        if (segments.size != 4 || segments[1] != "artist" ||
            segments[3] != id.toString() || segments[2].isBlank()
        ) return@runCatching null
        // Drop tracking query parameters and fragments.
        "https://music.apple.com${url.path}"
    }.getOrNull()

    internal fun parseLogo(html: String, artist: Artist): String? {
        // The canonical artist ID must occur in the page data, not only in the URL.
        if (!html.contains("\"storeAdamID\":\"${artist.id}\"")) return null

        val marker = Regex("\"artistLogo\"\\s*:\\s*\\{")
        val match = marker.find(html) ?: return null
        val start = html.indexOf('{', match.range.first)
        if (start < 0) return null
        val json = balancedObject(html, start) ?: return null
        val logo = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val dictionary = logo.optJSONObject("dictionary") ?: return null
        val assetUrl = dictionary.optString("url")
        return validateLogoAsset(assetUrl)
    }

    // Extract exactly one JSON object without scanning arbitrary script content.
    private fun balancedObject(src: String, start: Int): String? {
        var level = 0
        var quoted = false
        var escaped = false
        val limit = minOf(src.length, start + 4096)
        for (index in start until limit) {
            val c = src[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else {
                when (c) {
                    '"' -> quoted = true
                    '{' -> level++
                    '}' -> {
                        level--
                        if (level == 0) return src.substring(start, index + 1)
                        if (level < 0) return null
                    }
                }
            }
        }
        return null
    }

    internal fun validateLogoAsset(raw: String): String? = runCatching {
        if (raw.isBlank() || raw.length > 1200) return@runCatching null
        // Apple returns a media URL template. Never pass literal placeholders
        // to Coil or rewrite an arbitrary URL: substitute only Apple's w/h keys.
        val resolved = raw
            .replace("{w}", "1200")
            .replace("{h}", "480")
            .replace("{f}", "png")
        if ('{' in resolved || '}' in resolved) return@runCatching null

        val url = URL(resolved)
        val host = url.host.lowercase()
        if (url.protocol != "https" || url.userInfo != null ||
            url.port != -1 || url.query != null || url.ref != null
        ) return@runCatching null
        if (!Regex("^is[0-9]+-ssl\\.mzstatic\\.com$").matches(host)) return@runCatching null
        if (!url.path.startsWith("/image/thumb/") ||
            !url.path.lowercase().endsWith(".png")
        ) return@runCatching null
        resolved
    }.getOrNull()

    private fun getText(url: String, expectedType: String): String? {
        val parsed = URL(url)
        val conn = parsed.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4500
            conn.readTimeout = 4500
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; SonoraArtistArt/1.0)")
            conn.setRequestProperty("Accept", expectedType)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            // A public profile is generally < 350 KB. Avoid unbounded reads.
            return conn.inputStream.bufferedReader().use { it.readText().take(600_000) }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun resolve(artistName: String): String? = withContext(Dispatchers.IO) {
        val name = artistName.trim().takeIf { it.isNotEmpty() } ?: return@withContext null
        runCatching {
            val q = URLEncoder.encode(name, "UTF-8")
            val search = getText(
                "https://itunes.apple.com/search?term=$q&entity=musicArtist&limit=12",
                "application/json",
            ) ?: return@runCatching null
            val candidate = parseSearch(search, name) ?: return@runCatching null
            val html = getText(candidate.pageUrl, "text/html") ?: return@runCatching null
            parseLogo(html, candidate)
        }.getOrNull()
    }
}

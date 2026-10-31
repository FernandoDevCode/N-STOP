package com.stash.desktop

import com.stash.data.ytmusic.*
import com.stash.data.ytmusic.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Anonymous transport; production search/album/artist parsers are shared verbatim. */
class DesktopCatalog(private val authenticated: YTMusicApiClient? = null, private val innerTube: InnerTubeClient? = null) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    suspend fun search(query: String): SearchAllResults = authenticated?.searchAll(query) ?: parseSearchAll(request("search", "query", query))
    suspend fun album(id: String): AlbumDetail = authenticated?.getAlbum(id) ?: AlbumResponseParser.parse(id, request("browse", "browseId", id))
    suspend fun explore(): List<AlbumSummary> {
        val response = if (innerTube != null) innerTube.browse("FEmusic_home") ?: error("O YouTube Music não respondeu ao catálogo.") else request("browse", "browseId", "FEmusic_home")
        val albums = mutableListOf<AlbumSummary>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["musicCarouselShelfRenderer"]?.asObject()?.let { albums.addAll(parseAlbumsCarousel(it)) }
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(response)
        return albums.distinctBy { it.id }
    }
    suspend fun artist(id: String): ArtistProfile {
        if (authenticated != null) return authenticated.getArtist(id)
        val response = request("browse", "browseId", id)
        val header = response["header"]?.asObject()?.get("musicImmersiveHeaderRenderer")?.asObject()
        val name = header?.navigatePath("title", "runs")?.asArray()?.firstOrNull()?.asObject()?.get("text")?.asString().orEmpty()
        val art = header?.navigatePath("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
            ?.asArray()?.lastOrNull()?.asObject()?.get("url")?.asString()
        val sections = response.navigatePath("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.asArray()?.firstOrNull()?.asObject()?.navigatePath("tabRenderer", "content", "sectionListRenderer", "contents")?.asArray().orEmpty()
        var popular = emptyList<TrackSummary>()
        var albums = emptyList<AlbumSummary>()
        var singles = emptyList<AlbumSummary>()
        var related = emptyList<ArtistSummary>()
        for (item in sections) {
            val section = item.asObject() ?: continue
            section["musicShelfRenderer"]?.asObject()?.let { if (popular.isEmpty()) popular = parseTracksFromShelf(it) }
            section["musicCarouselShelfRenderer"]?.asObject()?.let { carousel ->
                val title = carousel.navigatePath("header", "musicCarouselShelfBasicHeaderRenderer", "title", "runs")
                    ?.asArray()?.firstOrNull()?.asObject()?.get("text")?.asString().orEmpty()
                when {
                    title.contains("Albums", true) -> albums = parseAlbumsCarousel(carousel)
                    title.contains("Singles", true) || title.contains("EPs", true) -> singles = parseAlbumsCarousel(carousel)
                    title.contains("Fans also like", true) -> related = parseArtistsCarousel(carousel)
                }
            }
        }
        return ArtistProfile(id, name, art, null, popular, albums, singles, related)
    }
    private suspend fun request(endpoint: String, field: String, value: String): JsonObject = withContext(Dispatchers.IO) {
        require(endpoint in setOf("search", "browse"))
        val body = buildJsonObject {
            putJsonObject("context") { putJsonObject("client") {
                put("clientName", "WEB_REMIX"); put("clientVersion", "1.20251001.01.00")
                put("hl", "en"); put("gl", "US")
            } }
            put(field, value)
        }
        val request = HttpRequest.newBuilder(URI("https://music.youtube.com/youtubei/v1/$endpoint"))
            .timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("User-Agent", "Mozilla/5.0").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "YouTube Music respondeu HTTP ${response.statusCode()}. Tente novamente." }
        Json.parseToJsonElement(response.body()).jsonObject
    }
}

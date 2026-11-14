package com.stash.data.ytmusic

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Public catalog metadata only. No audio resolution or executable actions. */
data class YouTubeWebVideo(
    val videoId: String,
    val title: String,
    val channel: String?,
    val thumbnailUrl: String?,
    val durationMs: Long?,
    val providerRank: Int,
)

/** Pass nextRank as rankOffset when requesting the next page of this query. */
data class YouTubeWebSearchPage(
    val videos: List<YouTubeWebVideo>,
    val continuation: String?,
    val nextRank: Int,
)

class YouTubeWebHttpException(val statusCode: Int) : IOException("YouTube search HTTP $statusCode")

/** Independent anonymous WEB transport; does not share YTM auth or player context. */
class YouTubeWebSearchClient(
    client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val origin: String = "https://www.youtube.com",
    timeoutMs: Long = 15_000,
) {
    private val http = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
    private val userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    // The old path fetched youtube.com/ on every search and again on every
    // continuation. On cellular it added a redundant round-trip to each
    // Spotify match before the player could even begin resolving audio.
    private data class WebConfig(val version: String, val key: String, val validUntilNs: Long)
    private val configMutex = Mutex()
    @Volatile private var cachedConfig: WebConfig? = null

    private suspend fun webConfig(): WebConfig {
        fun fresh(config: WebConfig?): WebConfig? =
            config?.takeIf { System.nanoTime() < it.validUntilNs }
        fresh(cachedConfig)?.let { return it }
        return configMutex.withLock {
            fresh(cachedConfig)?.let { return@withLock it }
            // Real WEB config, never an invented YouTube Music version/key.
            val html = request(Request.Builder().url("$origin/")
                .header("User-Agent", userAgent).build())
            fun field(name: String): String =
                Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"")
                    .find(html)?.groupValues?.get(1)
                    ?: throw IOException("Missing YouTube WEB config: $name")
            WebConfig(
                version = field("INNERTUBE_CLIENT_VERSION"),
                key = field("INNERTUBE_API_KEY"),
                validUntilNs = System.nanoTime() + 15L * 60L * 1_000_000_000L,
            ).also { cachedConfig = it }
        }
    }

    suspend fun search(query: String, continuation: String? = null, rankOffset: Int = 0): YouTubeWebSearchPage = withContext(Dispatchers.Default) {
        require(query.isNotBlank())
        require(rankOffset >= 0)
        val config = webConfig()
        val version = config.version
        val key = config.key
        val body = buildJsonObject {
            putJsonObject("context") { putJsonObject("client") {
                put("clientName", "WEB"); put("clientVersion", version)
                put("hl", "en"); put("gl", "US")
            } }
            if (continuation == null) put("query", query) else put("continuation", continuation)
        }
        val url = "$origin/youtubei/v1/search".toHttpUrlBuilder(key)
        val response = request(Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Origin", origin).header("Referer", "$origin/")
            .header("X-YouTube-Client-Name", "1").header("X-YouTube-Client-Version", version)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
        parse(response, rankOffset)
    }

    private fun String.toHttpUrlBuilder(key: String): String =
        this + "?key=" + java.net.URLEncoder.encode(key, "UTF-8") + "&prettyPrint=false"

    private suspend fun request(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        if (!it.isSuccessful) throw YouTubeWebHttpException(it.code)
                        it.body?.string() ?: throw IOException("Empty YouTube response body")
                    }
                    if (continuation.isActive) continuation.resume(body)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    internal fun parse(raw: String, rankOffset: Int = 0): YouTubeWebSearchPage {
        val root = json.parseToJsonElement(raw)
        require(root is JsonObject) { "Expected a YouTube response object" }
        val videos = mutableListOf<YouTubeWebVideo>()
        var rank = rankOffset
        var token: String? = null
        fun readItems(items: JsonArray) {
            for (item in items) {
                val node = item as? JsonObject ?: continue
                // Only explicit item sections and direct video/token entries are accepted.
                node.obj("itemSectionRenderer")?.get("contents")?.let {
                    (it as? JsonArray)?.let(::readItems)
                }
                val video = node["videoRenderer"] as? JsonObject
                if (video != null) {
                    val position = rank++
                    val id = video.string("videoId")
                    val title = video["title"].text()
                    if (id != null && id.matches(Regex("[A-Za-z0-9_-]{11}")) && title != null) {
                        val thumbnail = ((video["thumbnail"] as? JsonObject)?.get("thumbnails") as? JsonArray)
                            ?.mapNotNull { it as? JsonObject }?.lastOrNull()?.string("url")
                        videos += YouTubeWebVideo(id, title,
                            video["ownerText"].text() ?: video["longBylineText"].text() ?: video["shortBylineText"].text(),
                            thumbnail, duration(video["lengthText"].text()), position)
                    }
                    continue
                }
                if (token == null) token = node.obj("continuationItemRenderer")
                    ?.obj("continuationEndpoint")?.obj("continuationCommand")?.string("token")
            }
        }
        val primary = root.obj("contents")?.obj("twoColumnSearchResultsRenderer")
            ?.obj("primaryContents")?.obj("sectionListRenderer")?.get("contents") as? JsonArray
        if (primary != null) {
            readItems(primary)
        } else {
            // Continuations for other targets must not contribute items or tokens.
            val actions = listOf("onResponseReceivedCommands", "onResponseReceivedActions")
                .flatMap { (root[it] as? JsonArray).orEmpty() }
            val searchList = actions.firstNotNullOfOrNull {
                (it as? JsonObject)?.obj("appendContinuationItemsAction")
                    ?.takeIf { action -> action.string("targetId") == "search-feed" }
            }
            (searchList?.get("continuationItems") as? JsonArray)?.let(::readItems)
        }
        return YouTubeWebSearchPage(videos, token, rank)
    }

    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonElement?.text(): String? {
        val obj = this as? JsonObject ?: return null
        return obj.string("simpleText") ?: (obj["runs"] as? JsonArray)?.joinToString("") {
            (it as? JsonObject)?.string("text").orEmpty()
        }?.takeIf { it.isNotBlank() }
    }
    private fun duration(text: String?): Long? {
        val parts = text?.split(':') ?: return null
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        if (numbers.any { it < 0 } || numbers.drop(1).any { it > 59 }) return null
        return try { numbers.fold(0L) { total, n -> Math.addExact(Math.multiplyExact(total, 60), n) }
            .let { Math.multiplyExact(it, 1000) } } catch (_: ArithmeticException) { null }
    }
}

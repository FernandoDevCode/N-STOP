package com.stash.data.ytmusic

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
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

class YouTubeWebHttpException(
    val statusCode: Int,
    val stage: String = "search",
    val hasRetryAfter: Boolean = false,
) : IOException("YouTube WEB $stage HTTP $statusCode")

class YouTubeWebTransportException(
    val stage: String,
    cause: IOException,
) : IOException("YouTube WEB $stage transport failure", cause)

/** Independent anonymous WEB transport; does not share YTM auth or player context. */
class YouTubeWebSearchClient(
    client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val origin: String = "https://www.youtube.com",
    timeoutMs: Long = 7_000,
) {
    private val http = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
    private val userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private class WebConfig(val version: String, val key: String, val loadedAtNanos: Long)
    private val configMutex = Mutex()
    private var cachedConfig: WebConfig? = null
    private val blockedUntilMs = AtomicLong(0)

    suspend fun search(query: String, continuation: String? = null, rankOffset: Int = 0): YouTubeWebSearchPage =
        withContext(Dispatchers.Default) {
            require(query.isNotBlank())
            require(rankOffset >= 0)
            // At most one explicit recovery for the entire search, not per HTTP request.
            var recovered = false
            while (true) {
                currentCoroutineContext().ensureActive()
                var config: WebConfig? = null
                try {
                    config = getConfig()
                    return@withContext searchWithConfig(query, continuation, rankOffset, config)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    currentCoroutineContext().ensureActive()
                    // A rejected WEB configuration must not poison subsequent searches.
                    if (config != null && error is YouTubeWebHttpException && error.statusCode == 400) {
                        configMutex.withLock {
                            if (cachedConfig === config) cachedConfig = null
                        }
                    }
                    val recoverable = when (error) {
                        is YouTubeWebHttpException ->
                            !error.hasRetryAfter && (error.statusCode == 408 ||
                                error.statusCode in 500..599 ||
                                (error.statusCode == 400 && config != null && continuation == null))
                        else -> true
                    }
                    if (recovered || !recoverable) throw error
                    recovered = true
                    delay(300)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        }

    private suspend fun getConfig(): WebConfig = configMutex.withLock {
        cachedConfig?.takeIf {
            System.nanoTime() - it.loadedAtNanos < TimeUnit.MINUTES.toNanos(30)
        }?.let { return@withLock it }

        val html = request(Request.Builder().url("$origin/").header("User-Agent", userAgent).build(), "config")
        fun config(name: String): String = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"")
            .find(html)?.groupValues?.get(1)
            ?: throw YouTubeWebTransportException("config", IOException("Missing WEB configuration"))
        WebConfig(config("INNERTUBE_CLIENT_VERSION"), config("INNERTUBE_API_KEY"), System.nanoTime())
            .also { cachedConfig = it }
    }

    private suspend fun searchWithConfig(
        query: String,
        continuation: String?,
        rankOffset: Int,
        config: WebConfig,
    ): YouTubeWebSearchPage {
        val body = buildJsonObject {
            putJsonObject("context") { putJsonObject("client") {
                put("clientName", "WEB"); put("clientVersion", config.version)
                put("hl", "en"); put("gl", "US")
            } }
            if (continuation == null) put("query", query) else put("continuation", continuation)
        }
        val url = "$origin/youtubei/v1/search".toHttpUrlBuilder(config.key)
        val response = request(Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Origin", origin).header("Referer", "$origin/")
            .header("X-YouTube-Client-Name", "1").header("X-YouTube-Client-Version", config.version)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build(), "search")
        return parse(response, rankOffset)
    }

    private fun String.toHttpUrlBuilder(key: String): String =
        this + "?key=" + java.net.URLEncoder.encode(key, "UTF-8") + "&prettyPrint=false"

    private suspend fun request(request: Request, stage: String): String {
        currentCoroutineContext().ensureActive()
        if (System.currentTimeMillis() < blockedUntilMs.get()) {
            throw YouTubeWebHttpException(429, stage, hasRetryAfter = true)
        }
        return try {
            http.newCall(request).awaitSearchResponse {
                if (!it.isSuccessful) {
                    val retryAfter = it.header("Retry-After")
                    if (it.code == 429 || (it.code == 503 && retryAfter != null)) {
                        val now = System.currentTimeMillis()
                        val waitMs = retryAfter?.toLongOrNull()?.takeIf { seconds -> seconds >= 0 }
                            ?.let { seconds -> TimeUnit.SECONDS.toMillis(seconds) }
                            ?: it.headers.getDate("Retry-After")?.let { date -> (date.time - now).coerceAtLeast(0) }
                            ?: 30_000L
                        val until = now + waitMs.coerceAtMost(Long.MAX_VALUE - now)
                        blockedUntilMs.updateAndGet { previous -> maxOf(previous, until) }
                    }
                    throw YouTubeWebHttpException(it.code, stage, retryAfter != null)
                }
                it.body?.string() ?: throw IOException("Empty YouTube response body")
            }
        } catch (error: YouTubeWebHttpException) {
            throw error
        } catch (error: IOException) {
            throw YouTubeWebTransportException(stage, error)
        }
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

/** Keep cancellation connected to the call until the response body has been consumed. */
internal suspend fun <T> Call.awaitSearchResponse(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching { response.use(read) })
            }
        })
    }

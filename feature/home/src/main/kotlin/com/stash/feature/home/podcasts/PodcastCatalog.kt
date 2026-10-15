package com.stash.feature.home.podcasts

import android.content.Context
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class PodcastShow(
    val title: String,
    val author: String,
    val imageUrl: String?,
    val feedUrl: String,
    val catalogUrl: String? = null,
)

data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val published: String,
    val description: String,
    val imageUrl: String?,
)

internal fun podcastKey(raw: String): String =
    MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        .take(16).joinToString("") { "%02x".format(it) }

@Singleton
class PodcastCatalog @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = PodcastHttp.client.newBuilder()
        .callTimeout(java.time.Duration.ofSeconds(20)).build()
    private val prefs = context.getSharedPreferences("nonstop_podcasts", Context.MODE_PRIVATE)
    private val _followed = MutableStateFlow(readFollowed())
    val followed: StateFlow<List<PodcastShow>> = _followed

    fun toggleFollow(show: PodcastShow) {
        val new = if (_followed.value.any { it.feedUrl == show.feedUrl }) {
            _followed.value.filterNot { it.feedUrl == show.feedUrl }
        } else {
            listOf(show) + _followed.value
        }
        val json = JSONArray()
        new.forEach { json.put(JSONObject().apply {
            put("title", it.title); put("author", it.author)
            put("image", it.imageUrl); put("feed", it.feedUrl)
            put("catalog", it.catalogUrl)
        }) }
        if (prefs.edit().putString("followed", json.toString()).commit()) _followed.value = new
    }

    private fun readFollowed(): List<PodcastShow> = runCatching {
        val a = JSONArray(prefs.getString("followed", "[]") ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val item = a.optJSONObject(i) ?: return@mapNotNull null
            val feed = item.optString("feed")
            if (!validPodcastUrl(feed)) return@mapNotNull null
            PodcastShow(item.optString("title"), item.optString("author"),
                item.optString("image").takeIf { validPodcastUrl(it) }, feed,
                item.optString("catalog").takeIf { validPodcastUrl(it) })
        }
    }.getOrDefault(emptyList())

    /** Apple directory is used only to discover open RSS feed addresses, not to stream Apple content. */
    suspend fun search(term: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val raw = term.trim()
        if (validPodcastUrl(raw)) return@withContext listOf(readShowFromRss(raw).first)
        val q = raw.take(100)
        if (q.length < 2) return@withContext emptyList()
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&country=BR&limit=30&term=" +
            java.net.URLEncoder.encode(q, "UTF-8")
        val data = fetchLimited(url, MAX_CATALOG_BYTES)
        val results = JSONObject(String(data, Charsets.UTF_8)).optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
            val item = results.optJSONObject(i) ?: return@mapNotNull null
            val feed = item.optString("feedUrl")
            if (!validPodcastUrl(feed)) return@mapNotNull null
            PodcastShow(
                title = item.optString("collectionName").ifBlank { "Podcast" },
                author = item.optString("artistName"),
                imageUrl = item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") }
                    .takeIf { validPodcastUrl(it) },
                feedUrl = feed,
                catalogUrl = item.optString("collectionViewUrl").takeIf { validPodcastUrl(it) },
            )
        }.distinctBy { it.feedUrl }
    }

    suspend fun episodes(show: PodcastShow): Pair<PodcastShow, List<PodcastEpisode>> =
        withContext(Dispatchers.IO) { readShowFromRss(show.feedUrl, show) }

    private fun readShowFromRss(url: String, known: PodcastShow? = null): Pair<PodcastShow, List<PodcastEpisode>> {
        val bytes = fetchLimited(url, MAX_RSS_BYTES)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var depth = 0
        var showTitle = known?.title.orEmpty()
        var showAuthor = known?.author.orEmpty()
        var showImage = known?.imageUrl
        var title = ""
        var guid = ""
        var audio = ""
        var pubDate = ""
        var desc = ""
        var image: String? = null
        val episodes = ArrayList<PodcastEpisode>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT && episodes.size < 100) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        depth = 1; title = ""; guid = ""; audio = ""; pubDate = ""; desc = ""; image = null
                    } else if (depth == 1) {
                        when (tag) {
                            "title" -> title = parser.nextText().trim().take(500)
                            "guid", "id" -> guid = parser.nextText().trim().take(500)
                            "pubdate", "published", "updated" -> pubDate = parser.nextText().trim().take(100)
                            "description", "summary" -> if (desc.isBlank()) desc = parser.nextText().trim().take(1500)
                            "enclosure" -> audio = parser.getAttributeValue(null, "url").orEmpty()
                            "link" -> if (parser.getAttributeValue(null, "rel") == "enclosure") {
                                audio = parser.getAttributeValue(null, "href").orEmpty()
                            }
                            "image" -> image = parser.getAttributeValue(null, "href")?.takeIf(::validPodcastUrl)
                        }
                    } else if (depth == 0) {
                        when (tag) {
                            "title" -> if (showTitle.isBlank()) showTitle = parser.nextText().trim().take(500)
                            "author" -> if (showAuthor.isBlank()) showAuthor = parser.nextText().trim().take(200)
                            "image" -> showImage = parser.getAttributeValue(null, "href")
                                ?.takeIf(::validPodcastUrl) ?: showImage
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (depth == 1 &&
                    (parser.name.equals("item", true) || parser.name.equals("entry", true))) {
                    if (title.isNotBlank() && validPodcastUrl(audio)) {
                        episodes += PodcastEpisode(
                            id = podcastKey(url + "|" + guid.ifBlank { audio }),
                            title = title,
                            audioUrl = audio,
                            published = pubDate,
                            description = desc.replace(Regex("<[^>]*>"), " ").trim(),
                            imageUrl = image ?: showImage,
                        )
                    }
                    depth = 0
                }
            }
            parser.next()
        }
        return (known ?: PodcastShow(showTitle.ifBlank { "Podcast" }, showAuthor, showImage, url)) to episodes
    }

    private fun fetchLimited(url: String, max: Int): ByteArray {
        require(validPodcastUrl(url)) { "Only public HTTPS podcast feeds are supported" }
        val request = Request.Builder().url(url).header("User-Agent", "N0NStop-Podcasts/1.0").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Servidor retornou HTTP ${response.code}" }
            val body = response.body ?: error("Resposta vazia")
            check(body.contentLength() <= max) { "Feed maior que o limite permitido" }
            body.byteStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    check(out.size() + read <= max) { "Feed maior que o limite permitido" }
                    out.write(buf, 0, read)
                }
                return out.toByteArray()
            }
        }
    }

    companion object {
        private const val MAX_RSS_BYTES = 3 * 1024 * 1024
        private const val MAX_CATALOG_BYTES = 1024 * 1024
    }
}

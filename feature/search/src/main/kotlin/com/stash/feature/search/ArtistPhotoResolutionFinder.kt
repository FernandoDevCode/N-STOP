package com.stash.feature.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Searches public candidate catalogs and reverse-matches IMAGE PIXELS to the
 * Spotify reference. A provider's artist name alone is never enough to promote
 * its portrait. No API key, unofficial Lens scraping, download storage, or AI.
 *
 * Runs only when the official Spotify portrait URL is new. The repository
 * records the check so reopening the profile only revalidates Spotify itself.
 */
@Singleton
class ArtistPhotoResolutionFinder @Inject constructor(
    okHttpClient: OkHttpClient,
    private val deezer: DeezerArtistPhotoLookup,
) {
    private val http = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(3500, TimeUnit.MILLISECONDS)
        .build()

    suspend fun findBetter(artistName: String, spotifyPhotoUrl: String): String? =
        withContext(Dispatchers.IO) {
            if (artistName.isBlank() || !allowed(spotifyPhotoUrl)) return@withContext null
            val original = downloadPhoto(spotifyPhotoUrl) ?: return@withContext null
            val candidates = mutableListOf<String>()
            try {
                deezer.find(artistName)?.let(candidates::add)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Spotify still wins */ }
            try {
                candidates += commonsCandidates(artistName)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* still test Deezer if it is reachable */ }
            var best: Photo? = null
            for (url in candidates.asSequence().filter(::allowed).distinct().take(12)) {
                val next = downloadPhoto(url) ?: continue
                if (!ArtistPhotoPixelMatch.isHigherResolution(
                        original.width, original.height, next.width, next.height
                    ) || !ArtistPhotoPixelMatch.isSamePhoto(original.pixels, next.pixels)
                ) continue
                if (best == null || next.width.toLong() * next.height > best.width.toLong() * best.height) {
                    best = next.copy(url = url)
                }
            }
            best?.url
        }

    internal data class Photo(
        val width: Int, val height: Int, val pixels: IntArray,
        val url: String = "",
    )

    private fun downloadPhoto(url: String): Photo? {
        if (!allowed(url)) return null
        return try {
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.byteStream()?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_BYTES) return@use null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: return@use null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val width = bounds.outWidth
                val height = bounds.outHeight
                if (width !in 80..MAX_DIMENSION || height !in 80..MAX_DIMENSION ||
                    width.toLong() * height > MAX_PIXELS) return@use null
                val sample = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = when {
                        maxOf(width, height) > 2048 -> 8
                        maxOf(width, height) > 1024 -> 4
                        maxOf(width, height) > 512 -> 2
                        else -> 1
                    }
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sample)
                    ?: return@use null
                try {
                    val side = minOf(bitmap.width, bitmap.height)
                    val cropped = Bitmap.createBitmap(
                        bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side,
                    )
                    try {
                        val scaled = Bitmap.createScaledBitmap(cropped, 40, 40, true)
                        try {
                            val pixels = IntArray(40 * 40)
                            scaled.getPixels(pixels, 0, 40, 0, 0, 40, 40)
                            Photo(width, height, pixels)
                        } finally { if (scaled !== cropped) scaled.recycle() }
                    } finally { if (cropped !== bitmap) cropped.recycle() }
                } finally { bitmap.recycle() }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { null }
    }

    private fun commonsCandidates(name: String): List<String> {
        val query = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&format=json&formatversion=2&generator=search" +
            "&gsrnamespace=6&gsrlimit=12&gsrsearch=" +
            URLEncoder.encode(name.trim(), "UTF-8") +
            "&prop=imageinfo&iiprop=url%7Csize&iiurlwidth=1400"
        val req = Request.Builder().url(query).header("User-Agent", "Sonora/1.0 (Android personal music player)")
            .build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val pages = JSONObject(resp.body?.string().orEmpty())
                .optJSONObject("query")?.optJSONArray("pages") ?: return@use emptyList()
            (0 until pages.length()).mapNotNull { i ->
                val file = pages.optJSONObject(i)?.optJSONArray("imageinfo")?.optJSONObject(0)
                    ?: return@mapNotNull null
                val url = file.optString("thumburl").ifBlank { file.optString("url") }
                url.takeIf(::allowed)
            }
        }
    }

    private fun allowed(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            uri.host?.lowercase() in setOf(
                "i.scdn.co", "cdn-images.dzcdn.net", "e-cdns-images.dzcdn.net",
                "upload.wikimedia.org",
            ) && raw.length <= 2048
    }.getOrDefault(false)

    private companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val MAX_DIMENSION = 8192
        const val MAX_PIXELS = 24_000_000L
    }
}

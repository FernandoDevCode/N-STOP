package com.stash.feature.search

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.stash.core.common.ArtUrlUpgrader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val ARTWORK_HUE_BUCKETS = 24
private const val ARTWORK_TINT_CACHE_SIZE = 48
private val artworkTintLock = Any()
private val artworkTintCache = object : LinkedHashMap<String, Color>(ARTWORK_TINT_CACHE_SIZE, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean =
        size > ARTWORK_TINT_CACHE_SIZE
}

/**
 * Album art uses a different colour policy from artist photography: black,
 * white and lettering must not wash out a large red/yellow printed field.
 * Return a saturated but controlled ink to be composited as transparent glass.
 * No per-artist exceptions and no extraction on the Compose UI thread.
 */
internal fun featuredArtworkTintFromPixels(pixels: IntArray): Color? {
    if (pixels.isEmpty()) return null

    val votes = DoubleArray(ARTWORK_HUE_BUCKETS)
    val sine = DoubleArray(ARTWORK_HUE_BUCKETS)
    val cosine = DoubleArray(ARTWORK_HUE_BUCKETS)
    var opaque = 0
    var chromatic = 0

    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        opaque++

        val r = ((pixel ushr 16) and 255) / 255.0
        val g = ((pixel ushr 8) and 255) / 255.0
        val b = (pixel and 255) / 255.0
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val chroma = high - low
        if (high < .16 || chroma < .09) continue
        val saturation = chroma / high
        if (saturation < .23) continue

        val hue = (when (high) {
            r -> (g - b) / chroma + if (g < b) 6.0 else 0.0
            g -> (b - r) / chroma + 2.0
            else -> (r - g) / chroma + 4.0
        } * 60.0) % 360.0

        val bucket = (hue / 15.0).toInt().coerceIn(0, ARTWORK_HUE_BUCKETS - 1)
        val weight = .65 + .35 * saturation
        votes[bucket] += weight
        val radians = hue * PI / 180.0
        sine[bucket] += sin(radians) * weight
        cosine[bucket] += cos(radians) * weight
        chromatic++
    }

    // A tiny coloured detail on a grayscale sleeve is not a dominant hue.
    if (opaque == 0 || chromatic.toDouble() / opaque < .08) return null

    val winner = (0 until ARTWORK_HUE_BUCKETS).maxByOrNull { center ->
        votes[(center + ARTWORK_HUE_BUCKETS - 1) % ARTWORK_HUE_BUCKETS] +
            votes[center] + votes[(center + 1) % ARTWORK_HUE_BUCKETS]
    } ?: return null
    val neighbors = intArrayOf(
        (winner + ARTWORK_HUE_BUCKETS - 1) % ARTWORK_HUE_BUCKETS,
        winner, (winner + 1) % ARTWORK_HUE_BUCKETS,
    )
    val winningWeight = neighbors.sumOf { votes[it] }
    if (winningWeight / votes.sum() < .25) return null

    val hue = (Math.toDegrees(
        atan2(neighbors.sumOf { sine[it] }, neighbors.sumOf { cosine[it] }),
    ) + 360.0) % 360.0
    return artworkInk(hue)
}

/** Consistent chroma/value: transparency is controlled by the card, not here. */
private fun artworkInk(hue: Double): Color {
    val saturation = .82
    val value = .72
    val sector = (hue / 60.0).toInt() % 6
    val fraction = hue / 60.0 - floor(hue / 60.0)
    val p = value * (1 - saturation)
    val q = value * (1 - fraction * saturation)
    val t = value * (1 - (1 - fraction) * saturation)
    val (r, g, b) = when (sector) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }
    return Color(r.toFloat(), g.toFloat(), b.toFloat())
}

internal suspend fun featuredArtworkTintFromCover(context: Context, coverUrl: String?): Color? {
    if (coverUrl.isNullOrBlank()) return null
    synchronized(artworkTintLock) { artworkTintCache[coverUrl] }?.let { return it }

    return try {
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(coverUrl))
            .size(Size(128, 128))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return null

        val tint = withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            try {
                val pixels = IntArray(48 * 48)
                small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                featuredArtworkTintFromPixels(pixels)
            } finally {
                if (small !== bitmap) small.recycle()
            }
        }
        if (tint != null) synchronized(artworkTintLock) { artworkTintCache[coverUrl] = tint }
        tint
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }
}

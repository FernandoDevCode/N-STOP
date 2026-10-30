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

/**
 * Three distinct jobs: preserve the photo's colour near the hero, connect it to
 * the page, then leave a quiet dark reading surface. No artist-specific rules.
 */
internal data class ArtistAtmospherePalette(
    val hero: Color,
    val bridge: Color,
    val surface: Color,
)

internal val ArtistNeutralAtmosphere = Color(0xFF212224)
internal val ArtistBaseInk = Color(0xFF0E1115)
internal val ArtistNeutralPalette = ArtistAtmospherePalette(
    hero = Color(0xFF2C2D2F),
    bridge = Color(0xFF262729),
    surface = ArtistNeutralAtmosphere,
)

private const val HUE_BUCKETS = 24
private const val PHOTO_CACHE_SIZE = 48
private val photoCacheLock = Any()
private val photoPaletteCache = object : LinkedHashMap<String, ArtistAtmospherePalette>(
    PHOTO_CACHE_SIZE, 0.75f, true,
) {
    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, ArtistAtmospherePalette>?,
    ): Boolean = size > PHOTO_CACHE_SIZE
}

/**
 * Vote by occupied photo area, not highest saturation. Each 15-degree bucket
 * joins its neighbours, including the circular 345/0-degree red boundary.
 * Darkness is never an automatic veto: black silhouettes against an extensive
 * red background should still produce a red atmosphere.
 */
internal fun artistPaletteFromPixels(pixels: IntArray): ArtistAtmospherePalette {
    if (pixels.isEmpty()) return ArtistNeutralPalette

    val votes = DoubleArray(HUE_BUCKETS)
    val counts = IntArray(HUE_BUCKETS)
    val hueSin = DoubleArray(HUE_BUCKETS)
    val hueCos = DoubleArray(HUE_BUCKETS)
    var opaque = 0
    var chromatic = 0

    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        opaque++
        val red = ((pixel ushr 16) and 255) / 255.0
        val green = ((pixel ushr 8) and 255) / 255.0
        val blue = (pixel and 255) / 255.0
        val high = max(red, max(green, blue))
        val low = min(red, min(green, blue))
        val chroma = high - low
        if (high < 0.11 || chroma < 0.04 || chroma / high < 0.17) continue

        val hue = (when (high) {
            red -> (green - blue) / chroma + if (green < blue) 6.0 else 0.0
            green -> (blue - red) / chroma + 2.0
            else -> (red - green) / chroma + 4.0
        } * 60.0) % 360.0
        val bin = (hue / (360.0 / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
        // Every qualified pixel counts. A tiny vivid patch cannot outweigh
        // a much larger, moderately colourful background.
        val weight = 0.85 + 0.15 * (chroma / high)
        val radians = hue * PI / 180.0
        votes[bin] += weight
        counts[bin]++
        hueSin[bin] += sin(radians) * weight
        hueCos[bin] += cos(radians) * weight
        chromatic++
    }

    if (opaque == 0 || chromatic.toDouble() / opaque < 0.28) {
        return ArtistNeutralPalette
    }

    var winner = 0
    var strongest = -1.0
    for (center in 0 until HUE_BUCKETS) {
        val previous = (center + HUE_BUCKETS - 1) % HUE_BUCKETS
        val next = (center + 1) % HUE_BUCKETS
        val strength = votes[previous] + votes[center] + votes[next]
        if (strength > strongest) {
            strongest = strength
            winner = center
        }
    }

    val previous = (winner + HUE_BUCKETS - 1) % HUE_BUCKETS
    val next = (winner + 1) % HUE_BUCKETS
    val covered = counts[previous] + counts[winner] + counts[next]
    // Require meaningful photographic area and a coherent hue family.
    // Balanced multicolour photographs still get a stable deterministic vote.
    if (covered.toDouble() / opaque < 0.24 ||
        covered.toDouble() / chromatic < 0.38
    ) return ArtistNeutralPalette

    val x = hueCos[previous] + hueCos[winner] + hueCos[next]
    val y = hueSin[previous] + hueSin[winner] + hueSin[next]
    val hue = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    val coverage = (covered.toDouble() / opaque).coerceIn(0.0, 1.0)
    val depth = ((coverage - 0.24) / 0.52).coerceIn(0.0, 1.0)

    return ArtistAtmospherePalette(
        hero = artistHsv(hue, 0.49 + depth * 0.17, 0.30),
        bridge = artistHsv(hue, 0.36 + depth * 0.16, 0.23),
        surface = artistHsv(hue, 0.23 + depth * 0.12, 0.15),
    )
}

/** Legacy single-colour helper, kept for the original artist-colour tests. */
internal fun artistAtmosphereFromPixels(pixels: IntArray): Color =
    artistPaletteFromPixels(pixels).surface

/** Pure conversion so palette tests do not require Android framework calls. */
private fun artistHsv(hue: Double, saturation: Double, value: Double): Color {
    val sector = (hue / 60.0).toInt() % 6
    val fraction = hue / 60.0 - floor(hue / 60.0)
    val p = value * (1.0 - saturation)
    val q = value * (1.0 - fraction * saturation)
    val t = value * (1.0 - (1.0 - fraction) * saturation)
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

/**
 * Load a small copy from the existing Coil image loader; sample away from the
 * UI thread. Key by the actual photo URL, not artist name or current track.
 * Cache only successful reads so temporary network failures remain retryable.
 */
internal suspend fun artistPaletteFromPhoto(
    context: Context,
    photoUrl: String?,
): ArtistAtmospherePalette {
    if (photoUrl.isNullOrBlank()) return ArtistNeutralPalette
    synchronized(photoCacheLock) { photoPaletteCache[photoUrl] }?.let { return it }

    return try {
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(photoUrl))
            .size(Size(128, 128))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return ArtistNeutralPalette

        val palette = withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            try {
                val pixels = IntArray(48 * 48)
                small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                artistPaletteFromPixels(pixels)
            } finally {
                if (small !== bitmap) small.recycle()
            }
        }
        synchronized(photoCacheLock) { photoPaletteCache[photoUrl] = palette }
        palette
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        ArtistNeutralPalette
    }
}

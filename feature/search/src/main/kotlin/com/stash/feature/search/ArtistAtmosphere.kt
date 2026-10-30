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
import kotlin.math.max
import kotlin.math.min

/** This is scoped to the artist page, not the mini-player or Now Playing. */
internal val ArtistNeutralAtmosphere = Color(0xFF1B232D)
internal val ArtistBaseInk = Color(0xFF0E1115)

/**
 * Unlike the album palette, this measures *photo coverage*. A handful of vivid
 * orange pixels must not tint an otherwise dark, grey Slipknot band portrait.
 * Colours are always desaturated and darkened for readable white UI.
 */
internal fun artistAtmosphereFromPixels(pixels: IntArray): Color {
    if (pixels.isEmpty()) return ArtistNeutralAtmosphere
    val buckets = IntArray(12)
    val hueSums = DoubleArray(12)
    var opaque = 0
    var dark = 0
    var coloured = 0
    for (pixel in pixels) {
        if (pixel ushr 24 < 128) continue
        opaque++
        val r = ((pixel ushr 16) and 255) / 255.0
        val g = ((pixel ushr 8) and 255) / 255.0
        val b = (pixel and 255) / 255.0
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val delta = hi - lo
        val light = (hi + lo) * .5
        if (light < .33) dark++
        val sat = if (hi <= .00001) 0.0 else delta / hi
        if (sat < .24 || hi < .22 || light > .91) continue
        val hue = (when (hi) {
            r -> (g - b) / delta + if (g < b) 6.0 else 0.0
            g -> (b - r) / delta + 2
            else -> (r - g) / delta + 4
        } * 60.0) % 360.0
        val index = (hue / 30).toInt().coerceIn(0, 11)
        buckets[index]++
        hueSums[index] += hue
        coloured++
    }
    if (opaque == 0 || dark * 1.0 / opaque > .56 ||
        coloured * 1.0 / opaque < .42) return ArtistNeutralAtmosphere

    // Group adjacent buckets to avoid boundary instability (e.g. blue at 239°).
    val winner = buckets.indices.maxByOrNull { buckets[it] + buckets[(it + 1) % 12] }
        ?: return ArtistNeutralAtmosphere
    val next = (winner + 1) % 12
    val dominant = buckets[winner] + buckets[next]
    if (dominant.toDouble() / opaque < .32 ||
        dominant.toDouble() / coloured < .56) return ArtistNeutralAtmosphere

    val startHue = (hueSums[winner] / buckets[winner].coerceAtLeast(1))
    val nextHue = (hueSums[next] / buckets[next].coerceAtLeast(1))
    val continuousNext = if (next == 0 && winner == 11) nextHue + 360 else nextHue
    val hue = ((startHue * buckets[winner] +
        continuousNext * buckets[next]) / dominant) % 360
    // Warm scenes should feel deep burgundy/umber, never orange overlays.
    val warm = hue < 80 || hue >= 330
    return darkArtistHue(hue, if (warm) .19 else .34, if (warm) .18 else .20)
}

/** Pure colour conversion avoids Android SDK calls inside JVM unit tests. */
private fun darkArtistHue(hue: Double, saturation: Double, value: Double): Color {
    val sector = (hue / 60).toInt() % 6
    val fraction = hue / 60 - kotlin.math.floor(hue / 60)
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

internal suspend fun artistAtmosphereFromPhoto(
    context: Context,
    photoUrl: String?,
): Color {
    if (photoUrl.isNullOrBlank()) return ArtistNeutralAtmosphere
    return try {
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(photoUrl))
            .size(Size(128, 128))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return ArtistNeutralAtmosphere
        withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            try {
                val pixels = IntArray(48 * 48)
                small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                artistAtmosphereFromPixels(pixels)
            } finally {
                if (small !== bitmap) small.recycle()
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        ArtistNeutralAtmosphere
    }
}

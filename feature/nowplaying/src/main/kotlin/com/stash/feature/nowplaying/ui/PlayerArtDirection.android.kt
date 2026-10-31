package com.stash.feature.nowplaying.ui

import android.graphics.Bitmap

/** Small bounded process cache, shared between cover and lyrics presentations. */
private val artworkDirections = object : LinkedHashMap<String, PlayerArtDirection>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlayerArtDirection>?): Boolean =
        size > 64
}

/** Uses at most a 40 x 40 pixel sample; called off the UI thread. */
internal fun playerArtDirectionFromBitmap(bitmap: Bitmap, artKey: String): PlayerArtDirection {
    synchronized(artworkDirections) { artworkDirections[artKey]?.let { return it } }
    val scaled = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
    val resolved = try {
        val pixels = IntArray(40 * 40)
        scaled.getPixels(pixels, 0, 40, 0, 0, 40, 40)
        playerArtDirectionFromPixels(pixels, artKey)
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
    synchronized(artworkDirections) { artworkDirections[artKey] = resolved }
    return resolved
}



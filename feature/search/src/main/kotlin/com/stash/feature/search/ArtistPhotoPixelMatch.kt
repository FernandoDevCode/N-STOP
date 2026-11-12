package com.stash.feature.search

import kotlin.math.abs

/**
 * Strict same-photograph test over uniformly cropped 40x40 ARGB previews.
 *
 * Color difference AND local edge difference must be small. This is not face
 * identification: it refuses distinct shots of the same singer, logo/cover
 * guesses, and uncertain crops. False negatives keep the Spotify source.
 */
internal object ArtistPhotoPixelMatch {
    private const val SIDE = 40
    private const val PIXELS = SIDE * SIDE

    fun isSamePhoto(reference: IntArray, candidate: IntArray): Boolean {
        if (reference.size != PIXELS || candidate.size != PIXELS) return false
        var color = 0.0
        var edge = 0.0
        var texture = 0.0
        val lumaRef = DoubleArray(PIXELS)
        val lumaCandidate = DoubleArray(PIXELS)
        for (i in 0 until PIXELS) {
            val a = reference[i]
            val b = candidate[i]
            val ar = a ushr 16 and 255
            val ag = a ushr 8 and 255
            val ab = a and 255
            val br = b ushr 16 and 255
            val bg = b ushr 8 and 255
            val bb = b and 255
            color += (abs(ar - br) + abs(ag - bg) + abs(ab - bb)) / 765.0
            lumaRef[i] = (0.2126 * ar + 0.7152 * ag + 0.0722 * ab) / 255.0
            lumaCandidate[i] = (0.2126 * br + 0.7152 * bg + 0.0722 * bb) / 255.0
        }
        var edges = 0
        for (y in 0 until SIDE) for (x in 0 until SIDE - 1) {
            val i = y * SIDE + x
            val refDelta = lumaRef[i] - lumaRef[i + 1]
            val candidateDelta = lumaCandidate[i] - lumaCandidate[i + 1]
            edge += abs(refDelta - candidateDelta)
            texture += abs(refDelta)
            edges++
        }
        // A solid-color placeholder cannot establish same-photo identity.
        return texture / edges > 0.008 &&
            color / PIXELS <= 0.075 &&
            edge / edges <= 0.095
    }

    fun isHigherResolution(baseWidth: Int, baseHeight: Int, width: Int, height: Int): Boolean =
        baseWidth > 0 && baseHeight > 0 &&
            width >= baseWidth * 1.20 &&
            height >= baseHeight * 1.20 &&
            width.toLong() * height >= baseWidth.toLong() * baseHeight * 1.5
}

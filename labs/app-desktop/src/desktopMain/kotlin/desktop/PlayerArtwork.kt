package com.stash.feature.nowplaying.ui

import androidx.compose.runtime.*
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

@Composable
internal fun rememberNpArtRequest(artModel: Any?): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, artModel) {
        ImageRequest.Builder(context).data(artModel).size(640).precision(Precision.INEXACT).build()
    }
}
private val directionCache = object : LinkedHashMap<String, PlayerArtDirection>(64, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlayerArtDirection>?) = size > 64
}
@Composable
internal fun rememberDesktopArtDirection(model: String?): State<PlayerArtDirection> =
    produceState(PlayerArtDirection.Neutral, model) {
        value = PlayerArtDirection.Neutral
        if (model == null) return@produceState
        value = withContext(Dispatchers.IO) {
            synchronized(directionCache) { directionCache[model] } ?: runCatching {
                val bytes = if (model.startsWith("https://")) {
                    URI(model).toURL().openConnection().apply { connectTimeout = 5000; readTimeout = 10000 }
                        .getInputStream().use { it.readNBytes(12 * 1024 * 1024 + 1) }
                } else File(model).inputStream().use { it.readNBytes(12 * 1024 * 1024 + 1) }
                require(bytes.size <= 12 * 1024 * 1024)
                val image = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                    val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: error("Unsupported artwork format")
                    try {
                        reader.input = input
                        require(reader.getWidth(0).toLong() * reader.getHeight(0) <= 16000000L)
                        val params = reader.defaultReadParam.apply {
                            setSourceSubsampling((reader.getWidth(0) / 40).coerceAtLeast(1), (reader.getHeight(0) / 40).coerceAtLeast(1), 0, 0)
                        }
                        reader.read(0, params)
                    } finally { reader.dispose() }
                }
                val sample = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
                sample.createGraphics().run { try { drawImage(image, 0, 0, 40, 40, null) } finally { dispose() } }
                playerArtDirectionFromPixels(sample.getRGB(0, 0, 40, 40, null, 0, 40), model).also {
                    synchronized(directionCache) { directionCache[model] = it }
                }
            }.getOrElse { PlayerArtDirection.Neutral }
        }
    }

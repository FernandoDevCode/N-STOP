package com.stash.feature.nowplaying

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import nonstop.artlab.direction.*
import nonstop.artlab.features.*
import nonstop.artlab.renderer.*

internal data class AndroidArt(val recipe: ArtRecipe, val atmosphere: AtmosphereBitmaps?, val cover: ImageBitmap?)

/** Bounded content cache. Artwork identity never enters the director. Coil owns source image IO. */
internal object AndroidArtPipeline {
    private val mutex = Mutex()
    private val memory = object : LinkedHashMap<String, AndroidArt>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AndroidArt>?) = size > 3
    }
    fun fallback() = AndroidArt(baselineRecipe("missing"), null, null)

    suspend fun load(context: Context, source: Any?): AndroidArt = withContext(Dispatchers.IO) {
        if (source == null) return@withContext fallback()
        val request = ImageRequest.Builder(context).data(source).size(768).scale(Scale.FIT).allowHardware(false).build()
        val image = SingletonImageLoader.get(context).execute(request).image ?: return@withContext fallback()
        val cover = image.toBitmap()
        val scale = 256f / maxOf(cover.width, cover.height)
        val reduced = Bitmap.createScaledBitmap(cover, (cover.width * scale).toInt().coerceAtLeast(1),
            (cover.height * scale).toInt().coerceAtLeast(1), true)
        val pixels = IntArray(reduced.width * reduced.height)
        reduced.getPixels(pixels, 0, reduced.width, 0, 0, reduced.width, reduced.height)
        val bytes = ByteBuffer.allocate(pixels.size * 4 + 8).putInt(reduced.width).putInt(reduced.height)
        pixels.forEach(bytes::putInt)
        val hash = sha(bytes.array())
        val key = sha((hash + ClassicalDirector.VERSION + recipeJson.encodeToString(FeaturePolicy())).toByteArray())
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            memory[key]?.let { return@withLock it.copy(cover = cover.asImageBitmap()) }
            val directory = File(context.cacheDir, "player-v8").apply { mkdirs() }
            val file = File(directory, "$key.bin")
            val cached = runCatching {
                require(file.length() in 1..1_000_000)
                DataInputStream(file.inputStream().buffered()).use { input ->
                    require(input.readInt() == 1)
                    val encodedRecipe = input.readUTF()
                    val recipe = recipeJson.decodeFromString<ArtRecipe>(encodedRecipe)
                    require(recipe.isValid() && recipe.sourceImageHash == hash && recipe.modelVersion == "none")
                    val expected = input.readUTF()
                    val w = input.readInt(); val h = input.readInt()
                    require(w == 192 && h == 342)
                    val raw = ByteArray(w * h * 4); input.readFully(raw)
                    require(sha(encodedRecipe.toByteArray() + raw) == expected && input.read() == -1)
                    val buffer = ByteBuffer.wrap(raw)
                    recipe to PixelImage(w, h, IntArray(w * h) { buffer.int })
                }
            }.getOrNull()
            val (recipe, atmosphere) = cached ?: withContext(Dispatchers.Default) {
                val features = ClassicalFeatureExtractor().analyze(PixelImage(reduced.width, reduced.height, pixels))
                currentCoroutineContext().ensureActive()
                val r = ClassicalDirector.direct(features, hash)
                val worker = currentCoroutineContext()
                val composite = compositeAtmosphere(AtmospherePrecomputer.render(r, checkCancelled = { worker.ensureActive() }))
                currentCoroutineContext().ensureActive()
                r to composite
            }
            currentCoroutineContext().ensureActive()
            if (cached == null) runCatching {
                val raw = ByteBuffer.allocate(atmosphere.argb.size * 4)
                atmosphere.argb.forEach(raw::putInt)
                val temp = File(directory, "$key.tmp")
                DataOutputStream(temp.outputStream().buffered()).use { output ->
                    val encodedRecipe = recipeJson.encodeToString(recipe)
                    output.writeInt(1); output.writeUTF(encodedRecipe); output.writeUTF(sha(encodedRecipe.toByteArray() + raw.array()))
                    output.writeInt(atmosphere.width); output.writeInt(atmosphere.height); output.write(raw.array())
                }
                java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                directory.listFiles()?.filter { it.extension == "bin" }?.sortedByDescending { it.lastModified() }
                    ?.drop(32)?.forEach { it.delete() }
            }
            val bitmap = Bitmap.createBitmap(atmosphere.argb, atmosphere.width, atmosphere.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            // Android uses FINAL view only: retain one composite, not diagnostic masks.
            AndroidArt(recipe, AtmosphereBitmaps(bitmap, bitmap, bitmap, bitmap, bitmap), cover.asImageBitmap())
                .also { memory[key] = it.copy(cover = null) }
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

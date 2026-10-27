@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.stash.desktop

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.stash.core.ui.visual.*
import com.stash.preview.ReferenceArtwork
import com.stash.preview.VisualPreview
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

private val referenceBitmaps = mutableMapOf<String, ImageBitmap>()
internal fun loadReference(name: String): ImageBitmap = referenceBitmaps.getOrPut(name) {
    Image.makeFromEncoded(checkNotNull(object {}.javaClass.getResourceAsStream("/$name.png")).use { it.readBytes() }).toComposeImageBitmap()
}
@Composable internal fun DesktopVisualPreview() {
    VisualPreview(artwork = { key, modifier -> ReferenceArtwork(key, modifier, ::loadReference) })
}

internal fun captureVisualPreview() {
    val output = File("build/validation/remodel").apply { mkdirs() }
    val sizes = mapOf(VisualPage.Home to (311 to 710), VisualPage.Explore to (309 to 714),
        VisualPage.Search to (301 to 722), VisualPage.Library to (310 to 724))
    fun capture(name: String, page: VisualPage, width: Int, height: Int, status: VisualStatus = VisualStatus.Content,
        fontScale: Float = 1f, dark: Boolean = true, amoled: Boolean = false) {
        // Reference excludes the outer phone frame and OS status area; the report masks both.
        val scene = ImageComposeScene(width = width, height = height, density = Density(width / 390f, fontScale)) {
            VisualTheme(dark, amoled) { VisualBackground(Modifier.fillMaxSize()) {
                val topInset = when (page) { VisualPage.Search -> 50.dp; VisualPage.Library -> 46.dp; else -> 32.dp }
                Box(Modifier.fillMaxSize().padding(top = topInset, bottom = 6.dp)) {
                    VisualPreview(page, status, dark, amoled) { key, modifier -> ReferenceArtwork(key, modifier, ::loadReference) }
                }
            } }
        }
        try {
            scene.render().use { frame -> checkNotNull(frame.encodeToData(EncodedImageFormat.PNG)).use { File(output, "$name.png").writeBytes(it.bytes) } }
        } finally { scene.close() }
    }
    sizes.forEach { (page, size) -> capture(page.name.lowercase(), page, size.first, size.second) }
    for (page in listOf(VisualPage.Home, VisualPage.Explore, VisualPage.Search, VisualPage.Library)) {
        val scene = ImageComposeScene(width = 311, height = 50, density = Density(311 / 390f)) {
            VisualTheme { VisualBottomBar(page, null, VisualActions()) }
        }
        try { scene.render().use { frame -> checkNotNull(frame.encodeToData(EncodedImageFormat.PNG)).use {
            File(output, "navigation-${page.name.lowercase()}.png").writeBytes(it.bytes)
        } } } finally { scene.close() }
    }
    capture("small-home", VisualPage.Home, 280, 560)
    capture("large-font-home", VisualPage.Home, 310, 724, fontScale = 1.5f)
    capture("light-library", VisualPage.Library, 310, 724, dark = false)
    capture("amoled-library", VisualPage.Library, 310, 724, amoled = true)
    for (status in listOf(VisualStatus.Empty, VisualStatus.Loading, VisualStatus.Error)) {
        capture("library-${status.name.lowercase()}", VisualPage.Library, 310, 724, status)
    }
    println("Prévia visual em ${output.absolutePath}")
}

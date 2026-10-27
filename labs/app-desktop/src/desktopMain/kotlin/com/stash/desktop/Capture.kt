@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.stash.desktop

import androidx.compose.ui.ImageComposeScene
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import com.stash.core.model.Track

internal fun captureWorkbench() {
    val output = File("build/validation").apply { mkdirs() }
    for ((name, dark, amoled) in listOf(
        Triple("dark", true, false), Triple("light", false, false), Triple("amoled", true, true),
    )) {
        val scene = ImageComposeScene(width = 520, height = 1000) {
            DesktopWorkbench(initialDark = dark, initialAmoled = amoled)
        }
        try {
            val frame = scene.render()
            try {
                val png = checkNotNull(frame.encodeToData(EncodedImageFormat.PNG))
                png.use { File(output, "$name.png").writeBytes(it.bytes) }
            } finally { frame.close() }
        } finally { scene.close() }
    }
    println("Capturas Compose/Skia em ${output.absolutePath}")
}

internal fun captureNativeApp() {
    val output = File("build/validation").apply { mkdirs() }
    val temp = Files.createTempDirectory("nonstop-capture-")
    val controller = AppController(LibraryStore(temp.toFile()))
    try {
        for (page in listOf("home", "library", "search", "settings", "appearance", "accounts", "sync", "player")) {
            if (page == "player") controller.audio.setQueue(listOf(Track(id = 1,
                title = "Fixture de teste — UI", artist = "Sem áudio / sem capa de álbum", durationMs = 240000)), startPlaying = false)
            val scene = ImageComposeScene(width = 520, height = 1000) { DesktopApp(controller, initialPage = page) }
            try {
                val frame = scene.render()
                try { checkNotNull(frame.encodeToData(EncodedImageFormat.PNG)).use { File(output, "app-$page.png").writeBytes(it.bytes) } }
                finally { frame.close() }
            } finally { scene.close() }
        }
    } finally {
        controller.close(); Files.deleteIfExists(temp)
    }
    println("Captura real do app Desktop: ${File(output, "app-home.png").absolutePath}")
}

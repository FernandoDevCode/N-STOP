@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.stash.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.*
import com.stash.preview.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.roundToInt

internal data class VisualCase(
    val width: Int, val font: Float, val theme: String = "dark", val scenario: VisualScenario = VisualScenario.Approved,
    val page: VisualPage = VisualPage.Home, val density: Float = 1f, val height: Int = 900, val sideInset: Int = 0, val bottomInset: Int = 24,
) {
    val name get() = "${page.name.lowercase()}-${scenario.name.lowercase()}-${width}dp-font${(font * 100).roundToInt()}-$theme-d${density}-h$height-inset$sideInset" + if (bottomInset != 24) "-ime$bottomInset" else ""
}

internal fun visualCases(): List<VisualCase> = buildList {
    val widths = listOf(280, 320, 360, 390, 412, 480)
    for (width in widths) for (font in listOf(1f, 1.25f, 1.5f)) for (theme in listOf("dark", "light", "amoled"))
        add(VisualCase(width, font, theme))
    for (width in widths) for (font in listOf(1f, 1.5f)) for (theme in listOf("dark", "light", "amoled"))
        add(VisualCase(width, font, theme, VisualScenario.Dynamic))
    for (width in widths) for (scenario in listOf(VisualScenario.Many, VisualScenario.NoPlayer, VisualScenario.Loading, VisualScenario.Empty, VisualScenario.Error))
        add(VisualCase(width, 1.5f, scenario = scenario))
    for (width in listOf(280, 390, 480)) for (font in listOf(1f, 1.5f)) for (page in listOf(VisualPage.Explore, VisualPage.Search, VisualPage.Library))
        add(VisualCase(width, font, page = page))
    for (density in listOf(1.5f, 2f, 3f)) for (font in listOf(1f, 1.5f)) add(VisualCase(390, font, density = density))
    add(VisualCase(390, 1.5f, scenario = VisualScenario.Dynamic, density = 2f, sideInset = 24))
    add(VisualCase(280, 1.5f, scenario = VisualScenario.Dynamic, height = 560))
    add(VisualCase(280, 1.5f, scenario = VisualScenario.NoPlayer, height = 560))
    for (page in VisualPage.entries) for (density in listOf(1f, 2f)) for (font in listOf(1f, 1.5f))
        add(VisualCase(390, font, page = page, density = density))
    add(VisualCase(390, 1.5f, page = VisualPage.Search, bottomInset = 300))
}.distinctBy { it.name }

/** Actual dp is independent of pixels: pixel width = widthDp * density, never widthDp / 390. */
internal fun captureVisualSystem() {
    val output = File("build/validation/sonora-system/matrix").apply { mkdirs() }
    val completed = mutableListOf<String>()
    for ((index, case) in visualCases().withIndex()) {
        val pixelsW = (case.width * case.density).roundToInt()
        val pixelsH = (case.height * case.density).roundToInt()
        val scene = ImageComposeScene(width = pixelsW, height = pixelsH, density = Density(case.density, case.font)) {
            VisualTheme(case.theme != "light", case.theme == "amoled") { VisualBackground(Modifier.fillMaxSize()) {
                // Simulate safe areas once; the Android host applies real WindowInsets.systemBars once.
                Box(Modifier.fillMaxSize().padding(horizontal = case.sideInset.dp).padding(top = 24.dp, bottom = case.bottomInset.dp)) {
                    VisualPreview(case.page, dark = case.theme != "light", amoled = case.theme == "amoled", scenario = case.scenario,
                        artwork = { key, modifier -> ReferenceArtwork(key, modifier, ::loadReference) })
                }
            } }
        }
        try {
            scene.render().use { frame -> checkNotNull(frame.encodeToData(EncodedImageFormat.PNG)).use {
                File(output, "${case.name}.png").writeBytes(it.bytes)
            } }
            completed += """{"file":"${case.name}.png","widthDp":${case.width},"heightDp":${case.height},"density":${case.density},"pixelWidth":$pixelsW,"pixelHeight":$pixelsH,"fontScale":${case.font},"theme":"${case.theme}","scenario":"${case.scenario}","page":"${case.page}","topInsetDp":24,"bottomInsetDp":${case.bottomInset},"sideInsetDp":${case.sideInset}}"""
            File(output, "matrix.json").writeText(completed.joinToString(",\n", "[\n", "\n]"))
        } finally { scene.close() }
        if ((index + 1) % 12 == 0) println("Visual matrix: ${index + 1}/${visualCases().size}")
    }
    println("Visual matrix completed: ${completed.size} captures in ${output.absolutePath}")
}

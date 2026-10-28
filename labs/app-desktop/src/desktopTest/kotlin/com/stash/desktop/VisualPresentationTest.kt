package com.stash.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.stash.core.ui.visual.*
import com.stash.preview.VisualPreview
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisualPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewNavigatesFiltersSearchAndTransportWithoutAnAppController() {
        compose.setContent { VisualPreview(artwork = { _, modifier -> VisualCover(null, modifier) }) }
        compose.onNodeWithText("eternal return").assertIsDisplayed()
        compose.onNodeWithText("Músicas", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Pausar").performClick()
        compose.onNodeWithContentDescription("Reproduzir").assertIsDisplayed()
        compose.onNodeWithTag("visual-tab-Search").performClick()
        compose.onNodeWithText("Explorar tudo").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Radiohead")
        compose.onNode(hasSetTextAction()).assertTextContains("Radiohead")
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithText("Busca de demonstração: Radiohead").assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        for (page in VisualPage.entries) {
            compose.onNodeWithTag("visual-tab-${page.name}").performClick().assertIsSelected()
        }
        compose.onNodeWithTag("visual-tab-Library").performClick()
        compose.onNodeWithText("Músicas Curtidas").assertIsDisplayed()
    }

    @Test fun contentEventsKeepOpaqueIdsAndDoNotTriggerParentOnNestedMore() {
        val opened = mutableListOf<String>()
        val menu = mutableListOf<String>()
        compose.setContent { VisualTheme { VisualScreen(
            VisualScreenState(VisualPage.Library, rows = listOf(VisualCard("opaque:8", "Uma faixa", "Artista"))),
            VisualActions(onCard = { opened += it }, onMore = { menu += it })) } }
        compose.onNodeWithContentDescription("Opções de Uma faixa").performClick()
        assertEquals(listOf("opaque:8"), menu)
        assertTrue(opened.isEmpty())
        compose.onNodeWithText("Uma faixa").performClick()
        assertEquals(listOf("opaque:8"), opened)
    }

    @Test fun errorHasRetryAndLongRowsRemainReachableInSmallViewport() {
        var failure by mutableStateOf(true)
        var chrome by mutableStateOf(false)
        var theme by mutableIntStateOf(0)
        var selectedPage by mutableStateOf(VisualPage.Radio)
        val longTitle = "Título muito longo que precisa quebrar e continuar acessível sem cobrir os controles"
        val longFilter = "Seleção de músicas com nome longo"
        compose.setContent {
            if (chrome) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    VisualTheme(dark = theme != 1, amoled = theme == 2) {
                        Column(Modifier.width(280.dp).testTag("refinement-chrome")) {
                            VisualFilters(listOf("Todas", "Músicas", "Podcasts", longFilter), "Todas", {})
                            Spacer(Modifier.height(12.dp))
                            VisualBottomBar(selectedPage, null, VisualActions(onPage = { selectedPage = it }))
                        }
                    }
                }
            } else VisualTheme {
                Box(Modifier.width(280.dp).height(500.dp)) {
                    VisualScreen(VisualScreenState(VisualPage.Library,
                        status = if (failure) VisualStatus.Error else VisualStatus.Content,
                        rows = listOf(VisualCard("long", longTitle, "Um artista com nome muito longo"))),
                        VisualActions(onRetry = { failure = false }))
                }
            }
        }
        compose.onNodeWithText("Tentar novamente").performClick()
        compose.onNodeWithText(longTitle).assertIsDisplayed()
        compose.onNodeWithContentDescription("Opções de $longTitle").assertIsDisplayed()
        compose.runOnIdle { chrome = true }
        for (mode in 0..2) {
            compose.runOnIdle { theme = mode }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
            compose.onNodeWithText(longFilter).assertIsDisplayed()
            for (label in listOf(longFilter, "Início", "Explorar", "Rádio", "Buscar", "Biblioteca")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Missing text layout: $label", layouts.isNotEmpty())
                val layout = layouts.single()
                if (label != longFilter) assertEquals("Wrapped navigation label: $label", 1, layout.lineCount)
                for (line in 0 until layout.lineCount) {
                    assertFalse("Ellipsized label: $label", layout.isLineEllipsized(line))
                    assertTrue("Clipped text in theme $mode: $label", layout.getLineRight(line) <= layout.size.width + .5f)
                }
                assertFalse("Clipped height: $label", layout.didOverflowHeight)
            }
            for (page in VisualPage.entries) {
                compose.onNodeWithTag("visual-tab-${page.name}").performClick().assertIsSelected()
                val pixels = compose.onNodeWithTag("refinement-chrome").captureToImage().toPixelMap()
                val bitmap = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) bitmap.setRGB(x, y, pixels[x, y].toArgb())
                val folder = java.io.File("build/validation/local-refinement/compact").apply { mkdirs() }
                javax.imageio.ImageIO.write(bitmap, "png", java.io.File(folder, "theme-$mode-${page.name.lowercase()}.png"))
            }
        }
    }
}

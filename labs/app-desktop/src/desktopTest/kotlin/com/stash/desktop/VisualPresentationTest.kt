package com.stash.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
        val longTitle = "Título muito longo que precisa quebrar e continuar acessível sem cobrir os controles"
        compose.setContent { VisualTheme { Box(Modifier.width(280.dp).height(500.dp)) {
            VisualScreen(VisualScreenState(VisualPage.Library,
                status = if (failure) VisualStatus.Error else VisualStatus.Content,
                rows = listOf(VisualCard("long", longTitle, "Um artista com nome muito longo"))),
                VisualActions(onRetry = { failure = false }))
        } } }
        compose.onNodeWithText("Tentar novamente").performClick()
        compose.onNodeWithText(longTitle).assertIsDisplayed()
        compose.onNodeWithContentDescription("Opções de $longTitle").assertIsDisplayed()
    }
}

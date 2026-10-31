package com.stash.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.*
import com.stash.preview.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisualAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chromeRemainsReadableAcrossActualDpWidthsFontsAndThemes() {
        var width by mutableIntStateOf(390)
        var font by mutableFloatStateOf(1f)
        var theme by mutableIntStateOf(0)
        var selected by mutableStateOf(VisualPage.Home)
        val long = "Filtro com texto extremamente longo para testar acomodação"
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, font)) {
                VisualTheme(theme != 1, theme == 2) {
                    Column(Modifier.width(width.dp).testTag("viewport")) {
                        VisualHeader(VisualActions(onCast = {}, onNotifications = {}))
                        VisualFilters(listOf("Todas", "Músicas", "Podcasts", long), "Todas", {})
                        VisualBottomBar(selected, null, VisualActions(onPage = { selected = it }))
                    }
                }
            }
        }
        for (w in listOf(280, 320, 360, 390, 412, 480)) for (f in listOf(1f, 1.25f, 1.5f)) for (mode in 0..2) {
            compose.runOnIdle { width = w; font = f; theme = mode }
            compose.onNodeWithTag("viewport").assertWidthIsEqualTo(w.dp)
            for (text in listOf("NØNSTOP", "Início", "Explorar", "Rádio", "Buscar", "Biblioteca")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val result = layouts.single()
                assertEquals("Wrapped $text at $w/$f/$mode", 1, result.lineCount)
                assertFalse("Clipped height $text at $w/$f/$mode", result.didOverflowHeight)
                assertFalse("Ellipsis $text at $w/$f/$mode", result.isLineEllipsized(0))
                assertTrue("Clipped width $text at $w/$f/$mode", result.getLineRight(0) <= result.size.width + .5f)
            }
            compose.onNodeWithContentDescription("Configurações").assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
            compose.onNodeWithText(long).assertIsDisplayed()
            if (f == 1.5f && mode == 0) for (page in VisualPage.entries)
                compose.onNodeWithTag("visual-tab-${page.name}").performClick().assertIsSelected()
            compose.onNodeWithTag("visual-miniplayer").assertDoesNotExist()
        }
    }

    @Test fun heroMovesOnlyCopyWhenSpaceIsInsufficientAndKeepsItsArtworkViewport() {
        var stress by mutableStateOf(false)
        val long = "Um título extremamente longo com múltiplos artistas e versões que deve continuar legível"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (stress) 1.5f else 1f)) {
                VisualTheme { Box(Modifier.width(if (stress) 280.dp else 390.dp).height(700.dp)) {
                    val state = VisualFixture.screen(VisualPage.Home).copy(hero = if (stress)
                        VisualHero(VisualCard("long", long, long), description = long) else VisualFixture.screen(VisualPage.Home).hero)
                    VisualScreen(state, VisualActions(), artwork = { _, modifier -> Box(modifier) })
                } }
            }
        }
        val originalArt = compose.onNodeWithTag("visual-hero-artwork").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("visual-hero-artwork").assertHeightIsEqualTo(304.dp)
        assertTrue(compose.onNodeWithTag("visual-hero-metadata").getUnclippedBoundsInRoot().top < originalArt.bottom)
        compose.runOnIdle { stress = true }
        compose.onNodeWithTag("visual-hero-artwork").assertHeightIsEqualTo(304.dp)
        val art = compose.onNodeWithTag("visual-hero-artwork").getUnclippedBoundsInRoot()
        val metadata = compose.onNodeWithTag("visual-hero-metadata").getUnclippedBoundsInRoot()
        assertTrue("Overflow copy should be below the unchanged photo", metadata.top >= art.bottom)
        compose.onNodeWithText("Ouvir agora").performScrollTo().assertIsDisplayed()
    }

    @Test fun dynamicFixturesAreIsolatedAndEmptyRailsDoNotReserveSpace() {
        val dynamic = VisualScenario.Many.screen(VisualPage.Home)
        assertEquals(60, dynamic.sections.first { it.id == "dev-covers" }.cards.size)
        assertTrue(dynamic.sections.any { it.cards.any { c -> c.artwork == null } })
        assertTrue(dynamic.sections.any { it.cards.any { c -> c.subtitle.isEmpty() } })
        assertNull(VisualScenario.NoPlayer.player())
        compose.setContent { VisualTheme { Box(Modifier.width(390.dp).height(700.dp)) {
            VisualScreen(dynamic.copy(hero = null), VisualActions(), artwork = { _, modifier -> Box(modifier) })
        } } }
        compose.onNodeWithText("Seção vazia").assertDoesNotExist()
        compose.onNodeWithText("Conteúdo dinâmico com um título de seção muito longo").assertIsDisplayed()
        compose.onNodeWithTag("visual-rail-dev-covers").performScrollToIndex(59)
        compose.onNode(hasText("Faixa 59") and hasAnyAncestor(hasTestTag("visual-rail-dev-covers"))).assertIsDisplayed()
    }

    @Test fun miniplayerFitsSafeAreasAndItsAbsenceReleasesExactlyItsOwnSpace() {
        var width by mutableIntStateOf(390)
        var font by mutableFloatStateOf(1f)
        var hasPlayer by mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                VisualTheme {
                    Column(Modifier.width(width.dp).height(500.dp).padding(horizontal = 12.dp, vertical = 24.dp)) {
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("safe-body"))
                        VisualBottomBar(VisualPage.Home, if (hasPlayer) VisualScenario.Dynamic.player()?.let {
                            if (font == 1.5f) it.copy(card = it.card.copy(subtitle = "")) else it
                        } else null, VisualActions(), artwork = { _, modifier -> Box(modifier) })
                    }
                }
            }
        }
        for (w in listOf(280, 320, 360, 390, 412, 480)) for (f in listOf(1f, 1.5f)) {
            compose.runOnIdle { width = w; font = f; hasPlayer = true }
            compose.onNodeWithTag("visual-miniplayer").assertHeightIsEqualTo(52.dp)
            val mini = compose.onNodeWithTag("visual-miniplayer").getUnclippedBoundsInRoot()
            val nav = compose.onNodeWithTag("visual-tab-Home").getUnclippedBoundsInRoot()
            assertTrue(mini.bottom <= nav.top)
            assertEquals(476f, nav.bottom.value, .1f)
            val beforeBounds = compose.onNodeWithTag("safe-body").getUnclippedBoundsInRoot()
            val before = beforeBounds.bottom - beforeBounds.top
            compose.runOnIdle { hasPlayer = false }
            compose.onNodeWithTag("visual-miniplayer").assertDoesNotExist()
            val afterBounds = compose.onNodeWithTag("safe-body").getUnclippedBoundsInRoot()
            val after = afterBounds.bottom - afterBounds.top
            assertEquals(52f, (after - before).value, .1f)
        }
    }

    @Test fun categoryLabelsNeverDrawHalfALineAtLargeFont() {
        var width by mutableIntStateOf(280)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                VisualTheme { Box(Modifier.width(width.dp).height(600.dp)) {
                    VisualScreen(VisualScreenState(VisualPage.Explore,
                        categories = listOf(VisualCard("category", "Música Brasileira", artwork = null))),
                        VisualActions(), artwork = { _, modifier -> Box(modifier) })
                } }
            }
        }
        for (w in listOf(280, 390, 480)) {
            compose.runOnIdle { width = w }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Música Brasileira", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertTrue("Partial line at width $w", layout.multiParagraph.height <= layout.size.height + .5f)
            assertEquals("Música Brasileira", layouts.single().layoutInput.text.text)
        }
    }
}

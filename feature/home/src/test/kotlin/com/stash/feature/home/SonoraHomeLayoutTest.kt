package com.stash.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters

/** Android Compose layouts, not a Desktop preview. No production fixtures or network calls. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w480dp-h960dp-mdpi")
class SonoraHomeLayoutTest(private val width: Int, private val scale: Float, private val theme: String) {
    @get:Rule val compose = createComposeRule()

    @Test fun filtersAndEditorialContentRemainUsable() {
        var tab by mutableIntStateOf(0)
        var plays = 0
        var opens = 0
        var creates = 0
        compose.setContent {
            VisualTheme(dark = theme != "light", amoled = theme == "amoled") {
                SonoraHomeTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                        Column(Modifier.requiredWidth(width.dp).height(800.dp).testTag("viewport")
                            .verticalScroll(rememberScrollState())) {
                            NonstopMediaFilters(tab, { tab = it })
                            SonoraHeroCard("Descoberta disponível", "Um título editorial muito longo para testar a adaptação da Home",
                                "Metadados reais podem ocupar mais de uma linha", null,
                                onPlay = { plays++ }, onOpen = { opens++ }, onCreateMix = { creates++ },
                                modifier = Modifier.padding(horizontal = 20.dp))
                            SonoraSectionTitle("Uma seção com título muito longo", actionText = "Mostrar todos os 120", onActionClick = {})
                            SonoraContentCard(VisualCard("test", "Um título extenso para a capa indisponível", ""), {})
                            Text("Último conteúdo", Modifier.padding(bottom = 154.dp))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("viewport").assertWidthIsEqualTo(width.dp)
        compose.onNodeWithText("Música").assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Audiobooks").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Podcasts").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Música").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Ouvir agora").performScrollTo().performClick()
        compose.onNodeWithText("Abrir playlist").performScrollTo().performClick()
        compose.onNodeWithText("Criar mix").performScrollTo().performClick()
        compose.runOnIdle {
            check(plays == 1 && opens == 1 && creates == 1)
        }
        compose.onNodeWithText("Último conteúdo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue ouvindo").assertDoesNotExist()
    }

    companion object {
        @JvmStatic @Parameters(name = "{0}dp-font{1}-{2}")
        fun sizes(): List<Array<Any>> = listOf(280, 320, 360, 390, 412, 480).flatMap { width ->
            listOf(1f, 1.25f, 1.5f).flatMap { scale ->
                listOf("dark", "light", "amoled").map { theme -> arrayOf<Any>(width, scale, theme) }
            }
        }
    }
}

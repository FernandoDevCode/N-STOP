package com.stash.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class SharedUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun originalSettingsControlsWorkOnDesktop() {
        compose.setContent { DesktopWorkbench() }
        compose.onAllNodes(isToggleable())[0].assertIsOn().performClick().assertIsOff()
        compose.onAllNodes(isToggleable())[1].assertIsNotEnabled()
        compose.onAllNodes(isToggleable())[0].performClick().assertIsOn()
        compose.onAllNodes(isToggleable())[1].performClick().assertIsOn()
        compose.onNodeWithText("Estado da migração").performScrollTo().performClick()
        compose.onNodeWithText("Compartilhados:", substring = true).assertExists()
    }
}

package com.stash.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.stash.core.ui.theme.StashTheme
import com.stash.core.ui.components.SectionHeader
import com.stash.feature.settings.components.*

fun main(args: Array<String>) {
    if ("--capture-visual" in args) {
        System.setProperty("java.awt.headless", "true")
        captureVisualPreview(); return
    }
    if ("--probe-shared-catalog" in args) { probeSharedCatalog(); return }
    if ("--probe-online" in args) { probeCatalog(); return }
    if ("--capture" in args) {
        System.setProperty("java.awt.headless", "true")
        captureWorkbench()
        return
    }
    if ("--capture-app" in args) {
        System.setProperty("java.awt.headless", "true")
        captureNativeApp()
        return
    }
    application {
        val preview = "--visual-preview" in args
        val controller = remember { if (preview) null else AppController() }
        DisposableEffect(controller) { onDispose { controller?.close() } }
        val windowState = rememberWindowState(width = 390.dp, height = 900.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = if (preview) "NØNSTOP · Prévia visual" else "NØNSTOP",
            state = windowState,
        ) {
            CompositionLocalProvider(LocalDesktopWindowVisible provides !windowState.isMinimized) {
                if (preview) DesktopVisualPreview()
                else if ("--workbench" in args) DesktopWorkbench() else DesktopApp(checkNotNull(controller))
            }
        }
    }
}

/** Development host, not a replacement for any existing Android screen. */
@Composable
fun DesktopWorkbench(initialDark: Boolean = true, initialAmoled: Boolean = false) {
    var dark by remember { mutableStateOf(initialDark) }
    var amoled by remember { mutableStateOf(initialAmoled) }
    var fontScale by remember { mutableFloatStateOf(1f) }
    var details by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        StashTheme(darkTheme = dark, amoled = amoled) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("NØNSTOP", style = MaterialTheme.typography.headlineLarge)
                    Text("Base Desktop · componentes do app atual", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Migração em andamento. Esta janela valida componentes compartilhados; " +
                            "as telas e os serviços do app completo ainda não foram migrados.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SectionHeader("Aparência")
                    SettingsGroupCard(rows = listOf(
                        { SettingsToggleRow("Tema escuro", dark, { dark = it }) },
                        { SettingsToggleRow("Preto AMOLED", amoled, { amoled = it }, enabled = dark) },
                        {
                            SettingsSliderRow(
                                title = "Tamanho da fonte", valueLabel = "${(fontScale * 100).toInt()}%",
                                value = fontScale, onValueChange = { fontScale = it },
                                onValueChangeFinished = {}, valueRange = 1f..1.5f, steps = 4,
                                onReset = { fontScale = 1f }, resetEnabled = fontScale != 1f,
                            )
                        },
                    ))
                    SettingsGroupCard(rows = listOf({
                        SettingsNavRow(
                            title = if (details) "Ocultar estado da migração" else "Estado da migração",
                            subtitle = "Código original compilado para Windows",
                            onClick = { details = !details },
                        )
                    }))
                    if (details) {
                        Text(
                            "Compartilhados: modelos de domínio, cores, tema, tipografia, formas e controles acima.\n\n" +
                                "Pendentes: telas Home/Explore/Radio/Library/Search, player, fila, letras, " +
                                "banco de dados, contas, streaming, downloads, sync e integrações do sistema.\n\n" +
                                "Alterações nesta janela são locais à sessão. Não alteram suas preferências Android.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

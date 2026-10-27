package com.stash.core.ui.ambient

import androidx.compose.runtime.Composable
import java.awt.Toolkit

@Composable
fun rememberReducedMotion(): Boolean =
    Toolkit.getDefaultToolkit().getDesktopProperty("win.clientAreaAnimation") == false

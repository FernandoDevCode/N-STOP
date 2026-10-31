package com.stash.feature.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StashVinylLogo(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Image(painterResource(if (com.stash.core.ui.theme.LocalIsDarkTheme.current)
        com.stash.core.ui.R.drawable.nonstop_wordmark else com.stash.core.ui.R.drawable.nonstop_wordmark_light),
        "NØNSTOP", modifier.size(size))
}

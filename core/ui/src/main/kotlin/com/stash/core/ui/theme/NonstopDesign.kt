package com.stash.core.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.stash.core.ui.R

/** Fixed shell colors; album extraction is reserved for transport progress. */
object NonstopDesign {
    val Background = Color(0xFF0E1115)
    val Graphite = Color(0xFF191D23)
    val Violet = Color(0xFFD4D8E0)
    val ActiveInk = Color(0xFFF4F5F7)
    val Secondary = Color(0xFFB4B8C4)
}

@Composable
fun NonstopBrand(modifier: Modifier = Modifier) {
    Image(painterResource(if (LocalIsDarkTheme.current) R.drawable.nonstop_wordmark else R.drawable.nonstop_wordmark_light), "NØNSTOP",
        modifier.width(146.dp).height(38.dp))
}

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
    val Background = Color(0xFF08090B)
    val Graphite = Color(0xFF0C111A)
    val Violet = Color(0xFFA25CFF)
    val ActiveInk = Color(0xFFF6EAFF)
    val Secondary = Color(0xFFADB7CE)
}

@Composable
fun NonstopBrand(modifier: Modifier = Modifier) {
    Image(painterResource(if (LocalIsDarkTheme.current) R.drawable.nonstop_wordmark else R.drawable.nonstop_wordmark_light), "NØNSTOP",
        modifier.width(146.dp).height(38.dp))
}

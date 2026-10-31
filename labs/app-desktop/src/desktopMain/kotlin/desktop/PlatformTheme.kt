package com.stash.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// Same checked-in font bytes used by Android; only resource loading differs.
val SpaceGrotesk = FontFamily(
    Font("space_grotesk_semibold.ttf", FontWeight.SemiBold),
    Font("space_grotesk_bold.ttf", FontWeight.Bold),
)
val Inter = FontFamily(
    Font("inter_regular.ttf", FontWeight.Normal),
    Font("inter_medium.ttf", FontWeight.Medium),
    Font("inter_semibold.ttf", FontWeight.SemiBold),
)

@Composable
internal fun UpdatePlatformSystemBars(darkTheme: Boolean) = Unit

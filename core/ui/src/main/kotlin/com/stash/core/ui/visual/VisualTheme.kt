package com.stash.core.ui.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stash.core.ui.theme.Inter
import com.stash.core.ui.theme.StashTypography

/** Measured against the October 2026 screenshot references at a 390dp viewport. */
val LocalVisualDark = staticCompositionLocalOf { true }
val LocalVisualAmoled = staticCompositionLocalOf { false }
object VisualDesign {
    val Background: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
    val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Border: Color @Composable get() = MaterialTheme.colorScheme.outline
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val Gutter = 20.dp
    val Gap = 10.dp
    val Heading = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp)
    val scheme = darkColorScheme(primary = Color(0xFFE4DFFF), onPrimary = Color(0xFF101116),
        background = Color(0xFF101116), onBackground = Color(0xFFF2F1F7), surface = Color(0xFF22222F), onSurface = Color(0xFFF2F1F7),
        surfaceVariant = Color(0xFF22222F), onSurfaceVariant = Color(0xFFB3B1C8), outline = Color(0xFF414052),
        secondary = Color(0xFFAAA2D4), onSecondary = Color(0xFF101116))
    val lightScheme = lightColorScheme(primary = Color(0xFF62517F), onPrimary = Color.White,
        background = Color(0xFFF3F1F7), onBackground = Color(0xFF24212D), surface = Color(0xFFE8E4F0),
        onSurface = Color(0xFF24212D), surfaceVariant = Color(0xFFE4DFEB), onSurfaceVariant = Color(0xFF625B72), outline = Color(0xFFC0B6CE))
}

@Composable fun VisualTheme(dark: Boolean = true, amoled: Boolean = false, content: @Composable () -> Unit) {
    val scheme = if (!dark) VisualDesign.lightScheme else if (amoled) VisualDesign.scheme.copy(background = Color.Black) else VisualDesign.scheme
    MaterialTheme(colorScheme = scheme, typography = StashTypography.copy(
        titleMedium = VisualDesign.Heading, bodyMedium = VisualDesign.Body,
        bodySmall = VisualDesign.Caption, labelSmall = VisualDesign.Caption,
    )) { CompositionLocalProvider(LocalContentColor provides VisualDesign.Ink,
        LocalVisualDark provides dark, LocalVisualAmoled provides (dark && amoled), content = content) }
}

@Composable fun VisualBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(VisualDesign.Background)) {
        if (LocalVisualDark.current && !LocalVisualAmoled.current) Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            listOf(Color(0xFF242532), Color(0xFF191A23), Color.Transparent),
            center = Offset(160f, -90f), radius = 850f)))
        content()
    }
}

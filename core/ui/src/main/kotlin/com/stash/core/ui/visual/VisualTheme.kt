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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
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
    val Brand = TextStyle(fontFamily = Inter, fontSize = 21.sp, letterSpacing = 3.2.sp, fontWeight = FontWeight.Normal)
    val PageTitle = Heading.copy(fontSize = 20.sp)
    val HeroTitle = Heading.copy(fontSize = 27.sp, lineHeight = 32.sp)
    val HeroArtist = Heading.copy(fontSize = 16.sp)
    val CardTitle = Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp)
    val CardCaption = Caption.copy(fontSize = 10.sp, letterSpacing = 0.sp)
    val NavigationLabel = Caption.copy(fontSize = 10.sp)

    // A small vocabulary, preserving the approved 390dp presentation.
    object Space {
        val Tiny = 3.dp; val Small = 8.dp; val Medium = 12.dp
        val Section = 24.dp; val Rail = 10.dp
    }
    object Size {
        val Chrome = 62.dp; val Control = 40.dp; val NavigationIcon = 22.dp
        val Hero = 304.dp; val HeroPadding = 18.dp
        val CardWidth = 110.dp; val CardMinimum = 168.dp; val CardArtwork = 82.dp
        val AlbumWidth = 104.dp; val RecentWidth = 80.dp; val MiniPlayer = 52.dp
    }
    object Radius {
        val Card = 8.dp; val Hero = 11.dp; val Filter = 12.dp; val MiniPlayer = 13.dp
    }
    object Light {
        val Core = Color(0xFFF4F5F7)
        val Wide = Color(0xFFD4D8E0); val Near = Color(0xFFF4F5F7)
        val WideRadius = 7.dp; val NearRadius = 2.dp
        const val WideAlpha = .95f; const val NearAlpha = .9f
        const val TransitionMs = 220
        const val RadioEnergy = .65f; const val LibraryEnergy = .8f
        val Fallback = Color(0xFFD4D8E0)
        val FallbackStrokes = listOf(15f to .02f, 12f to .04f, 9f to .07f, 7f to .12f, 5f to .2f, 3f to .3f)
        val AmbientBase = listOf(Color(0xFF232831), Color(0xFF191D23), Color(0xFF0E1115))
        val AmbientUpper = listOf(Color(0x55566072), Color(0x15454F5D), Color.Transparent)
        val AmbientSide = listOf(Color(0x35454E59), Color(0x103B454E), Color.Transparent)
    }
    private val cardDark = listOf(Color(0xFF303640), Color(0xFF191D23))
    private val filterSelected = listOf(Color(0xFF343944), Color(0xFF303640), Color(0xFF2A303A))
    private val filterIdle = listOf(Color(0xFF232831), Color(0xFF191D23))
    val FilterSelectedBorder = Color(0xFF626C7B)
    val FilterIdleBorder = Color(0xFF343944)
    @Composable fun cardSurface() = Brush.verticalGradient(if (LocalVisualDark.current) cardDark else listOf(Surface, Surface))
    @Composable fun filterSurface(selected: Boolean) = Brush.verticalGradient(
        if (!LocalVisualDark.current) listOf(Surface, Surface) else if (selected) filterSelected else filterIdle)
    @Composable fun navigationSurface() = Brush.verticalGradient(when {
        !LocalVisualDark.current -> listOf(Surface, Background)
        LocalVisualAmoled.current -> listOf(Color.Black, Color.Black)
        else -> listOf(Color(0xFF191D23), Color(0xFF0E1115))
    })
    val scheme = darkColorScheme(primary = Color(0xFFF4F5F7), onPrimary = Color(0xFF0E1115),
        background = Color(0xFF0E1115), onBackground = Color(0xFFF4F5F7), surface = Color(0xFF232831), onSurface = Color(0xFFF4F5F7),
        surfaceVariant = Color(0xFF232831), onSurfaceVariant = Color(0xFFB4B8C4), outline = Color(0xFF343944),
        secondary = Color(0xFFD4D8E0), onSecondary = Color(0xFF0E1115))
    val lightScheme = lightColorScheme(primary = Color(0xFF242A34), onPrimary = Color.White,
        background = Color(0xFFF6F7F9), onBackground = Color(0xFF181C22), surface = Color(0xFFE8EAEE),
        onSurface = Color(0xFF181C22), surfaceVariant = Color(0xFFE8EAEE), onSurfaceVariant = Color(0xFF505866), outline = Color(0xFFB5BCC6))
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
        if (LocalVisualDark.current && !LocalVisualAmoled.current) Box(Modifier.fillMaxSize().clipToBounds().drawWithCache {
            val base = Brush.verticalGradient(VisualDesign.Light.AmbientBase)
            val shoulder = Offset(size.width * .92f, size.height * .04f)
            val edge = Offset(-size.width * .35f, size.height * .48f)
            val upperLight = Brush.radialGradient(VisualDesign.Light.AmbientUpper,
                shoulder, size.width * 1.12f)
            val sideLight = Brush.radialGradient(VisualDesign.Light.AmbientSide,
                edge, size.width * 1.15f)
            onDrawBehind {
                drawRect(base)
                withTransform({ scale(1.1f, .72f, shoulder) }) {
                    drawCircle(upperLight, size.width * 1.12f, shoulder)
                }
                withTransform({ scale(.85f, 1.4f, edge) }) {
                    drawCircle(sideLight, size.width * 1.15f, edge)
                }
            }
        })
        content()
    }
}

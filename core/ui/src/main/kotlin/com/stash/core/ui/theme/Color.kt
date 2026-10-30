package com.stash.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Sonora neutral accents; legacy token names retained for source compatibility.
val StashPurple = Color(0xFFF4F5F7)
val StashPurpleLight = Color(0xFFD4D8E0)
val StashPurpleDark = Color(0xFF343944)
val StashCyan = Color(0xFFD4D8E0)
val StashCyanLight = Color(0xFFF4F5F7)
val StashCyanDark = Color(0xFF343944)
val StashSpotifyGreen = Color(0xFF1DB954)
val StashYouTubeRed = Color(0xFFFF0033)
// Last.fm brand red — the Sync-tab Last.fm source card's brand bar (issue #255).
val StashLastfmRed = Color(0xFFD51007)
val StashError = Color(0xFFEF4444)
val StashWarning = Color(0xFFF59E0B)
val StashSuccess = Color(0xFF10B981)

// ── Dark theme palette ─────────────────────────────────────────────────────
val StashBackground = Color(0xFF0E1115)
val StashSurface = Color(0xFF191D23)
val StashElevatedSurface = Color(0xFF232831)

// ── AMOLED (pure-black dark) ───────────────────────────────────────────────
// Backgrounds and surfaces go to true #000000 so OLED pixels switch off;
// only the raised-container tier keeps a dim lift for hierarchy.
val StashAmoledSurfaceHigh = Color(0xFF232831)
val StashTextPrimary = Color(0xFFF4F5F7)
val StashTextSecondary = Color(0xFFB4B8C4)
val StashTextTertiary = Color(0xFF858D9B)
val StashGlassBackground = Color(0x0AFFFFFF)
val StashGlassBackgroundHover = Color(0x14FFFFFF)
val StashGlassBorder = Color(0x0FFFFFFF)
val StashGlassBorderBright = Color(0x24FFFFFF)

// ── Light theme palette (neutral Sonora) ──────────────────────────────
//
// Design goal: not a generic "white background + colored accents" light mode.
// Instead, a neutral mineral base that echoes the dark theme's graphite
// accent and keeps editorial contrast.
val StashBackgroundLight = Color(0xFFF6F7F9)      // subtly lavender-tinted white
val StashSurfaceLight = Color(0xFFFFFFFF)          // pure white cards stand out
val StashElevatedSurfaceLight = Color(0xFFE8EAEE)  // subtle lavender wash for raised elements
val StashTextPrimaryLight = Color(0xFF181C22)      // deep plum, high contrast
val StashTextSecondaryLight = Color(0xFF505866)    // muted purple-gray for secondary text
val StashTextTertiaryLight = Color(0xFF818895)     // lightest tier, for metadata
val StashGlassBackgroundLight = Color(0x12637084)  // purple tint on warm white
val StashGlassBackgroundHoverLight = Color(0x1A637084)
val StashGlassBorderLight = Color(0x24637084)      // subtle botanical border
val StashGlassBorderBrightLight = Color(0x40637084)

@Immutable
data class StashExtendedColors(
    val spotifyGreen: Color = StashSpotifyGreen,
    val youtubeRed: Color = StashYouTubeRed,
    val lastfmRed: Color = StashLastfmRed,
    val cyan: Color = StashCyan,
    val cyanLight: Color = StashCyanLight,
    val cyanDark: Color = StashCyanDark,
    val purpleLight: Color = StashPurpleLight,
    val purpleDark: Color = StashPurpleDark,
    val elevatedSurface: Color = StashElevatedSurface,
    val glassBackground: Color = StashGlassBackground,
    val glassBackgroundHover: Color = StashGlassBackgroundHover,
    val glassBorder: Color = StashGlassBorder,
    val glassBorderBright: Color = StashGlassBorderBright,
    val textTertiary: Color = StashTextTertiary,
    val warning: Color = StashWarning,
    val success: Color = StashSuccess,
)

/** Dark-theme instance of extended colors (current default). */
val StashExtendedColorsDark = StashExtendedColors()

/** Light-theme instance of extended colors — cream/lavender palette. */
val StashExtendedColorsLight = StashExtendedColors(
    // Accessible dark-ink versions of the accent colors on bright surfaces.
    cyan = Color(0xFF3E4857),
    cyanLight = Color(0xFF343944),
    cyanDark = Color(0xFF232831),
    purpleLight = Color(0xFF343944),
    purpleDark = Color(0xFF343944),
    elevatedSurface = StashElevatedSurfaceLight,
    glassBackground = StashGlassBackgroundLight,
    glassBackgroundHover = StashGlassBackgroundHoverLight,
    glassBorder = StashGlassBorderLight,
    glassBorderBright = StashGlassBorderBrightLight,
    textTertiary = StashTextTertiaryLight,
    // External service colors remain distinct; Sonora ink adapts per theme.
)

val LocalStashColors = staticCompositionLocalOf { StashExtendedColorsDark }

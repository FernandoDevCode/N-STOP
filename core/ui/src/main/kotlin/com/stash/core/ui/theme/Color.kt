package com.stash.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── NØSTOP accent tokens (legacy property names retained for compatibility) ──
val StashPurple = Color(0xFFC6F58D)
val StashPurpleLight = Color(0xFFE4FFC2)
val StashPurpleDark = Color(0xFF315A40)
val StashCyan = Color(0xFF70DDB8)
val StashCyanLight = Color(0xFFA2EFCC)
val StashCyanDark = Color(0xFF245B4A)
val StashSpotifyGreen = Color(0xFF1DB954)
val StashYouTubeRed = Color(0xFFFF0033)
// Last.fm brand red — the Sync-tab Last.fm source card's brand bar (issue #255).
val StashLastfmRed = Color(0xFFD51007)
val StashError = Color(0xFFEF4444)
val StashWarning = Color(0xFFF59E0B)
val StashSuccess = Color(0xFF10B981)

// ── Dark theme palette ─────────────────────────────────────────────────────
val StashBackground = Color(0xFF080D0A)
val StashSurface = Color(0xFF111A15)
val StashElevatedSurface = Color(0xFF1D2A21)

// ── AMOLED (pure-black dark) ───────────────────────────────────────────────
// Backgrounds and surfaces go to true #000000 so OLED pixels switch off;
// only the raised-container tier keeps a dim lift for hierarchy.
val StashAmoledSurfaceHigh = Color(0xFF162018)
val StashTextPrimary = Color(0xFFF1F7EE)
val StashTextSecondary = Color(0xFFA6B7AA)
val StashTextTertiary = Color(0xFF6F8473)
val StashGlassBackground = Color(0x0AFFFFFF)
val StashGlassBackgroundHover = Color(0x14FFFFFF)
val StashGlassBorder = Color(0x0FFFFFFF)
val StashGlassBorderBright = Color(0x24FFFFFF)

// ── Light theme palette (mineral-white / sage, NØSTOP identity) ─────────────
//
// Design goal: not a generic "white background + colored accents" light mode.
// Instead, a warm mineral base that echoes the dark theme's green
// accent and keeps editorial contrast.
val StashBackgroundLight = Color(0xFFF3F6EF)      // soft lavender-tinted white
val StashSurfaceLight = Color(0xFFFFFFFF)          // pure white cards stand out
val StashElevatedSurfaceLight = Color(0xFFE4EBDF)  // subtle purple wash for raised elements
val StashTextPrimaryLight = Color(0xFF122019)      // deep aubergine, high contrast
val StashTextSecondaryLight = Color(0xFF475D4F)    // muted purple-gray for secondary text
val StashTextTertiaryLight = Color(0xFF708477)     // lightest tier, for metadata
val StashGlassBackgroundLight = Color(0x122E6844)  // green tint on warm white
val StashGlassBackgroundHoverLight = Color(0x1A2E6844)
val StashGlassBorderLight = Color(0x242E6844)      // subtle botanical border
val StashGlassBorderBrightLight = Color(0x402E6844)

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
    elevatedSurface = StashElevatedSurfaceLight,
    glassBackground = StashGlassBackgroundLight,
    glassBackgroundHover = StashGlassBackgroundHoverLight,
    glassBorder = StashGlassBorderLight,
    glassBorderBright = StashGlassBorderBrightLight,
    textTertiary = StashTextTertiaryLight,
    // Brand colors (spotify/youtube/cyan/purple variants) stay identical —
    // they're identity markers, not theme-dependent.
)

val LocalStashColors = staticCompositionLocalOf { StashExtendedColorsDark }

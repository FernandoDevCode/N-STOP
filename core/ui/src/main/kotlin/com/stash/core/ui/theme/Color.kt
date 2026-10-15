package com.stash.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── NØNStop violet → cyan → mint identity (legacy names kept for compatibility) ──
val StashPurple = Color(0xFFAE76FA)
val StashPurpleLight = Color(0xFFDCC0FF)
val StashPurpleDark = Color(0xFF46316A)
val StashCyan = Color(0xFF4BDDCB)
val StashCyanLight = Color(0xFF91F3E3)
val StashCyanDark = Color(0xFF155A60)
val StashSpotifyGreen = Color(0xFF1DB954)
val StashYouTubeRed = Color(0xFFFF0033)
// Last.fm brand red — the Sync-tab Last.fm source card's brand bar (issue #255).
val StashLastfmRed = Color(0xFFD51007)
val StashError = Color(0xFFEF4444)
val StashWarning = Color(0xFFF59E0B)
val StashSuccess = Color(0xFF10B981)

// ── Dark theme palette ─────────────────────────────────────────────────────
val StashBackground = Color(0xFF080A13)
val StashSurface = Color(0xFF111521)
val StashElevatedSurface = Color(0xFF202438)

// ── AMOLED (pure-black dark) ───────────────────────────────────────────────
// Backgrounds and surfaces go to true #000000 so OLED pixels switch off;
// only the raised-container tier keeps a dim lift for hierarchy.
val StashAmoledSurfaceHigh = Color(0xFF191C2E)
val StashTextPrimary = Color(0xFFF5F4FA)
val StashTextSecondary = Color(0xFFADB1C7)
val StashTextTertiary = Color(0xFF7F869D)
val StashGlassBackground = Color(0x0AFFFFFF)
val StashGlassBackgroundHover = Color(0x14FFFFFF)
val StashGlassBorder = Color(0x0FFFFFFF)
val StashGlassBorderBright = Color(0x24FFFFFF)

// ── Light theme palette (lavender mineral white, NØNStop identity) ─────────────
//
// Design goal: not a generic "white background + colored accents" light mode.
// Instead, a warm mineral base that echoes the dark theme's violet
// accent and keeps editorial contrast.
val StashBackgroundLight = Color(0xFFF6F4FB)      // subtly lavender-tinted white
val StashSurfaceLight = Color(0xFFFFFFFF)          // pure white cards stand out
val StashElevatedSurfaceLight = Color(0xFFEAE6F3)  // subtle lavender wash for raised elements
val StashTextPrimaryLight = Color(0xFF221A35)      // deep plum, high contrast
val StashTextSecondaryLight = Color(0xFF5D5470)    // muted purple-gray for secondary text
val StashTextTertiaryLight = Color(0xFF837991)     // lightest tier, for metadata
val StashGlassBackgroundLight = Color(0x126C4C9F)  // purple tint on warm white
val StashGlassBackgroundHoverLight = Color(0x1A6C4C9F)
val StashGlassBorderLight = Color(0x246C4C9F)      // subtle botanical border
val StashGlassBorderBrightLight = Color(0x406C4C9F)

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
    cyan = Color(0xFF126C67),
    cyanLight = Color(0xFF157C75),
    cyanDark = Color(0xFF13524D),
    purpleLight = Color(0xFF46316A),
    purpleDark = Color(0xFF4D287E),
    elevatedSurface = StashElevatedSurfaceLight,
    glassBackground = StashGlassBackgroundLight,
    glassBackgroundHover = StashGlassBackgroundHoverLight,
    glassBorder = StashGlassBorderLight,
    glassBorderBright = StashGlassBorderBrightLight,
    textTertiary = StashTextTertiaryLight,
    // External service colors remain distinct; NØSTOP ink adapts per theme.
)

val LocalStashColors = staticCompositionLocalOf { StashExtendedColorsDark }

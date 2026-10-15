package com.stash.feature.home.podcasts

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Activity-level podcast controller shared between Home's podcast tab and
 * root chrome. It is the same ViewModel/MediaController on every tab.
 */
val LocalPodcastViewModel = staticCompositionLocalOf<PodcastViewModel?> { null }

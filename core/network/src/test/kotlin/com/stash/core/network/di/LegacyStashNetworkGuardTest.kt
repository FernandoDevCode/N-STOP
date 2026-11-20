package com.stash.core.network.di

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyStashNetworkGuardTest {
    @Test fun `blocks original project destinations`() {
        listOf(
            "https://stashfm.app/v1/mixes",
            "https://sync.stashfm.app/v1/pair",
            "https://stash-share.rawnaldclark.workers.dev/v1/rooms",
            "https://raw.githubusercontent.com/rawnaldclark/Stash/master/icon.png",
            "https://github.com/rawnaldclark/Stash/releases",
            "https://api.github.com/repos/rawnaldclark/Stash/releases/latest",
        ).forEach { assertTrue(it, isLegacyStashDestination(it.toHttpUrl())) }
    }

    @Test fun `allows Sonora catalog providers and release endpoint`() {
        listOf(
            "https://api.github.com/repos/FernandoDevCode/N-STOP/releases/latest",
            "https://music.youtube.com/",
            "https://api-partner.spotify.com/pathfinder/v1/query",
            "https://raw.githubusercontent.com/FernandoDevCode/N-STOP/nonstop/README.md",
            "https://notstashfm.app/",
        ).forEach { assertFalse(it, isLegacyStashDestination(it.toHttpUrl())) }
    }
}

package com.stash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSocialDisplayNameTest {
    @Test fun knownNetworksHaveReadableLabels() {
        assertEquals("Instagram", artistSocialDisplayName("instagram"))
        assertEquals("YouTube", artistSocialDisplayName("YOUTUBE"))
        assertEquals("SoundCloud", artistSocialDisplayName("soundcloud"))
        assertEquals("Bandcamp", artistSocialDisplayName("bandcamp"))
    }

    @Test fun unknownNetworksHaveNeutralFallback() {
        assertEquals("Site", artistSocialDisplayName("official"))
        assertEquals("Site", artistSocialDisplayName(""))
    }
}

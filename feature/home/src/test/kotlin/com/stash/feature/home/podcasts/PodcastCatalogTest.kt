package com.stash.feature.home.podcasts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastCatalogTest {
    @Test fun acceptsPublicHttpsFeeds() {
        assertTrue(validPodcastUrl("https://feeds.simplecast.com/example"))
        assertTrue(validPodcastUrl("https://podcasts.example.org:443/rss.xml?token=abc"))
    }

    @Test fun rejectsLocalAndNonHttpsUrls() {
        listOf(
            "http://podcasts.example.org/feed.xml",
            "file:///data/user/0/private",
            "https://localhost/test",
            "https://127.0.0.1/feed",
            "https://192.168.1.10/feed",
            "https://api.example.com:8080/feed",
            "https://user:pass@example.org/feed",
            "https://example.local/feed",
        ).forEach { assertFalse("Should reject $it", validPodcastUrl(it)) }
    }

    @Test fun hashedEpisodeKeyIsStableAndFeedSpecific() {
        val first = podcastKey("https://a.example/feed|episode-1")
        assertEquals(32, first.length)
        assertEquals(first, podcastKey("https://a.example/feed|episode-1"))
        assertTrue(first != podcastKey("https://b.example/feed|episode-1"))
    }
}

package com.stash.feature.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistHeroPhotoStoreUrlTest {
    @Test fun acceptsHttpsImageLinks() {
        assertTrue(ArtistHeroPhotoStore.isValidPhotoUrl("https://example.com/photo.jpg"))
    }
    @Test fun rejectsUnsafeOrInvalidLink() {
        for (url in listOf("file:///sdcard/p.jpg", "content://foo/p", "http://example.com/p.jpg",
            "https://localhost/x", "https://127.0.0.1/test", "https://myserver.local/image",
            "https://user:pass@example.com/img", "https://[::1]/x", "javascript:alert(1)"
        )) assertFalse(url, ArtistHeroPhotoStore.isValidPhotoUrl(url))
    }
}

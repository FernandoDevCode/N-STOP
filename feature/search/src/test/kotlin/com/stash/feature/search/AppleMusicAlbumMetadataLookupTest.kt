package com.stash.feature.search

import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.AlbumSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppleMusicAlbumMetadataLookupTest {
    @Test fun `Apple matching artist ID enriches only exact album title`() {
        val json = """{"results":[
          {"wrapperType":"artist","artistId":42},
          {"wrapperType":"collection","collectionType":"Album","artistId":42,
           "collectionName":"A Night at the Opera","releaseDate":"1975-11-21T00:00:00Z",
           "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/xyz/100x100bb.jpg"},
          {"wrapperType":"collection","collectionType":"Album","artistId":777,
           "collectionName":"Other Artist","releaseDate":"2024-01-01T00:00:00Z"},
          {"wrapperType":"collection","collectionType":"Song","artistId":42,
           "collectionName":"A Song","releaseDate":"2020-01-01T00:00:00Z"}
        ]}"""
        val found = AppleMusicAlbumMetadataLookup.parseAlbumLookup(json, 42)
        assertEquals(1, found.size)
        val album = AlbumSummary(id="YT_ID",title="A Night at the Opera",artist="Queen",
          thumbnailUrl="old",year="?",source=AlbumSource.YOUTUBE,trackCount=12)
        val updated = AppleMusicAlbumMetadataLookup.enrich(listOf(album),found).single()
        assertEquals(album.id, updated.id)
        assertEquals(album.source, updated.source)
        assertEquals(12, updated.trackCount)
        assertEquals("1975", updated.year)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/xyz/600x600bb.jpg",updated.thumbnailUrl)
    }

    @Test fun `unexpected album titles preserve existing playback metadata`() {
        val original = AlbumSummary(id="yt",title="Other",artist="Queen",
          thumbnailUrl="fallback",year="2020")
        val updated = AppleMusicAlbumMetadataLookup.enrich(listOf(original),
            mapOf("different" to AppleAlbumMetadata("Different","2019","art")))
        assertEquals(original, updated.single())
    }

    @Test fun `unsafe artwork host is rejected`() {
        assertNull(AppleMusicAlbumMetadataLookup.appleArtwork(
            "https://is1-ssl.mzstatic.com.evil.test/image/thumb/xyz/100x100bb.jpg"))
        assertNull(AppleMusicAlbumMetadataLookup.appleArtwork("http://is1-ssl.mzstatic.com/image/thumb/x/100x100bb.jpg"))
    }
}

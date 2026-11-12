package com.stash.feature.search

import com.stash.core.data.musicbrainz.MusicBrainzClient
import com.stash.data.spotify.SpotifyApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

class SpotifyArtistPhotoLookupIdentityTest {
    private val lookup = SpotifyArtistPhotoLookup(
        spotifyApi = mock<SpotifyApiClient>(),
        musicBrainz = mock<MusicBrainzClient>(),
        okHttpClient = OkHttpClient(),
    )
    private val id = "4Z8W4fKeB5YxbusRsdQVPb"
    private val artistUrl = "https://open.spotify.com/artist/$id"
    @Test fun oneUniqueMusicBrainzSpotifyLinkCanBeUsed() {
        val data = Json.parseToJsonElement(
            """{"relations":[
                {"url":{"resource":"https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb"}},
                {"url":{"resource":"https://www.example.org/artist/other"}}
            ]}"""
        ).jsonObject
        assertEquals(artistUrl, lookup.uniqueSpotifyLinkFromRelations(data))
    }
    @Test fun multipleSpotifyIdsAndMalformedLinksFailClosed() {
        val data = Json.parseToJsonElement(
            """{"relations":[
                {"url":{"resource":"https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb"}},
                {"url":{"resource":"https://open.spotify.com/artist/3DiDSECUqqY1AuBP8qtaIa"}}
            ]}"""
        ).jsonObject
        assertNull(lookup.uniqueSpotifyLinkFromRelations(data))
    }
    @Test fun artistOEmbedRequiresMatchingNameAndOfficialImageHost() {
        val body = """{"provider_name":"Spotify","title":"Mothica",
            "thumbnail_url":"https://i.scdn.co/image/official"}"""
        val photo = lookup.parseSpotifyArtistOEmbed(body, "mothica", id)
        assertEquals("https://i.scdn.co/image/official", photo?.imageUrl)
        assertEquals(artistUrl, photo?.artistUrl)
        assertNull(lookup.parseSpotifyArtistOEmbed(body, "Slipknot", id))
        assertNull(lookup.parseSpotifyArtistOEmbed(body.replace("i.scdn.co", "image.example.net"), "mothica", id))
        assertNull(lookup.parseSpotifyArtistOEmbed(body.replace("Spotify", "Imposter"), "mothica", id))
    }
}

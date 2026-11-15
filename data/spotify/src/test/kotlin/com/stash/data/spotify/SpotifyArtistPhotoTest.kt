package com.stash.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyArtistPhotoTest {
    private val id = "4Z8W4fKeB5YxbusRsdQVPb"
    @Test fun exactNameSelectsArtistProfileNotSimilarResult() {
        val body = """{"artists":{"items":[
            {"id":"4Z8W4fKeB5YxbusRsdQVPb","name":"Radiohead Tribute","images":[{"url":"https://i.scdn.co/image/wrong"}]},
            {"id":"4Z8W4fKeB5YxbusRsdQVPb","name":"  RADIOHEAD  ","images":[{"url":"https://i.scdn.co/image/original"},{"url":"https://i.scdn.co/image/tiny"}]}
        ]}}"""
        assertEquals(SpotifyArtistPhoto("https://i.scdn.co/image/original", "https://open.spotify.com/artist/"+id),
            parseSpotifyArtistPhoto(body,"Radiohead"))
    }
    @Test fun emptyOrWrongArtistDoesNotReturnUnrelatedPhoto() {
        assertNull(parseSpotifyArtistPhoto("""{"artists":{"items":[{"id":"4Z8W4fKeB5YxbusRsdQVPb","name":"Other","images":[{"url":"https://i.scdn.co/image/foo"}]}]}}""", "Radiohead"))
        assertNull(parseSpotifyArtistPhoto("""{"artists":{"items":[]}}""","Radiohead"))
    }
    @Test fun ambiguousSameNameArtistsFailClosed() {
        val body = """{"artists":{"items":[
            {"id":"4Z8W4fKeB5YxbusRsdQVPb","name":"Radiohead","images":[{"url":"https://i.scdn.co/image/a"}]},
            {"id":"3DiDSECUqqY1AuBP8qtaIa","name":"Radiohead","images":[{"url":"https://i.scdn.co/image/b"}]}
        ]}}"""
        assertNull(parseSpotifyArtistPhoto(body, "Radiohead"))
    }
    @Test fun malformedAndNonSpotifyPhotoRejected() {
        assertNull(parseSpotifyArtistPhoto("{", "Radiohead"))
        assertNull(parseSpotifyArtistPhoto("""{"artists":{"items":[{"id":"4Z8W4fKeB5YxbusRsdQVPb","name":"Radiohead","images":[{"url":"http://127.0.0.1/image.jpg"}]}]}}""","Radiohead"))
    }
}

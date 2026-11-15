package com.stash.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
    @Test fun partnerArtistAvatarMatchesExactUriAndName() {
        val root = Json.parseToJsonElement("""
            {"data":{"artistUnion":{
              "uri":"spotify:artist:4Z8W4fKeB5YxbusRsdQVPb",
              "profile":{"name":"Radiohead"},
              "visuals":{"avatarImage":{"sources":[
                {"url":"https://i.scdn.co/image/small","width":64,"height":64},
                {"url":"https://i.scdn.co/image/large","width":640,"height":640}
              ]}}
            }}}
        """.trimIndent()).jsonObject
        assertEquals(
            SpotifyArtistPhoto("https://i.scdn.co/image/large",
                "https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb"),
            parseSpotifyPartnerArtistDetail(root, id, "Radiohead")
        )
        assertNull(parseSpotifyPartnerArtistDetail(root, id, "Radiohead Tribute"))
        assertNull(parseSpotifyPartnerArtistDetail(root, "3DiDSECUqqY1AuBP8qtaIa", "Radiohead"))
    }

    @Test fun partnerArtistPhotoRejectsWrongImageOriginAndMissingUri() {
        val root = Json.parseToJsonElement("""
            {"data":{"artistUnion":{
              "uri":"spotify:artist:4Z8W4fKeB5YxbusRsdQVPb",
              "profile":{"name":"Radiohead"},
              "visuals":{"avatarImage":{"sources":[{"url":"http://localhost/photo"}]}}
            }}}
        """.trimIndent()).jsonObject
        assertNull(parseSpotifyPartnerArtistDetail(root, id, "Radiohead"))
        val noUri = Json.parseToJsonElement("""
            {"data":{"artistUnion":{
              "profile":{"name":"Radiohead"},
              "visuals":{"avatarImage":{"sources":[{"url":"https://i.scdn.co/image/good"}]}}
            }}}
        """.trimIndent()).jsonObject
        assertNull(parseSpotifyPartnerArtistDetail(noUri, id, "Radiohead"))
    }

}

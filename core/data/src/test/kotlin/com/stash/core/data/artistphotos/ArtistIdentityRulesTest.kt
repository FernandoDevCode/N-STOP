package com.stash.core.data.artistphotos

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class ArtistIdentityRulesTest {
    private fun data(raw: String) = Json.parseToJsonElement(raw).jsonObject
    private fun image(
        kind: ArtistIdentityKind = ArtistIdentityKind.PHOTO,
        width: Int = 1600,
        height: Int = 1200,
        mime: String = "image/jpeg",
        url: String = "https://upload.wikimedia.org/wikipedia/commons/thumb/x/photo.jpg",
    ) = ArtistIdentityCandidate(url, kind, width, height, mime, false)

    @Test fun correctMusicBrainzLinkIsRequired() {
        val artist = data("""{
          "name":"Slipknot","type":"Group","relations":[
            {"type":"wikidata","url":{"resource":"https://www.wikidata.org/wiki/Q244101"}}]
        }""")
        assertThat(ArtistIdentityRules.wikidataId(artist, "Slipknot")).isEqualTo("Q244101")
        assertThat(ArtistIdentityRules.wikidataId(artist, "Slipknot Tribute")).isNull()
        val wrong = data("""{"name":"Slipknot","type":"Group","relations":[
            {"type":"wikidata","url":{"resource":"https://untrusted.example/wiki/Q244101"}}]
        }""")
        assertThat(ArtistIdentityRules.wikidataId(wrong, "Slipknot")).isNull()
    }

    @Test fun onlyRepresentativePhotosAreReturnedFromWikidata() {
        val entity = data("""{"entities":{"Q1":{"id":"Q1","claims":{
          "P154":[{"rank":"preferred","mainsnak":{"snaktype":"value",
            "datavalue":{"value":"Band-logo.svg"}}}],
          "P18":[{"rank":"normal","mainsnak":{"snaktype":"value",
            "datavalue":{"value":"Band-photo.jpg"}}}]
        }}}}""")
        assertThat(ArtistIdentityRules.files(entity, "Q1"))
            .containsExactly("Band-photo.jpg" to ArtistIdentityKind.PHOTO)
    }

    @Test fun symbolsNeverWinEvenWithMorePixels() {
        assertThat(ArtistIdentityRules.select(listOf(
            image(ArtistIdentityKind.SYMBOL, 2200, 2200, "image/png"),
            image(ArtistIdentityKind.PHOTO, 1800, 1000),
        ))?.kind).isEqualTo(ArtistIdentityKind.PHOTO)
        assertThat(ArtistIdentityRules.select(listOf(
            image(ArtistIdentityKind.SYMBOL, 2200, 2200, "image/png"),
        ))).isNull()
    }

    @Test fun rejectTallOrTinyHeroPhotosWithoutAlteringLegacyFallback() {
        assertThat(ArtistIdentityRules.select(listOf(
            image(width = 1100, height = 1900),
            image(width = 500, height = 350),
        ))).isNull()
        val old = ArtistPhotoRules.select(
            listOf(ArtistPhotoCandidate(
                url = "https://assets.fanart.tv/fanart/music/photo.jpg",
                category = "artistbackground", width = 1920, height = 1080,
                likes = 11, capturedAt = null,
            )), false, null,
        )
        assertThat(old?.likes).isEqualTo(11)
    }

    @Test fun photographMustBeDecodableAndFromAllowedHost() {
        assertThat(ArtistIdentityRules.select(listOf(
            image(url = "https://wrong.example/photo.jpg"),
            image(mime = "image/svg+xml"),
        ))).isNull()
        assertThat(ArtistIdentityRules.select(listOf(
            image(width = 1920, height = 1080, mime = "image/jpeg"),
        ))?.kind).isEqualTo(ArtistIdentityKind.PHOTO)
    }

    @Test fun preferredPhotoWinsAndDeprecatedIsIgnored() {
        val entity = data("""{"entities":{"Q8":{"id":"Q8","claims":{
          "P18":[
            {"rank":"deprecated","mainsnak":{"snaktype":"value","datavalue":{"value":"Old.jpg"}}},
            {"rank":"normal","mainsnak":{"snaktype":"value","datavalue":{"value":"A.jpg"}}},
            {"rank":"preferred","mainsnak":{"snaktype":"value","datavalue":{"value":"Preferred.jpg"}}}
          ]
        }}}}""")
        assertThat(ArtistIdentityRules.files(entity, "Q8"))
            .containsExactly("Preferred.jpg" to ArtistIdentityKind.PHOTO)
    }

    @Test fun missingOrMalformedPhotosDoNotWin() {
        assertThat(ArtistIdentityRules.files(data("""{"entities":{"Q1":{"id":"Q1","claims":{}}}}"""), "Q1"))
            .isEmpty()
        val malformed = data("""{"entities":{"Q1":{"id":"Q1","claims":{"P18":[
          {"rank":"normal","mainsnak":{"snaktype":"value",
            "datavalue":{"value":"../../wrong.jpg"}}}
        ]}}}}""")
        assertThat(ArtistIdentityRules.files(malformed, "Q1")).isEmpty()
    }
}

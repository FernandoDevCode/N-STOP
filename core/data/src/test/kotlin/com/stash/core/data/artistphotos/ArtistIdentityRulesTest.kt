package com.stash.core.data.artistphotos

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class ArtistIdentityRulesTest {
    private fun data(raw: String) = Json.parseToJsonElement(raw).jsonObject
    private fun image(
        kind: ArtistIdentityKind,
        width: Int = 1600,
        height: Int = 1200,
        mime: String = "image/jpeg",
        url: String = "https://upload.wikimedia.org/wikipedia/commons/thumb/x/image.jpg",
        raster: Boolean = false,
    ) = ArtistIdentityCandidate(url, kind, width, height, mime, raster)

    @Test fun musicBrainzMustHaveExactNameAndSingleWikidataRelation() {
        val artist = data("""{
          "name":"Queen","type":"Group",
          "relations":[{"type":"wikidata","url":{"resource":"https://www.wikidata.org/wiki/Q15862"}}]
        }""")
        assertThat(ArtistIdentityRules.wikidataId(artist, "queen")).isEqualTo("Q15862")
        assertThat(ArtistIdentityRules.wikidataId(artist, "Queen Tribute")).isNull()
        assertThat(ArtistIdentityRules.wikidataId(data("""{
          "name":"Queen","type":"Group","relations":[
            {"type":"wikidata","url":{"resource":"https://www.wikidata.org/wiki/Q15862"}},
            {"type":"wikidata","url":{"resource":"https://www.wikidata.org/wiki/Q30000"}}
          ]}"""), "Queen")).isNull()
    }

    @Test fun rejectsFalseIdentityLinks() {
        val bad = data("""{
          "name":"Queen","type":"Group",
          "relations":[{"type":"wikidata",
            "url":{"resource":"https://example.com/wiki/Q15862"}}]
        }""")
        assertThat(ArtistIdentityRules.wikidataId(bad, "Queen")).isNull()
        assertThat(ArtistIdentityRules.wikidataId(data("""{
          "name":"Queen","type":"Group",
          "relations":[{"type":"wikidata",
            "url":{"resource":"https://www.wikidata.org/wiki/Q15862?redirect=evil"}}]
        }"""), "Queen")).isNull()
    }

    @Test fun directSymbolBeforeRepresentativePhoto() {
        val entity = data("""{"entities":{"Q100":{"id":"Q100","claims":{
          "P154":[{"rank":"normal","mainsnak":{"snaktype":"value",
             "datavalue":{"value":"Band emblem.svg"}}}],
          "P18":[{"rank":"normal","mainsnak":{"snaktype":"value",
             "datavalue":{"value":"Historic group photo.jpg"}}}]
        }}}}""")
        assertThat(ArtistIdentityRules.files(entity, "Q100")).containsExactly(
            "Band emblem.svg" to ArtistIdentityKind.SYMBOL,
            "Historic group photo.jpg" to ArtistIdentityKind.PHOTO,
        ).inOrder()
        assertThat(ArtistIdentityRules.select(listOf(
            image(ArtistIdentityKind.SYMBOL, width = 900, height = 900),
            image(ArtistIdentityKind.PHOTO),
        ))?.kind).isEqualTo(ArtistIdentityKind.SYMBOL)
    }

    @Test fun recognizesOfficialSymbolEntityWithoutManualArtistOverrides() {
        val band = data("""{"entities":{"Q11036":{"id":"Q11036","claims":{
          "P2238":[{"rank":"normal","mainsnak":{"snaktype":"value",
            "datavalue":{"value":{"entity-type":"item","id":"Q999"}}}}]
        }}}}""")
        assertThat(ArtistIdentityRules.officialSymbolId(band, "Q11036")).isEqualTo("Q999")
        assertThat(ArtistIdentityRules.officialSymbolId(band, "Q2")).isNull()
    }

    @Test fun wideWordmarkDoesNotReplaceHeroButHistoricPhotoCan() {
        val selected = ArtistIdentityRules.select(listOf(
            image(ArtistIdentityKind.SYMBOL, width = 1100, height = 180),
            image(ArtistIdentityKind.PHOTO, width = 1798, height = 1146),
        ))
        assertThat(selected?.kind).isEqualTo(ArtistIdentityKind.PHOTO)
    }

    @Test fun noSuitableImageMeansNullSoOriginalSelectorRuns() {
        val selected = ArtistIdentityRules.select(listOf(
            image(ArtistIdentityKind.PHOTO, width = 367, height = 346),
            image(ArtistIdentityKind.SYMBOL, width = 288, height = 106),
        ))
        assertThat(selected).isNull()
        // Original photo selector remains a separate, unchanged second stage.
        val original = ArtistPhotoRules.select(listOf(
            ArtistPhotoCandidate("https://assets.fanart.tv/fanart/music/a.jpg",
                "artistbackground", 1900, 1080, 9, null),
        ), false, null)
        assertThat(original?.likes).isEqualTo(9)
    }

    @Test fun invalidOrUnrenderableImagesNeverWin() {
        assertThat(ArtistIdentityRules.select(listOf(
            image(ArtistIdentityKind.SYMBOL, 900, 900, "image/svg+xml", raster = false),
            image(ArtistIdentityKind.PHOTO, url = "https://other.example/image.jpg"),
            image(ArtistIdentityKind.PHOTO, mime = "application/pdf"),
        ))).isNull()
        assertThat(ArtistIdentityRules.select(listOf(
            image(ArtistIdentityKind.SYMBOL, 900, 900, "image/svg+xml", raster = true),
        ))?.kind).isEqualTo(ArtistIdentityKind.SYMBOL)
    }

    @Test fun deprecatedFilesAreIgnoredAndPreferredRankWins() {
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

    @Test fun missingClaimsAndMalformedFileNamesRemainUnavailable() {
        assertThat(ArtistIdentityRules.files(data("""{"entities":{"Q1":{"id":"Q1","claims":{}}}}"""), "Q1"))
            .isEmpty()
        val entity = data("""{"entities":{"Q1":{"id":"Q1","claims":{"P18":[
          {"rank":"normal","mainsnak":{"snaktype":"value",
            "datavalue":{"value":"../../wrong.jpg"}}}
        ]}}}}""")
        assertThat(ArtistIdentityRules.files(entity, "Q1")).isEmpty()
    }
}

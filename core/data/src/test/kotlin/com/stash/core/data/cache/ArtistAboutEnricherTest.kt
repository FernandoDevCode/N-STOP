package com.stash.core.data.cache

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.musicbrainz.MusicBrainzClient
import com.stash.data.ytmusic.model.SocialLink
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class ArtistAboutEnricherTest {
    private fun relsPayload(vararg typeToUrl: Pair<String, String>): JsonObject {
        val rels = typeToUrl.joinToString(",") { (type, url) ->
            """{"type":"$type","url":{"resource":"$url"}}"""
        }
        return Json.parseToJsonElement("""{"relations":[$rels]}""") as JsonObject
    }

    private fun enricher(searchResult: JsonObject?): RealArtistAboutEnricher {
        val mb = object : MusicBrainzClient {
            override suspend fun lookupUrlRels(mbid: String): JsonObject? = null
            override suspend fun lookupMemberRelations(mbid: String): JsonObject? = null
            override suspend fun searchByName(name: String) = searchResult
            override suspend fun searchUniqueByName(name: String) = searchResult
        }
        return RealArtistAboutEnricher(mb)
    }

    @Test fun `musicbrainz socials are preserved`() = runTest {
        val about = enricher(
            relsPayload("social network" to "https://instagram.com/a"),
        ).enrich("A")
        assertThat(about!!.bio).isNull()
        assertThat(about.socials).containsExactly(SocialLink("instagram", "https://instagram.com/a"))
    }

    @Test fun `no relations returns null`() = runTest {
        assertThat(enricher(null).enrich("A")).isNull()
    }

    @Test fun `other URL relations are mapped`() = runTest {
        val about = enricher(
            relsPayload("youtube" to "https://youtube.com/@a"),
        ).enrich("A")
        assertThat(about!!.socials).containsExactly(SocialLink("youtube", "https://youtube.com/@a"))
    }
}

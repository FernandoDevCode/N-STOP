package com.stash.core.data.musicbrainz

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicBrainzDiscographyTest {
    private fun body(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun `groups preserve MusicBrainz IDs and exclude unusable records`() {
        val groups = parseMusicBrainzGroups(body("""{"release-groups":[
          {"id":"11111111-1111-1111-1111-111111111111","title":"Slipknot","first-release-date":"1999-06-29"},
          {"id":"broken","title":"Wrong"},{"title":"Unknown"}
        ]}"""))
        assertEquals(1, groups.size)
        assertEquals("Slipknot", groups.single().title)
        assertEquals("1999", groups.single().year)
    }

    @Test fun `anniversary edition remains separate from a normal edition`() {
        val found = parseMusicBrainzEditions(body("""{"releases":[
          {"id":"22222222-2222-2222-2222-222222222222","title":"Slipknot","date":"1999-06-29"},
          {"id":"33333333-3333-3333-3333-333333333333","title":"Slipknot","disambiguation":"10th anniversary edition","date":"2009-09-09"}
        ]}"""))
        assertEquals(2, found.size)
        assertEquals("10th anniversary edition", found.first().description)
        assertEquals("33333333-3333-3333-3333-333333333333", found.first().id)
    }

    @Test fun `track order is exact within each disc and retains recording IDs`() {
        val tracks = parseMusicBrainzTracks(body("""{"media":[{"position":1,"tracks":[
          {"position":1,"title":"(sic)","length":196000,
           "recording":{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}},
          {"position":2,"title":"Eyeless","length":234000}
        ]},{"position":2,"tracks":[{"position":1,"title":"Bonus demo"}]}]}"""))
        assertEquals(listOf(1,2,1), tracks.map { it.position })
        assertEquals(listOf(1,1,2), tracks.map { it.disc })
        assertEquals(196000L, tracks.first().durationMs)
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", tracks.first().recordingId)
        assertNull(tracks.last().durationMs)
    }
}

package com.stash.core.data.artistphotos

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.LocalDate

class ArtistPhotoRulesTest {
    private fun candidate(
        likes: Int,
        captured: String? = null,
        category: String = "artistbackground",
        width: Int = 1920,
        height: Int = 1080,
        name: String = "photo",
    ) = ArtistPhotoCandidate(
        "https://assets.fanart.tv/fanart/music/$name.jpg",
        category, width, height, likes, captured?.let(LocalDate::parse),
    )

    @Test fun soloChoosesHighestRatingWithNoDateRequirement() {
        assertThat(ArtistPhotoRules.select(
            listOf(candidate(3), candidate(12, name = "best"), candidate(9)),
            isGroup = false, currentLineupFloor = null,
        )?.likes).isEqualTo(12)
    }

    @Test fun groupRejectsUndatedAndPreLineupPhotosEvenIfMostLiked() {
        val floor = LocalDate.parse("2024-09-01")
        val actual = ArtistPhotoRules.select(
            listOf(
                candidate(50), candidate(30, captured = "2010-01-01"),
                candidate(3, captured = "2024-09-01", name = "valid"),
            ), isGroup = true, currentLineupFloor = floor,
        )
        assertThat(actual?.likes).isEqualTo(3)
    }

    @Test fun unknownFloorDoesNotAssumeBandMembership() {
        assertThat(ArtistPhotoRules.select(
            listOf(candidate(99, captured = "2026-01-01")),
            isGroup = true, currentLineupFloor = null,
        )).isNull()
    }

    @Test fun resolutionAndCategoryAreHardGates() {
        val winner = ArtistPhotoRules.select(
            listOf(candidate(50, category = "musiclogo"), candidate(60, width = 400),
                candidate(7, name = "valid")),
            isGroup = false, currentLineupFloor = null,
        )
        assertThat(winner?.likes).isEqualTo(7)
    }

    @Test fun tiesNeedNoExtraCriteria() {
        val first = candidate(8, name = "first")
        val second = candidate(8, name = "second")
        assertThat(ArtistPhotoRules.select(
            listOf(first, second), false, null,
        )).isEqualTo(first)
    }

    @Test fun lineupFloorTracksMostRecentExitOrEntry() {
        val data = Json.parseToJsonElement("""
          {"relations":[
            {"type":"member of band","artist":{"name":"A"},"ended":false,
              "begin":{"year":2010,"month":1,"day":1}},
            {"type":"member of band","artist":{"name":"B"},"ended":false,
              "begin":{"year":2018,"month":4,"day":7}},
            {"type":"member of band","artist":{"name":"C"},"ended":true,
              "end":{"year":2021,"month":9,"day":2}}
          ]}
        """).jsonObject
        assertThat(ArtistPhotoRules.currentLineupFloor(data))
            .isEqualTo(LocalDate.parse("2021-09-02"))
    }

    @Test fun partialMembershipDateUsesConservativeEndOfYear() {
        val data = Json.parseToJsonElement("""
          {"relations":[{"type":"member of band","artist":{"name":"A"},
            "ended":false,"begin":{"year":2020}}]}
        """).jsonObject
        assertThat(ArtistPhotoRules.currentLineupFloor(data))
            .isEqualTo(LocalDate.parse("2020-12-31"))
    }

    @Test fun missingMembershipYearRemainsUnknown() {
        val data = Json.parseToJsonElement("""
          {"relations":[{"type":"member of band","artist":{"name":"A"},
            "ended":false,"begin":{}}]}
        """).jsonObject
        assertThat(ArtistPhotoRules.currentLineupFloor(data)).isNull()
    }
}

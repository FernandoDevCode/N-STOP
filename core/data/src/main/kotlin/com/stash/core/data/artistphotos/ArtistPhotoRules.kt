package com.stash.core.data.artistphotos

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** An upload timestamp is NOT evidence of when a photograph was taken. */
data class ArtistPhotoCandidate(
    val url: String,
    val category: String,
    val width: Int,
    val height: Int,
    val likes: Int,
    val capturedAt: LocalDate?,
)

/**
 * Three ordered gates, without aesthetic scoring or recency tie-breaks:
 * current line-up floor -> suitable type/resolution -> highest community likes.
 * In a tie the first eligible candidate wins.
 */
object ArtistPhotoRules {
    fun select(
        candidates: List<ArtistPhotoCandidate>,
        isGroup: Boolean,
        currentLineupFloor: LocalDate?,
    ): ArtistPhotoCandidate? {
        if (isGroup && currentLineupFloor == null) return null
        return candidates.asSequence()
            .filter { candidate ->
                if (!isGroup) true
                else candidate.capturedAt != null &&
                    !candidate.capturedAt.isBefore(currentLineupFloor!!)
            }
            .filter { it.category in setOf("artistthumb", "artistbackground") }
            .filter { it.width >= 800 && it.height >= 450 }
            .filter { it.url.startsWith("https://assets.fanart.tv/") }
            .maxByOrNull { it.likes }
    }

    /**
     * MusicBrainz relationship dates describe the band membership timeline.
     * A missing join/leave date makes the floor unverified, rather than
     * silently treating the first upload or today's date as a photo date.
     */
    fun currentLineupFloor(artistWithRels: JsonObject): LocalDate? {
        val relations = artistWithRels["relations"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.filter { it["type"]?.jsonPrimitive?.content == "member of band" &&
                it["artist"] != null }
            ?: return null
        if (relations.isEmpty()) return null

        val changes = mutableListOf<LocalDate>()
        var hasCurrentMember = false
        for (rel in relations) {
            val ended = rel["ended"]?.jsonPrimitive?.content == "true"
            val dateField = if (ended) "end" else "begin"
            val dateObject = rel[dateField]?.jsonObject ?: return null
            val year = dateObject["year"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
            val month = dateObject["month"]?.jsonPrimitive?.content?.toIntOrNull()
            val day = dateObject["day"]?.jsonPrimitive?.content?.toIntOrNull()
            // MusicBrainz often knows only a year/month. Choose the latest
            // POSSIBLE day (conservative upper bound): no later photograph
            // is mistakenly accepted as predating a membership change.
            val date = runCatching {
                when {
                    month == null -> LocalDate.of(year, 12, 31)
                    day == null -> java.time.YearMonth.of(year, month).atEndOfMonth()
                    else -> LocalDate.of(year, month, day)
                }
            }.getOrNull() ?: return null
            changes += date
            if (!ended) hasCurrentMember = true
        }
        return if (hasCurrentMember) changes.maxOrNull() else null
    }
}

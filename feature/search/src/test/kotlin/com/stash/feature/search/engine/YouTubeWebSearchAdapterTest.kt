package com.stash.feature.search.engine

import com.stash.data.ytmusic.YouTubeWebVideo
import com.stash.feature.search.contract.*
import org.junit.Assert.*
import org.junit.Test

class YouTubeWebSearchAdapterTest {
    @Test fun preservesSelectedVersionsAndOrdinalPositionWithoutActions() {
        listOf("JOvAJJI2Ui8" to "Demo", "JQ2pTamaqQ4" to "Live", "MoL0u4fxUW4" to "Cover").forEachIndexed { index, (id, title) ->
            val result = YouTubeWebVideo(id, title, "Channel", "https://img/art", 1234L, index + 5).toUniversalSearchResult()
            assertEquals(CatalogIdentity(CatalogSource.YOUTUBE, id), result.identity)
            assertEquals(SearchResultType.VIDEO, result.type)
            assertEquals(title, result.title)
            assertEquals(index + 5, result.providerRank)
            assertEquals(1.0 / (index + 6.0), result.rankingScore, 0.0)
            assertEquals("Channel", result.subtitle)
            assertEquals(1234L, result.durationMs)
            assertTrue(result.supportedActions.isEmpty())
            assertNull(result.audioIdentity)
        }
    }
}

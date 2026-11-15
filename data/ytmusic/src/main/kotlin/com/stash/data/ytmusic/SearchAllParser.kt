package com.stash.data.ytmusic

import com.stash.data.ytmusic.model.*
import kotlinx.serialization.json.JsonObject

internal fun parseSearchAll(response: JsonObject): SearchAllResults {
    val shelves = response.navigatePath(
        "contents", "tabbedSearchResultsRenderer", "tabs",
    )?.firstArray()?.firstOrNull()?.asObject()
        ?.navigatePath("tabRenderer", "content", "sectionListRenderer", "contents")
        ?.asArray()
        ?: return SearchAllResults(emptyList())

    val sections = mutableListOf<SearchResultSection>()

    // 1. Top result — musicCardShelfRenderer appears at most once, usually first.
    val topCard = shelves.asSequence()
        .mapNotNull { it.asObject() }
        .firstOrNull { it.containsKey("musicCardShelfRenderer") }
        ?.get("musicCardShelfRenderer")?.asObject()
    topCard?.let { parseTopResultCard(it) }
        ?.let { sections.add(SearchResultSection.Top(it)) }

    // 2..4. Named musicShelfRenderer shelves, dispatched by their title text.
    for (shelf in shelves) {
        val renderer = shelf.asObject()?.get("musicShelfRenderer")?.asObject() ?: continue
        val title = renderer.navigatePath("title", "runs")?.firstArray()
            ?.firstOrNull()?.asObject()?.get("text")?.asString() ?: continue
        // Parsers live in SearchResponseParser.kt as top-level internal funcs.
        when (title) {
            "Songs" -> parseSongsShelf(renderer).takeIf { it.isNotEmpty() }
                ?.let { sections.add(SearchResultSection.Songs(it.take(4))) }
            "Artists" -> parseArtistsShelf(renderer).takeIf { it.isNotEmpty() }
                ?.let { sections.add(SearchResultSection.Artists(it)) }
            "Albums" -> parseAlbumsShelf(renderer).takeIf { it.isNotEmpty() }
                ?.let { sections.add(SearchResultSection.Albums(it)) }
        }
    }

    // The titled musicShelfRenderer shape above is only served to
    // authenticated YT-Music clients in supported regions. Unauthenticated
    // / region-limited requests get a FLAT itemSectionRenderer list with no
    // titled shelves, which the loop above skips entirely — the user then
    // sees "No results found" over a response full of hits (issue #268).
    // Recover those rows when no list shelf matched.
    val hasListSections = sections.any {
        it is SearchResultSection.Songs ||
            it is SearchResultSection.Artists ||
            it is SearchResultSection.Albums
    }
    if (!hasListSections) {
        sections.addAll(parseFlatSearchSections(shelves))
    }

    return SearchAllResults(sections, topAlbum = topCard?.let { parseAlbumTopCard(it) })
}



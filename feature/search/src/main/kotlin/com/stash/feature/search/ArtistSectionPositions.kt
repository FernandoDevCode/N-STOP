package com.stash.feature.search

/**
 * Item positions in the artist LazyColumn. The hero and the sticky section
 * navigator are always the first two items; each populated shelf contributes
 * a heading followed by a shelf/list. Keeping the mapping pure prevents
 * album deep-links from landing in the wrong section when data is missing.
 */
internal data class ArtistSectionPositions(
    val songs: Int?,
    val albums: Int?,
    val singles: Int?,
    val about: Int?,
) {
    fun available(): List<Int> = listOfNotNull(songs, albums, singles, about)
}

internal fun artistSectionPositions(
    hasSongs: Boolean,
    hasAlbums: Boolean,
    hasSingles: Boolean,
    hasRelated: Boolean,
    hasAbout: Boolean,
): ArtistSectionPositions {
    var index = 2 // hero (0), sticky section navigator (1)
    fun reserve(hasContent: Boolean): Int? {
        if (!hasContent) return null
        val result = index
        index += 2 // heading + content
        return result
    }
    val songs = reserve(hasSongs)
    val albums = reserve(hasAlbums)
    val singles = reserve(hasSingles)
    reserve(hasRelated) // Fans also like sits between Singles and About
    val about = reserve(hasAbout)
    return ArtistSectionPositions(songs, albums, singles, about)
}

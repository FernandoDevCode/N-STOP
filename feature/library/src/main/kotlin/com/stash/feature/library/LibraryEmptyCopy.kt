package com.stash.feature.library

/**
 * The on-screen name of an active Songs source filter ("FLAC ×" above the list, "No FLAC
 * songs…" when it empties it); null for [SourceFilter.ALL], which filters nothing.
 */
internal fun sourceFilterLabel(filter: SourceFilter): String? = when (filter) {
    SourceFilter.ALL -> null
    SourceFilter.YOUTUBE -> "YouTube"
    SourceFilter.SPOTIFY -> "Spotify"
    SourceFilter.FLAC -> "FLAC"
    SourceFilter.NON_FLAC -> "Non-FLAC"
}

/**
 * The empty-state line for a Library tab emptied by the user's own search [query] or source
 * [filter], rather than by an empty library; null when neither is active, so the tab keeps its
 * "Sync your library…" / "Connect a service…" line.
 *
 * Without it, a FLAC filter persisted from days ago (or a search that matched nothing) blamed
 * sync or a missing connection for an empty list the user could fix with one tap.
 *
 * @param noun What the tab lists, plural and lowercase ("songs", "artists").
 */
internal fun noMatchMessage(noun: String, query: String, filter: SourceFilter = SourceFilter.ALL): String? {
    val q = query.trim()
    val label = sourceFilterLabel(filter)
    return when {
        q.isNotEmpty() && label != null -> "No $label $noun match “$q”"
        label != null -> "No $label $noun in your library"
        q.isNotEmpty() -> "No $noun match “$q”"
        else -> null
    }
}

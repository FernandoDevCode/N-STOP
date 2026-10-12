package com.stash.core.common

/**
 * The library's one "same song" rule: what `tracks.canonical_title` and `tracks.canonical_artist`
 * hold for every row, whoever writes it (sync, discovery, the YouTube canonicalizer, search
 * downloads, stream stubs, likes, hand-offs), and what every lookup by words computes. One rule so
 * a song filed by two paths is found as one row instead of being filed twice.
 *
 * Loose on purpose, so the same song from two sources meets:
 *  - [title]: every bracketed group ("(feat. X)", "[Live]", "(Remastered 2011)") and a trailing
 *    "feat. / ft. / featuring ..." are dropped, then the rest is lowercased and trimmed.
 *    Punctuation stays: "Don't Stop (feat. X)" is "don't stop".
 *  - [artist]: split on `,` `;` `&` `/` and the word "and", each part lowercased and trimmed, the
 *    parts sorted and joined with ", ": "B & A" is "a, b".
 *
 * Because the title rule drops brackets, two versions share one key: "Song (Live)" and "Song" are
 * both "song". Never take a row found by its words alone as the same recording.
 *
 * This is `TrackMatcher`'s rule, moved here unchanged so code outside the sync can call it without
 * injecting the matcher. Do not change its output: the blocklist keys (`artist|title`) and every
 * stored row are keyed by it, so a change re-keys the whole library. Pinned by TrackCanonicalTest.
 */
object TrackCanonical {

    /** A bracketed group: (remix), (feat. X), [live]. */
    private val BRACKET_GROUP = Regex("""\s*[\(\[][^)\]]*[\)\]]""")

    /**
     * "feat", "ft", "featuring" and everything after. Note there is no word boundary in front, so
     * "Left Hand" loses "ft Hand" ("le"). Kept as is: the stored keys and the blocklist depend on it.
     */
    private val FEAT_TAIL = Regex("""\s*(feat\.?|ft\.?|featuring)\s+.*""", RegexOption.IGNORE_CASE)

    /** Common artist separators. */
    private val ARTIST_SEPARATOR = Regex("""\s*[,;&/]\s*|\s+(?:and|&)\s+""", RegexOption.IGNORE_CASE)

    /**
     * The canonical (loose) form of a track title.
     *
     * @param title Raw track title.
     * @return Lowercased title without bracketed groups or a trailing credit.
     */
    fun title(title: String): String =
        title
            .replace(BRACKET_GROUP, "")
            .replace(FEAT_TAIL, "")
            .lowercase()
            .trim()

    /**
     * The canonical (loose) form of an artist credit.
     *
     * @param artist Raw artist string (may name several artists).
     * @return Lowercased artist names, sorted, joined with ", ".
     */
    fun artist(artist: String): String =
        artist
            .split(ARTIST_SEPARATOR)
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .sorted()
            .joinToString(", ")
}

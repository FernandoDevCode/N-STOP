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
 * both "song". A row found by its words alone may only stand for an incoming song when
 * [sameVersion] agrees too (the whole reuse rule is `SameSongRule` in core:data).
 *
 * This is `TrackMatcher`'s rule, moved here unchanged so code outside the sync can call it without
 * injecting the matcher. Do not change its output: the blocklist keys (`artist|title`) and every
 * stored row are keyed by it, so a change re-keys the whole library. Pinned by TrackCanonicalTest.
 */
object TrackCanonical {

    /** A bracketed group: (remix), (feat. X), [live]. [versionKey] sees exactly the groups [title] drops. */
    private val BRACKET_GROUP = Regex("""\s*[\(\[][^)\]]*[\)\]]""")

    /**
     * "feat", "ft", "featuring" and everything after. Note there is no word boundary in front, so
     * "Left Hand" loses "ft Hand" ("le"). Kept as is: the stored keys and the blocklist depend on it.
     * [versionKey] doesn't repeat the quirk, so the reuse rule still tells such titles apart.
     */
    private val FEAT_TAIL = Regex("""\s*(feat\.?|ft\.?|featuring)\s+.*""", RegexOption.IGNORE_CASE)

    /** Common artist separators. */
    private val ARTIST_SEPARATOR = Regex("""\s*[,;&/]\s*|\s+(?:and|&)\s+""", RegexOption.IGNORE_CASE)

    /**
     * A credit outside brackets: a whole word after a space (so not "Left Hand", not a title that
     * starts with "Ft."), up to a " - " that starts a version suffix, or to the end:
     * "Song feat. Jay-Z - Live" loses "feat. Jay-Z" and keeps "- Live".
     */
    private val CREDIT_TAIL = Regex(
        """(?<=\s)(?:feat\.?|ft\.?|featuring)\s+.*?(?=\s+-\s+|$)""",
        RegexOption.IGNORE_CASE,
    )

    /** A bracketed group (in strict form) that only credits other artists. The words Stash on the web drops too. */
    private val CREDIT_GROUP = Regex("""^(?:feat|ft|featuring)(?:\s|$)""")

    /**
     * Bracketed tags YouTube adds to how a song is presented, never to which recording it is.
     * Compared with the whole group in strict form, so "(Official Video HD)" is not one of them: it
     * counts as a version, which can only keep two songs apart, never merge them.
     */
    private val PRESENTATION_TAGS = setOf(
        "official video", "official music video", "official audio", "official lyric video",
        "official lyrics video", "official visualizer", "official visualiser", "official hd video",
        "official", "video", "music video", "audio", "hd video", "lyric video", "lyrics video",
        "lyrics", "lyric", "visualizer", "visualiser", "hd", "hq", "4k", "explicit",
    )

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

    /**
     * Which version a title names, as far as its words tell: the title without its credits
     * ("(feat. X)", "ft. X") and without YouTube presentation tags ("(Official Video)", "[Lyrics]"),
     * in [strictCanonical] form. Everything else [title] drops stays: "(Live)", "(Remix)",
     * "[2011 Remaster]", "(Taylor's Version)", and the words a mid-word "ft " cuts off.
     *
     * @param title Raw track title.
     * @return A key that is equal for two titles of the same version.
     */
    fun versionKey(title: String): String {
        val withoutTags = BRACKET_GROUP.replace(title) { group ->
            val words = strictCanonical(group.value)
            if (CREDIT_GROUP.containsMatchIn(words) || words in PRESENTATION_TAGS) " " else group.value
        }
        return strictCanonical(CREDIT_TAIL.replace(withoutTags, " "))
    }

    /**
     * Whether two titles name the same version of a song: "Song (feat. X)", "Song ft. X",
     * "Song (Official Video)" and "Song" do; "Song (Live)" and "Song" don't.
     */
    fun sameVersion(a: String, b: String): Boolean = versionKey(a) == versionKey(b)
}

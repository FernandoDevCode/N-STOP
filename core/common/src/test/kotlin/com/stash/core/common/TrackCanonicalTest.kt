package com.stash.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [TrackCanonical], the one rule behind `tracks.canonical_title` / `canonical_artist`. The
 * sync wrote it for years and the blocklist is keyed by it, so its output on these inputs must
 * never move: a change re-keys every stored row and every block.
 */
class TrackCanonicalTest {

    /** TrackMatcher's body before the rule moved here, verbatim: the move must not change a single key. */
    private val parenthetical = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    private val feat = Regex("""\s*(feat\.?|ft\.?|featuring)\s+.*""", RegexOption.IGNORE_CASE)
    private val separator = Regex("""\s*[,;&/]\s*|\s+(?:and|&)\s+""", RegexOption.IGNORE_CASE)
    private fun formerTitle(t: String) = t.replace(parenthetical, "").replace(feat, "").lowercase().trim()
    private fun formerArtist(a: String) =
        a.split(separator).map { it.lowercase().trim() }.filter { it.isNotEmpty() }.sorted().joinToString(", ")

    private val titles = linkedMapOf(
        // The owner's example: brackets and credits go, punctuation stays.
        "Don't Stop (feat. X)" to "don't stop",
        // Every bracketed group goes, version tags included: two versions share a key.
        "Song (Remastered 2011)" to "song",
        "Song [Live at Wembley]" to "song",
        "Song (Live) [2011 Remaster]" to "song",
        // A " - " suffix is not a bracket, so it stays.
        "Song - Radio Edit" to "song - radio edit",
        // A credit outside brackets goes, with everything after it.
        "Song ft. Artist B" to "song",
        "Song feat. Artist B" to "song",
        "Song Featuring B" to "song",
        // No word boundary before "ft": a mid-word "ft " cuts the title. Pinned, not endorsed.
        "Left Hand Free" to "le",
        // Only lowercased and trimmed: inner whitespace and punctuation are kept.
        "  Don't  Stop   Me Now!!  " to "don't  stop   me now!!",
        "Track_Name-v2.0" to "track_name-v2.0",
        // An unclosed bracket is not a group.
        "Song (unclosed" to "song (unclosed",
        // A title that is only a bracket is blank: lookups skip blank keys.
        "(Intro)" to "",
        // Unicode letters lowercase locale-free.
        "Beyoncé" to "beyoncé",
        "坂本龍一" to "坂本龍一",
        "İstanbul" to "i̇stanbul",
        "" to "",
    )

    private val artists = linkedMapOf(
        // The owner's example: a two-artist credit, however it's joined, is one sorted list.
        "A & B" to "a, b",
        "B & A" to "a, b",
        "B, A" to "a, b",
        "A;B" to "a, b",
        "AC/DC" to "ac, dc",
        "Simon & Garfunkel" to "garfunkel, simon",
        "Earth, Wind & Fire" to "earth, fire, wind",
        "Florence and the Machine" to "florence, the machine",
        // Not separators.
        "Artist A x Artist B" to "artist a x artist b",
        "Dan + Shay" to "dan + shay",
        "Beyoncé feat. Jay-Z" to "beyoncé feat. jay-z",
        "  " to "",
        "" to "",
    )

    @Test fun `titles on tricky inputs`() {
        for ((input, expected) in titles) {
            assertEquals("title(\"$input\")", expected, TrackCanonical.title(input))
        }
    }

    @Test fun `artists on tricky inputs`() {
        for ((input, expected) in artists) {
            assertEquals("artist(\"$input\")", expected, TrackCanonical.artist(input))
        }
    }

    @Test fun `agrees with the TrackMatcher body it replaced`() {
        for (input in titles.keys + artists.keys) {
            assertEquals("title \"$input\"", formerTitle(input), TrackCanonical.title(input))
            assertEquals("artist \"$input\"", formerArtist(input), TrackCanonical.artist(input))
        }
    }

    /** Pairs that share a canonical title and ARE the same version: a row may be reused. */
    private val sameVersions = listOf(
        "Don't Stop (feat. X)" to "Don't Stop",
        "Song (ft. X & Y)" to "Song",
        "Song [Featuring X]" to "Song",
        "Song ft. X" to "Song",
        "Song (Official Video)" to "Song",
        "Song [Official Music Video] (HD)" to "Song",
        "Song (Lyrics)" to "Song",
        "Song (Explicit)" to "Song",
        "Song (feat. X) [Live]" to "Song (Live)",
        "SONG (LIVE)" to "song [live]",
    )

    /** Pairs that share a canonical title but are NOT the same version: each keeps its own row. */
    private val otherVersions = listOf(
        "Song (Live)" to "Song",
        "Song [Live at Wembley]" to "Song",
        "Song (Remix)" to "Song",
        "Song (Remastered 2011)" to "Song",
        "Song (Acoustic)" to "Song (Live)",
        "Song (Taylor's Version)" to "Song",
        "Song (Clean)" to "Song",
        "Song (Official Video HD)" to "Song",
        "Song (with X)" to "Song",
        // The loose rule drops everything after "feat." (so this is "song"); the version key keeps "- Live".
        "Song feat. Jay-Z - Live" to "Song",
        // The loose rule cuts a mid-word "ft " ("le" for both); the version key doesn't.
        "Left Hand Free" to "Left Outside Alone",
        "Lift Me Up" to "Lift Off",
    )

    @Test fun `credits and presentation tags are the same version`() {
        for ((a, b) in sameVersions) {
            assertEquals("canonical title of \"$a\" / \"$b\"", TrackCanonical.title(a), TrackCanonical.title(b))
            assertTrue("\"$a\" and \"$b\" are one version", TrackCanonical.sameVersion(a, b))
        }
    }

    @Test fun `live, remix, remaster and other bracketed words are other versions`() {
        for ((a, b) in otherVersions) {
            assertEquals("canonical title of \"$a\" / \"$b\"", TrackCanonical.title(a), TrackCanonical.title(b))
            assertFalse("\"$a\" and \"$b\" are two versions", TrackCanonical.sameVersion(a, b))
        }
    }

    @Test fun `version keys`() {
        assertEquals("song", TrackCanonical.versionKey("Song (feat. X) [Official Video]"))
        assertEquals("song live", TrackCanonical.versionKey("Song (Live) (feat. X)"))
        assertEquals("ft lauderdale", TrackCanonical.versionKey("Ft. Lauderdale"))
        assertEquals("", TrackCanonical.versionKey(""))
    }

    @Test fun `is idempotent on its own output`() {
        for (input in titles.keys) {
            val once = TrackCanonical.title(input)
            assertEquals(once, TrackCanonical.title(once))
        }
        for (input in artists.keys) {
            val once = TrackCanonical.artist(input)
            assertEquals(once, TrackCanonical.artist(once))
        }
    }
}

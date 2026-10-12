package com.stash.core.common

/**
 * The library's strict identity form of a title or artist: what `tracks.canonical_title` and
 * `tracks.canonical_artist` hold for a song saved from search, a stream, a hand-off or a tap.
 * Lowercased; every character that isn't a letter, a digit or whitespace becomes a space; runs of
 * whitespace collapse to one space; the ends are trimmed. Letters and digits of every script count
 * (`\p{L}`, `\p{N}`), so "Beyoncé" and "坂本龍一" keep their characters.
 *
 * Strict on purpose: nothing is dropped as noise. "(feat. X)", "[Live]" or "- Remastered" stay as
 * words, so two versions of a song stay two identities. The loose rule the sync matcher uses
 * (`TrackMatcher` in core:data) is a different rule and must stay separate.
 *
 * One function because five call sites (search downloads, the track actions, the playback
 * service, the repository, hand-off matching) used to keep byte-identical private copies: an
 * insert-if-absent lookup computed by one copy and stored by another only works while every copy
 * agrees, and a drift would file a duplicate row instead of finding the song.
 */
fun strictCanonical(s: String): String =
    s.lowercase()
        .replace(NOT_LETTER_DIGIT_OR_SPACE, " ")
        .replace(WHITESPACE_RUN, " ")
        .trim()

private val NOT_LETTER_DIGIT_OR_SPACE = Regex("[^\\p{L}\\p{N}\\s]")
private val WHITESPACE_RUN = Regex("\\s+")

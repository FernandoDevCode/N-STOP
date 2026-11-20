package com.stash.feature.search.engine

import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("(?<=\\p{IsLatin})\\p{M}+")
private val whitespace = Regex("\\s+")
private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*")

// Keep symbols and edition qualifiers for the strongest match; token matching is weaker.
internal fun searchTextKey(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace('’', '\'')
        .replace('–', '-')
        .replace('—', '-')
        .trim()
        .replace(whitespace, " ")

private class SearchText(value: String) {
    val key = searchTextKey(value)
    val words = word.findAll(key).map { it.value }.toList()
}

private enum class FieldMatch { NONE, TERMS, WORD_PREFIX, PREFIX, EQUIVALENT, EXACT }

private fun SearchText.match(field: SearchText): FieldMatch = when {
    key.isNotEmpty() && key == field.key -> FieldMatch.EXACT
    words.isEmpty() || field.words.isEmpty() -> FieldMatch.NONE
    words == field.words -> FieldMatch.EQUIVALENT
    // Punctuation can separate a single written name, e.g. initials or a band name.
    words.size == 1 && field.key.any { !it.isLetterOrDigit() && !it.isWhitespace() } &&
        words.single() == field.words.joinToString("") -> FieldMatch.EQUIVALENT
    field.words.size == 1 && key.any { !it.isLetterOrDigit() && !it.isWhitespace() } &&
        field.words.single() == words.joinToString("") -> FieldMatch.EQUIVALENT
    startsAt(field, 0) -> FieldMatch.PREFIX
    (1..(field.words.size - words.size)).any { startsAt(field, it) } -> FieldMatch.WORD_PREFIX
    words.size <= field.words.size &&
        words.distinct().all { term -> words.count { it == term } <= field.words.count { it == term } } ->
        FieldMatch.TERMS
    else -> FieldMatch.NONE
}

private fun SearchText.startsAt(field: SearchText, offset: Int): Boolean =
    offset + words.size <= field.words.size &&
        (0 until words.lastIndex).all { words[it] == field.words[offset + it] } &&
        field.words[offset + words.lastIndex].startsWith(words.last())

// Ordered evidence bands. Provider position breaks ties, including homonyms and exact
// cross-type matches without corroboration. Do not mix popularity scales across types.
private enum class Evidence {
    PROVIDER,
    RELEASE_CONTEXT,
    GUEST_CREDIT,
    LEAD_CREDIT,
    TITLE_TERMS,
    TITLE_WORD_PREFIX,
    TITLE_PREFIX,
    CORROBORATED_ARTIST_PREFIX,
    EQUIVALENT_NAME,
    EXACT_NAME,
    CORROBORATED_ARTIST,
    TITLE_AND_GUEST_CREDIT,
    TITLE_AND_CREDIT,
    COMPLETE_TITLE_AND_CREDIT,
}

internal fun rankMusicSearch(query: String, results: List<UniversalSearchResult>): List<UniversalSearchResult> {
    val input = SearchText(query)
    val creditedIds = results.flatMapTo(hashSetOf()) { it.artistIds }
    return results.map { result ->
        val title = SearchText(result.title)
        val credits = result.artistNames.ifEmpty { listOfNotNull(result.subtitle) }.map(::SearchText)
        val titleMatch = input.match(title)
        val corroboratedArtist = result.type == SearchResultType.ARTIST && result.identity.catalogId in creditedIds
        val combined = if (titleMatch >= FieldMatch.EQUIVALENT) -1 else credits.indexOfFirst {
            input.matchesBoth(title, it) || input.matchesBoth(it, title)
        }
        val evidence = when {
            combined == 0 && (input.matchesBoth(title, credits[0]) ||
                input.matchesBoth(credits[0], title, requireCompleteSecond = true)) -> Evidence.COMPLETE_TITLE_AND_CREDIT
            combined == 0 -> Evidence.TITLE_AND_CREDIT
            combined > 0 -> Evidence.TITLE_AND_GUEST_CREDIT
            titleMatch == FieldMatch.EXACT && corroboratedArtist -> Evidence.CORROBORATED_ARTIST
            titleMatch == FieldMatch.EXACT -> Evidence.EXACT_NAME
            titleMatch == FieldMatch.EQUIVALENT -> Evidence.EQUIVALENT_NAME
            titleMatch >= FieldMatch.WORD_PREFIX && corroboratedArtist -> Evidence.CORROBORATED_ARTIST_PREFIX
            titleMatch == FieldMatch.PREFIX -> Evidence.TITLE_PREFIX
            titleMatch == FieldMatch.WORD_PREFIX -> Evidence.TITLE_WORD_PREFIX
            titleMatch == FieldMatch.TERMS -> Evidence.TITLE_TERMS
            credits.firstOrNull()?.let { input.match(it) >= FieldMatch.WORD_PREFIX } == true -> Evidence.LEAD_CREDIT
            credits.drop(1).any { input.match(it) >= FieldMatch.WORD_PREFIX } -> Evidence.GUEST_CREDIT
            result.type == SearchResultType.TRACK && result.release?.title?.let {
                input.match(SearchText(it)) >= FieldMatch.EQUIVALENT
            } == true -> Evidence.RELEASE_CONTEXT
            else -> Evidence.PROVIDER
        }
        result.copy(rankingScore = evidence.ordinal.toDouble() / Evidence.entries.lastIndex)
    }
}

private fun SearchText.matchesBoth(
    first: SearchText,
    second: SearchText,
    requireCompleteSecond: Boolean = false,
): Boolean {
    val split = first.words.size
    val remaining = words.size - split
    if (split == 0 || remaining !in 1..second.words.size || words.take(split) != first.words) return false
    if (requireCompleteSecond && remaining != second.words.size) return false
    return words.subList(split, words.lastIndex) == second.words.take(remaining - 1) &&
        second.words[remaining - 1].startsWith(words.last())
}

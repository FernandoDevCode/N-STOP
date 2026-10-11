package com.stash.core.ui.components

/**
 * The letter the fast-scroll bubble shows for an item while the listener drags the scrollbar
 * cap down an A–Z list ([VerticalScrollbar]'s `sectionLabel`).
 *
 * Pass the item's SORT KEY, the exact string its list is ordered by, not its display name:
 * the bubble then can't disagree with the order the rows are actually in. Nothing is folded
 * or stripped here because the Library's A–Z sort folds nothing either (it compares
 * `name.lowercase()` code point by code point): "The Beatles" files under T, and "Édith Piaf"
 * sorts after Z, so its bubble says "É" rather than claiming an E that isn't there.
 *
 * - A key starting with a letter (any script) shows that letter in upper case: "abba" → "A",
 *   "édith" → "É", "ωmega" → "Ω", "日本" → "日".
 * - Anything else (digits, punctuation, spaces, emoji, an empty key) shows "#", the usual
 *   catch-all for the rows that sort around the letters.
 *
 * Works on the first code point, so a letter outside the Basic Multilingual Plane stays whole.
 * Pure and allocation-light: it runs once per frame while the cap is dragged.
 */
fun fastScrollSectionLetter(sortKey: String): String {
    if (sortKey.isEmpty()) return SECTION_OTHER
    val first = sortKey.codePointAt(0)
    if (!Character.isLetter(first)) return SECTION_OTHER
    return String(Character.toChars(Character.toUpperCase(first)))
}

/** The bubble for rows that don't start with a letter. */
private const val SECTION_OTHER = "#"

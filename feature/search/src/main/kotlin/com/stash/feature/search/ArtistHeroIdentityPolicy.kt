package com.stash.feature.search

import com.stash.core.data.artistphotos.ArtistIdentityKind

/**
 * A verified official/artist-linked wordmark is a first-tier identity image,
 * not merely text to overlay on the fallback portrait.
 *
 * Wikidata or Fanart symbols selected by the data layer still take precedence.
 * TheAudioDB name-only logos are intentionally excluded here: without a
 * cross-source ID we cannot safely claim they represent this artist.
 */
internal fun artistHeroWithVerifiedWordmark(hero: HeroState, wordmarkUrl: String?): HeroState {
    if (hero.artworkKind == ArtistIdentityKind.SYMBOL || wordmarkUrl.isNullOrBlank()) return hero
    return when (ArtistWordmarkLookup.attribution(wordmarkUrl)) {
        "Apple Music", "Fanart.tv (arte da comunidade)" ->
            hero.copy(avatarUrl = wordmarkUrl, artworkKind = ArtistIdentityKind.SYMBOL)
        else -> hero
    }
}

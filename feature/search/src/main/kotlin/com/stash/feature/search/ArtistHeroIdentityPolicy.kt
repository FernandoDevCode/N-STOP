package com.stash.feature.search

import com.stash.core.data.artistphotos.ArtistIdentityKind

/**
 * A verified official/artist-linked wordmark is a first-tier identity image,
 * not merely text to overlay on the fallback portrait.
 *
 * Wikidata or Fanart symbols selected by the data layer still take precedence.
 * TheAudioDB images only qualify when their MusicBrainz ID has been
 * independently cross-checked. Name-only logos remain text overlays.
 */
internal fun artistHeroWithVerifiedWordmark(hero: HeroState, wordmarkUrl: String?): HeroState {
    if (hero.artworkKind == ArtistIdentityKind.SYMBOL || wordmarkUrl.isNullOrBlank()) return hero
    return when (ArtistWordmarkLookup.attribution(wordmarkUrl)) {
        "Apple Music", "Fanart.tv (arte da comunidade)" ->
            hero.copy(avatarUrl = wordmarkUrl, artworkKind = ArtistIdentityKind.SYMBOL)
        "TheAudioDB (arte da comunidade)" ->
            if (ArtistWordmarkLookup.isVerifiedAudioDbHero(wordmarkUrl)) {
                hero.copy(avatarUrl = wordmarkUrl, artworkKind = ArtistIdentityKind.SYMBOL)
            } else hero
        else -> hero
    }
}

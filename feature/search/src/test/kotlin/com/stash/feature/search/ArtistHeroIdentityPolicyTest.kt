package com.stash.feature.search

import com.stash.core.data.artistphotos.ArtistIdentityKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtistHeroIdentityPolicyTest {
    private val photo = HeroState(
        name = "Slipknot",
        avatarUrl = "https://yt3.googleusercontent.com/old-photo",
        subscribersText = null,
    )

    @Test fun verifiedAppleArtistLogoReplacesFallbackPhoto() {
        val url = "https://is1-ssl.mzstatic.com/image/thumb/Music124/artist-logo.png"
        val chosen = artistHeroWithVerifiedWordmark(photo, url)
        assertThat(chosen.avatarUrl).isEqualTo(url)
        assertThat(chosen.artworkKind).isEqualTo(ArtistIdentityKind.SYMBOL)
    }

    @Test fun musicbrainzMatchedFanartLogoReplacesFallbackPhoto() {
        val url = "https://assets.fanart.tv/fanart/music/hdmusiclogo/logo.png"
        val chosen = artistHeroWithVerifiedWordmark(photo, url)
        assertThat(chosen.avatarUrl).isEqualTo(url)
        assertThat(chosen.artworkKind).isEqualTo(ArtistIdentityKind.SYMBOL)
    }

    @Test fun currentSymbolWinsOverAnyWordmark() {
        val logo = photo.copy(
            avatarUrl = "https://upload.wikimedia.org/wikipedia/commons/official.png",
            artworkKind = ArtistIdentityKind.SYMBOL,
        )
        assertThat(artistHeroWithVerifiedWordmark(
            logo, "https://assets.fanart.tv/fanart/music/hdmusiclogo/other.png",
        )).isEqualTo(logo)
    }

    @Test fun unreliableLogoAndMissingLogoKeepOriginalPhoto() {
        assertThat(artistHeroWithVerifiedWordmark(photo, null)).isEqualTo(photo)
        assertThat(artistHeroWithVerifiedWordmark(
            photo, "https://example.com/unverified.png",
        )).isEqualTo(photo)
        assertThat(artistHeroWithVerifiedWordmark(
            photo, "https://r2.theaudiodb.com/images/media/artist/logo/ambiguous.png",
        )).isEqualTo(photo)
    }
}

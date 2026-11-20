package com.stash.core.data.cache

import com.stash.core.data.musicbrainz.MusicBrainzClient
import com.stash.core.data.musicbrainz.mapSocials
import com.stash.data.ytmusic.model.ArtistAbout
import com.stash.data.ytmusic.model.SocialLink
import javax.inject.Inject

interface ArtistAboutEnricher { suspend fun enrich(artistName: String): ArtistAbout? }

/** No-op default so ArtistCache's existing test constructions keep compiling
 *  (mirrors NoopDiscographySupplement). */
class NoopArtistAboutEnricher : ArtistAboutEnricher {
    override suspend fun enrich(artistName: String): ArtistAbout? = null
}

class RealArtistAboutEnricher @Inject constructor(
    private val mb: MusicBrainzClient,
) : ArtistAboutEnricher {
    override suspend fun enrich(artistName: String): ArtistAbout? {
        val relsPayload = mb.searchByName(artistName)
        val socials: List<SocialLink> = relsPayload?.let { mapSocials(it) } ?: emptyList()
        return socials.takeIf { it.isNotEmpty() }?.let {
            ArtistAbout(bio = null, socials = it, photoUrl = null)
        }
    }
}

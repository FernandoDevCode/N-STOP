package com.stash.core.data.artistphotos

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/** Artist *identity*, not a claim that a particular photo depicts the current lineup. */
enum class ArtistIdentityKind { PHOTO, SYMBOL }

data class ArtistIdentityImage(val url: String, val kind: ArtistIdentityKind)

/** Metadata must be verified by Commons, never guessed from a filename or upload date. */
internal data class ArtistIdentityCandidate(
    val url: String,
    val kind: ArtistIdentityKind,
    val width: Int,
    val height: Int,
    val mime: String,
    val hasRasterThumbnail: Boolean,
)

internal object ArtistIdentityRules {
    private val qidPattern = Regex("Q[1-9][0-9]{0,11}")
    private val supportedFile = Regex("(?i)^.{1,240}\\.(?:jpg|jpeg|png|webp|svg)$")

    /** MusicBrainz' direct relation identifies the entity, never a name-only Wikidata search. */
    fun wikidataId(artist: JsonObject, requestedName: String): String? {
        val type = (artist["type"] as? JsonPrimitive)?.content
        val name = (artist["name"] as? JsonPrimitive)?.content
        if (type !in setOf("Group", "Person") || name?.trim()
                ?.equals(requestedName.trim(), ignoreCase = true) != true) return null
        val links = artist["relations"]?.jsonArray.orEmpty().mapNotNull { item ->
            val rel = item as? JsonObject ?: return@mapNotNull null
            if ((rel["type"] as? JsonPrimitive)?.content != "wikidata" ||
                (rel["ended"] as? JsonPrimitive)?.content == "true") return@mapNotNull null
            val url = ((rel["url"] as? JsonObject)?.get("resource") as? JsonPrimitive)?.content
                ?: return@mapNotNull null
            val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.host !in setOf("www.wikidata.org", "wikidata.org") ||
                uri.query != null || uri.fragment != null || uri.userInfo != null) return@mapNotNull null
            uri.path.removePrefix("/wiki/").takeIf { qidPattern.matches(it) }
        }.distinct()
        return links.singleOrNull()
    }

    /** Only P18 photographs are eligible as primary artist art.
     * P154/P2238 logos and symbols belong to secondary wordmark rendering.
     * Deprecated statements are ignored; preferred rank wins. */
    fun files(entityResponse: JsonObject, qid: String): List<Pair<String, ArtistIdentityKind>> {
        if (!qidPattern.matches(qid)) return emptyList()
        val entity = (entityResponse["entities"] as? JsonObject)?.get(qid) as? JsonObject
            ?: return emptyList()
        if ((entity["id"] as? JsonPrimitive)?.content != qid) return emptyList()
        val claims = entity["claims"] as? JsonObject ?: return emptyList()
        return listOf("P18" to ArtistIdentityKind.PHOTO)
            .mapNotNull { (property, kind) ->
                val options = claims[property]?.jsonArray.orEmpty().mapNotNull { entry ->
                    val claim = entry as? JsonObject ?: return@mapNotNull null
                    if ((claim["rank"] as? JsonPrimitive)?.content == "deprecated") return@mapNotNull null
                    val snak = claim["mainsnak"] as? JsonObject ?: return@mapNotNull null
                    if ((snak["snaktype"] as? JsonPrimitive)?.content != "value") return@mapNotNull null
                    val data = snak["datavalue"] as? JsonObject ?: return@mapNotNull null
                    val name = (data["value"] as? JsonPrimitive)?.content
                        ?.takeIf { supportedFile.matches(it) && !it.contains('/') && !it.contains('\\') }
                        ?: return@mapNotNull null
                    (name to ((claim["rank"] as? JsonPrimitive)?.content == "preferred"))
                }
                options.firstOrNull { it.second }?.first?.let { it to kind }
                    ?: options.firstOrNull()?.first?.let { it to kind }
            }
    }

    /** The first tier can only return a usable, photographic, representative image. */
    fun select(candidates: List<ArtistIdentityCandidate>): ArtistIdentityImage? {
        return candidates.firstNotNullOfOrNull { image ->
            val host = runCatching { URI(image.url) }.getOrNull()
            if (host?.scheme != "https" || host.host != "upload.wikimedia.org" ||
                host.userInfo != null || host.port != -1 || host.fragment != null) return@firstNotNullOfOrNull null
            if (image.width < 1 || image.height < 1) return@firstNotNullOfOrNull null
            if (image.kind != ArtistIdentityKind.PHOTO) return@firstNotNullOfOrNull null
            if (image.mime !in setOf("image/jpeg", "image/png", "image/webp"))
                return@firstNotNullOfOrNull null
            val ratio = image.width.toDouble() / image.height
            // The first tier is a photographic HERO, not a portrait or logo.
            // A tall photo may still be used by the catalogue as the last fallback;
            // this tier only accepts compositions wide enough for a band header.
            val suitable = image.width >= 800 && image.height >= 450 &&
                ratio in 1.05..3.2
            if (suitable) ArtistIdentityImage(image.url, image.kind) else null
        }
    }
}

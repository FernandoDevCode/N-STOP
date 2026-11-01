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

    /**
     * P154 = curated logo; P18 = curated representative image. A square-ish logo
     * may be a symbol, but a wide wordmark is NOT a hero image (the UI has a wordmark).
     * Deprecated statements are ignored; preferred rank wins within each property.
     */
    fun files(entityResponse: JsonObject, qid: String): List<Pair<String, ArtistIdentityKind>> {
        if (!qidPattern.matches(qid)) return emptyList()
        val entity = (entityResponse["entities"] as? JsonObject)?.get(qid) as? JsonObject
            ?: return emptyList()
        if ((entity["id"] as? JsonPrimitive)?.content != qid) return emptyList()
        val claims = entity["claims"] as? JsonObject ?: return emptyList()
        return listOf("P154" to ArtistIdentityKind.SYMBOL, "P18" to ArtistIdentityKind.PHOTO)
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

    /** P2238 may point to a dedicated official-symbol entity, not a file on the band. */
    fun officialSymbolId(entityResponse: JsonObject, qid: String): String? {
        if (!qidPattern.matches(qid)) return null
        val entity = (entityResponse["entities"] as? JsonObject)?.get(qid) as? JsonObject
            ?: return null
        if ((entity["id"] as? JsonPrimitive)?.content != qid) return null
        val claims = entity["claims"] as? JsonObject ?: return null
        val ids = claims["P2238"]?.jsonArray.orEmpty().mapNotNull { statement ->
            val claim = statement as? JsonObject ?: return@mapNotNull null
            if ((claim["rank"] as? JsonPrimitive)?.content == "deprecated") return@mapNotNull null
            val snak = claim["mainsnak"] as? JsonObject ?: return@mapNotNull null
            if ((snak["snaktype"] as? JsonPrimitive)?.content != "value") return@mapNotNull null
            val value = (snak["datavalue"] as? JsonObject)?.get("value") as? JsonObject
                ?: return@mapNotNull null
            (value["id"] as? JsonPrimitive)?.content?.takeIf(qidPattern::matches)
        }.distinct()
        return ids.singleOrNull()
    }

    /** Fanart community rating only counts on an exact MBID+name match.
     * Unlike P154, logos from this source need evidence of community adoption. */
    fun fanartLogo(response: JsonObject, name: String, mbid: String): ArtistIdentityImage? {
        val sourceName = (response["name"] as? JsonPrimitive)?.content?.trim()
        val sourceId = ((response["mbid_id"] ?: response["mbid"]) as? JsonPrimitive)?.content
        if (sourceName?.equals(name.trim(), ignoreCase = true) != true ||
            sourceId?.equals(mbid, ignoreCase = true) != true) return null
        val options = listOf("hdmusiclogo", "musiclogo").flatMap { key ->
            response[key]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val url = (obj["url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val likes = (obj["likes"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
                if (uri.scheme != "https" || uri.host != "assets.fanart.tv" ||
                    uri.port != -1 || uri.userInfo != null || uri.query != null ||
                    uri.fragment != null || !uri.path.endsWith(".png", ignoreCase = true) ||
                    !uri.path.contains("/musiclogo/") && !uri.path.contains("/hdmusiclogo/")) {
                    return@mapNotNull null
                }
                if (likes < 5) return@mapNotNull null
                ArtistIdentityImage(url, ArtistIdentityKind.SYMBOL) to likes
            }
        }
        return options.maxByOrNull { it.second }?.first
    }

    /** Recognizable identity graphics fit the hero without the photo's crop. */
    fun select(candidates: List<ArtistIdentityCandidate>): ArtistIdentityImage? {
        return candidates.firstNotNullOfOrNull { image ->
            val host = runCatching { URI(image.url) }.getOrNull()
            if (host?.scheme != "https" || host.host != "upload.wikimedia.org" ||
                host.userInfo != null || host.port != -1 || host.fragment != null) return@firstNotNullOfOrNull null
            if (image.width < 1 || image.height < 1) return@firstNotNullOfOrNull null
            val supported = image.mime in setOf("image/jpeg", "image/png", "image/webp") ||
                (image.mime == "image/svg+xml" && image.hasRasterThumbnail)
            if (!supported) return@firstNotNullOfOrNull null
            val ratio = image.width.toDouble() / image.height
            val suitable = when (image.kind) {
                // Identity assets often ARE deliberately wide (Queen, Nirvana,
                // AC/DC, etc.). This is presentation metadata, not photo art:
                // Fit preserves the entire mark and avoids cropping.
                ArtistIdentityKind.SYMBOL ->
                    ratio in 0.22..6.5 &&
                    (image.mime == "image/svg+xml" ||
                        (image.width >= 280 && image.height >= 100))
                ArtistIdentityKind.PHOTO ->
                    image.width >= 800 && image.height >= 450 && ratio in 0.5..3.2
            }
            if (suitable) ArtistIdentityImage(image.url, image.kind) else null
        }
    }
}

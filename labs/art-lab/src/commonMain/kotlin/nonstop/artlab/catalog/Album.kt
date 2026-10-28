package nonstop.artlab.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class ArtworkStatus { OFFICIAL_COVER, REFERENCE_EXTRACT, MISSING, LOCAL }

@Serializable
data class Album(
    val artist: String,
    val album: String,
    val track: String? = null,
    val durationSeconds: Int? = null,
    val resource: String? = null,
    val status: ArtworkStatus = ArtworkStatus.MISSING,
    val source: String? = null,
    val sha256: String? = null,
)


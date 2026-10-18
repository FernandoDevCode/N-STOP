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

val validationCatalog = listOf(
    Album("Pink Floyd", "The Dark Side of the Moon", "Time", 383, "covers/PINK_FLOYD.jpg", ArtworkStatus.OFFICIAL_COVER,
        "https://music.apple.com/us/album/the-dark-side-of-the-moon/1065973699", "7b5ad4cdeed66d53928f485667e8d5bf764529d4d7c45fb620f1ea31913445cb"),
    Album("Megadeth", "Rust in Peace", "Holy Wars... The Punishment Due", 392, "covers/MEGADETH.jpg", ArtworkStatus.OFFICIAL_COVER,
        "https://music.apple.com/us/album/rust-in-peace/724648893", "3a1ef3872b81e1dbccbc60673f1e5f20db80e222b7d3414b2c85be3b6a227c6e"),
    Album("Nirvana", "Nevermind"), Album("Michael Jackson", "Thriller"),
    Album("The Beatles", "Abbey Road"), Album("Daft Punk", "Random Access Memories"),
    Album("Joy Division", "Unknown Pleasures"), Album("Kendrick Lamar", "To Pimp a Butterfly"),
    Album("Travis Scott", "Astroworld"), Album("Frank Ocean", "Blonde"),
    Album("Metallica", "Master of Puppets"), Album("Kanye West", "Graduation"),
)

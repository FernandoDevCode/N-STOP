package nonstop.artlab.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class ArtworkStatus { REFERENCE_EXTRACT, MISSING, LOCAL }

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
    Album("Pink Floyd", "The Dark Side of the Moon", "Time", 383, "PINK_FLOYD.png", ArtworkStatus.REFERENCE_EXTRACT,
        "docs/player-v8/references/MASTER_PINK_FLOYD.png — recorte do mockup, não capa original", "6b7cbd4da0fa601379b2d8d790d70aac2b532d22bb35739370fc3958fec430f0"),
    Album("Megadeth", "Rust in Peace", "Holy Wars... The Punishment Due", 392, "MEGADETH.png", ArtworkStatus.REFERENCE_EXTRACT,
        "docs/player-v8/references/MASTER_MEGADETH.png — recorte do mockup, não capa original", "3547184210edca28399a0945dc28192e65341ebd1545be070873593004e3d68d"),
    Album("Nirvana", "Nevermind"), Album("Michael Jackson", "Thriller"),
    Album("The Beatles", "Abbey Road"), Album("Daft Punk", "Random Access Memories"),
    Album("Joy Division", "Unknown Pleasures"), Album("Kendrick Lamar", "To Pimp a Butterfly"),
    Album("Travis Scott", "Astroworld"), Album("Frank Ocean", "Blonde"),
    Album("Metallica", "Master of Puppets"), Album("Kanye West", "Graduation"),
)

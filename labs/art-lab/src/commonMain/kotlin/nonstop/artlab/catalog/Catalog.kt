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
    Album("Nirvana", "Nevermind", resource="covers/NIRVANA.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/nevermind/1440783617", sha256="639ea600f32f14ad0c2750cd3b54dc6744f9d405574f66e00a8934a7514b6513"),
    Album("Michael Jackson", "Thriller", resource="covers/THRILLER.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/thriller/269572838", sha256="12f4105989a5e3148f8a681526988ab2638fa3b66c519bce9b1903b302baf546"),
    Album("The Beatles", "Abbey Road", resource="covers/ABBEY_ROAD.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/abbey-road-remastered/1441164426", sha256="0264eb6b70f19331a4d321cc656d0b6a24aa22594c08bfa27169023321398d71"),
    Album("Daft Punk", "Random Access Memories", resource="covers/DAFT_PUNK.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/random-access-memories/617154241", sha256="d16a3ba3e50c5f50ed2aa42bf7d1d7e880e38b0744a60d3cf23a30caaa79e276"),
    Album("Joy Division", "Unknown Pleasures", resource="covers/JOY_DIVISION.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/unknown-pleasures-2019-digital-master/1476702180", sha256="30c838c093425349457a451157ce85a40d8117316f52e1dd0f8a988ca67c706e"),
    Album("Kendrick Lamar", "To Pimp a Butterfly", resource="covers/KENDRICK.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/to-pimp-a-butterfly/1440871877", sha256="a9d6e383bb83561f80c8ec64ed3fc2924b77e9296ac415fbbdf87f15ac37decf"),
    Album("Travis Scott", "Astroworld", resource="covers/ASTROWORLD.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/astroworld/1421658111", sha256="b1964d164919309487c35d8cd297073c3f4de6f641cf0ddbe60827f2447ab1e6"),
    Album("Frank Ocean", "Blonde", resource="covers/BLONDE.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/blonde/1146195596", sha256="b9a74397e4ae05fe2c4c51402d774feb659f533d22ad2998c655716f31741ae1"),
    Album("Metallica", "Master of Puppets", resource="covers/METALLICA.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/master-of-puppets-remastered/1275600311", sha256="5b26ba7f3f5af912fd8a60a865060adc80265a156c7ce931c040bf43fe967835"),
    Album("Kanye West", "Graduation", resource="covers/GRADUATION.jpg", status=ArtworkStatus.OFFICIAL_COVER,
        source="https://music.apple.com/us/album/graduation/1442845779", sha256="afb3e2138c48444a75db2ac925716a5e815121affa3f686d788ce57b1e5272c7"),
)

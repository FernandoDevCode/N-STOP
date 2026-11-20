package com.stash.core.data.mix

/**
 * Stored recipe identifiers kept for database compatibility.
 * External graph seeding was retired; current mixes are generated locally.
 */
enum class MixSeedStrategy(val storedValue: String) {
    ARTIST_SIMILAR("ARTIST_SIMILAR"),
    TAG_GRAPH("TAG_GRAPH"),
    TRACK_SIMILAR("TRACK_SIMILAR"),
    NONE("NONE");

    companion object {
        fun fromStored(s: String): MixSeedStrategy =
            entries.firstOrNull { it.storedValue == s } ?: ARTIST_SIMILAR
    }
}

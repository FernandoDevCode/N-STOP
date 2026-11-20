package com.stash.core.data.mix

import org.junit.Assert.assertEquals
import org.junit.Test

class MixSeedStrategyTest {
    @Test fun persistedRecipeValuesRemainReadable() {
        assertEquals(MixSeedStrategy.ARTIST_SIMILAR, MixSeedStrategy.fromStored("garbage"))
        assertEquals(MixSeedStrategy.TAG_GRAPH, MixSeedStrategy.fromStored("TAG_GRAPH"))
        assertEquals(MixSeedStrategy.TRACK_SIMILAR, MixSeedStrategy.fromStored("TRACK_SIMILAR"))
        assertEquals(MixSeedStrategy.NONE, MixSeedStrategy.fromStored("NONE"))
    }
}

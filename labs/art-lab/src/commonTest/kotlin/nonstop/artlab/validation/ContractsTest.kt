package nonstop.artlab.validation

import kotlin.test.*
import kotlinx.serialization.encodeToString
import nonstop.artlab.catalog.*
import nonstop.artlab.direction.*
import nonstop.artlab.inference.*
import nonstop.artlab.renderer.progressX

class ContractsTest {
    @Test fun fixedCatalogHasTwelveHonestEntries() {
        assertEquals(12,validationCatalog.size)
        assertEquals(2,validationCatalog.count { it.status==ArtworkStatus.REFERENCE_EXTRACT })
        assertEquals(10,validationCatalog.count { it.status==ArtworkStatus.MISSING && it.resource==null && it.track==null })
        assertEquals("The Dark Side of the Moon",validationCatalog[0].album)
        assertEquals("Rust in Peace",validationCatalog[1].album)
    }
    @Test fun deterministicRecipeRoundTripsWithoutInventingScores() {
        val first=baselineRecipe("fixture")
        assertTrue(first.isValid())
        val json=recipeJson.encodeToString(first)
        assertEquals(json,recipeJson.encodeToString(baselineRecipe("fixture")))
        assertEquals(first,loadRecipe(json))
        assertNull(first.confidence)
        assertTrue(first.archetypeScores.isEmpty())
    }
    @Test fun invalidNumbersAndJsonHaveStableFallback() {
        val good=baselineRecipe("fixture")
        assertEquals(good,safeRecipe(good.copy(scrimTop=Float.NaN)))
        assertEquals(good,safeRecipe(good.copy(lights=listOf(good.lights[0].copy(radiusX=-1f)))))
        assertEquals(good,safeRecipe(good.copy(progress=good.progress.copy(fill=listOf(Stop(1f,0xff000000),Stop(0f,0xffffffff))))))
        assertEquals(baselineRecipe("invalid-json"),loadRecipe("broken"))
    }
    @Test fun seekRevealsFixedFullTrackCoordinates() {
        assertEquals(listOf(0f,200f,400f,800f),listOf(0f,.25f,.5f,1f).map { progressX(it,800f) })
        assertEquals(0f,progressX(-1f,800f)); assertEquals(800f,progressX(2f,800f))
    }
}

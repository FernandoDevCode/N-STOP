package com.stash.core.art

import nonstop.artlab.direction.baselineRecipe
import nonstop.artlab.renderer.AtmospherePrecomputer
import nonstop.artlab.renderer.compositeAtmosphere
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class AndroidDirectorContractTest {
    @Test fun cancellationStopsAnObsoleteCoverBetweenRows() {
        var rows = 0
        try {
            AtmospherePrecomputer.render(baselineRecipe("cancel"), 32, 32) {
                if (++rows == 3) throw CancellationException()
            }
            fail("Obsolete cover should not finish rendering")
        } catch (_: CancellationException) {
            assertEquals(3, rows)
        }
    }
    @Test fun cancellationHookDoesNotChangeProceduralPixels() {
        val recipe = baselineRecipe("same-cover")
        val a = compositeAtmosphere(AtmospherePrecomputer.render(recipe, 32, 32))
        var rows = 0
        val b = compositeAtmosphere(AtmospherePrecomputer.render(recipe, 32, 32) { rows++ })
        assertEquals(32, rows)
        assertArrayEquals(a.argb, b.argb)
    }
}

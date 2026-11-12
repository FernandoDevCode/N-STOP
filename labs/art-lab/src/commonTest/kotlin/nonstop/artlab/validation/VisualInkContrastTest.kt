package nonstop.artlab.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nonstop.artlab.features.PixelImage
import nonstop.artlab.renderer.VisualInkContrast

/** Local contrast: not a global light/dark toggle based on the cover average. */
class VisualInkContrastTest {
    private fun solid(c: Int): VisualInkContrast =
        VisualInkContrast.from(PixelImage(48,96,IntArray(48*96) { c }))

    @Test fun whiteBackgroundUsesGraphiteInk() {
        val white=solid(0xffffffff.toInt())
        assertTrue(white.darkInkAt(.5f,.5f,0f,0f))
        assertTrue(white.darkInkAt(.1f,.9f,0f,0f))
    }

    @Test fun blackBackgroundKeepsWhiteInk() {
        val dark=solid(0xff101115.toInt())
        assertFalse(dark.darkInkAt(.5f,.5f,0f,0f))
        assertFalse(dark.darkInkAt(.9f,.1f,.3f,0f))
    }

    @Test fun regionSamplingIsIndependentPerControl() {
        val pixels=IntArray(48*96) { i ->
            if ((i%48)<24) 0xffffffff.toInt() else 0xff0b0e14.toInt()
        }
        val map=VisualInkContrast.from(PixelImage(48,96,pixels))
        assertTrue(map.darkInkAt(.14f,.6f,0f,0f))
        assertFalse(map.darkInkAt(.86f,.6f,0f,0f))
    }

    @Test fun actualBlackScrimChangesInkChoice() {
        val white=solid(0xffffffff.toInt())
        assertTrue(white.darkInkAt(.5f,.06f,0f,0f))
        assertFalse(white.darkInkAt(.5f,.06f,1f,0f))
    }

    @Test fun coordinateClampingIsSafeForUnknownViewports() {
        val map=solid(0xffffffff.toInt())
        assertTrue(map.darkInkAt(-1f,2f,0f,0f))
        assertTrue(map.darkInkAt(2f,-1f,0f,0f))
    }
}

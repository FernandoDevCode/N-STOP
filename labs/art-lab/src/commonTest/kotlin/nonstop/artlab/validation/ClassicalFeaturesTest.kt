package nonstop.artlab.validation

import kotlin.test.*
import nonstop.artlab.features.*
import nonstop.artlab.direction.*
import kotlinx.serialization.encodeToString

class ClassicalFeaturesTest {
    @Test fun directionalEvidenceRotatesWithPixelsAndUniformInputIsConservative() {
        fun band(vertical:Boolean):ImageFeatures {
            val image=IntArray(128*128) { i ->
                val x=i%128; val y=i/128
                if(if(vertical)x in 48..55 && y in 5..120 else y in 48..55 && x in 5..120)0xff00ffff.toInt() else 0xff000000.toInt()
            }
            return ClassicalFeatureExtractor().analyze(PixelImage(128,128,image))
        }
        val horizontal=band(false); val vertical=band(true)
        val a=horizontal.palette.first { it.saturation>.8 }; val b=vertical.palette.first { it.saturation>.8 }
        assertTrue(a.coherence>.9 && b.coherence>.9)
        assertTrue(kotlin.math.abs(a.orientation)<1)
        assertTrue(kotlin.math.abs(kotlin.math.abs(b.orientation)-90)<1)
        assertNotEquals(horizontal.directions,vertical.directions)
        val plain=ClassicalFeatureExtractor().analyze(fixture(0xff000000.toInt()))
        assertTrue(plain.directions.isEmpty())
        assertTrue(ClassicalDirector.direct(plain,"opaque").directionalDetails.isEmpty())
        val r=ClassicalDirector.direct(horizontal,"opaque")
        assertFalse(r.copy(directionalDetails=listOf(DirectionalDetail(.5f,.5f,Float.NaN,.2f,.01f,0xffffffff,.2f,1f))).isValid())
    }
    private fun fixture(color:Int) = PixelImage(64,64,IntArray(4096) { color })
    @Test fun blackWhiteAndMonochromeHaveFiniteNormalizedFeaturesAndLegibleRecipes() {
        for(c in listOf(0xff000000.toInt(),0xffffffff.toInt(),0xff777777.toInt(),0xff001aff.toInt())) {
            val f=ClassicalFeatureExtractor().analyze(fixture(c))
            assertTrue(f.isValid()); assertEquals(1.0,f.lowFraction+f.midFraction+f.highFraction,.00001)
            val r=ClassicalDirector.direct(f,"same-metadata-free-id")
            assertTrue(r.isValid()); assertFalse(recipeJson.encodeToString(r).contains("NaN"))
            assertNull(r.confidence)
            assertTrue(r.controlSurface.fill.all { contrast(r.controlSurface.ink,it.color)>=4.5 })
        }
    }
    @Test fun changingThresholdsChangesMeasuredCoverage() {
        val image=fixture(0xff555555.toInt())
        val a=ClassicalFeatureExtractor(FeaturePolicy(lowLuminance=.02)).analyze(image)
        val b=ClassicalFeatureExtractor(FeaturePolicy(lowLuminance=.15)).analyze(image)
        assertNotEquals(a.lowFraction,b.lowFraction)
        val blue=fixture(0xff000033.toInt())
        assertEquals(0.0,ClassicalFeatureExtractor().analyze(blue).saturatedFraction)
        assertEquals(1.0,ClassicalFeatureExtractor(FeaturePolicy(accentMinLuminance=0.0)).analyze(blue).saturatedFraction)
    }
    @Test fun peripheralAccentIsRetainedAndMovesLightsWithoutMetadata() {
        fun image(right:Boolean):PixelImage {
            val pixels=IntArray(128*128) { 0xff000000.toInt() }
            for(y in 10..32) for(x in 2..18) pixels[y*128+(if(right)127-x else x)]=0xffff3030.toInt()
            return PixelImage(128,128,pixels)
        }
        val a=ClassicalFeatureExtractor().analyze(image(false)); val b=ClassicalFeatureExtractor().analyze(image(true))
        assertTrue(a.saturatedX<.2); assertTrue(b.saturatedX>.8)
        assertTrue(a.grid.first().importance>0)
        val ra=ClassicalDirector.direct(a,"identical-id"); val rb=ClassicalDirector.direct(b,"identical-id")
        assertNotEquals(ra.accents,rb.accents)
    }
    @Test fun allRecipeNumericAndContrastBoundariesRejectInvalidData() {
        val r=baselineRecipe("test")
        assertFalse(r.copy(controlSurface=r.controlSurface.copy(haloIntensity=Float.NaN)).isValid())
        assertFalse(r.copy(lights=listOf(r.lights[0].copy(color=-1))).isValid())
        assertFalse(r.copy(lyrics=r.lyrics.copy(alphaProgression=listOf(Float.POSITIVE_INFINITY))).isValid())
        assertFalse(r.copy(controlSurface=r.controlSurface.copy(ink=r.controlSurface.fill[0].color)).isValid())
        assertEquals(r,safeRecipe(r.copy(controlSurface=r.controlSurface.copy(haloRadius=Float.NaN))))
    }
}

package nonstop.artlab.validation

import kotlin.test.*
import nonstop.artlab.features.*
import nonstop.artlab.direction.*
import nonstop.artlab.renderer.*
import kotlinx.serialization.encodeToString

class ProceduralDirectorTest {
    @Test fun measuredEvidenceCanCombineEveryFamilyAndSelectConservativeFallback() {
        val size=128
        fun direct(pixel:(Int,Int)->Long):ArtRecipe {
            val p=PixelImage(size,size,IntArray(size*size) { pixel(it%size,it/size).toInt() })
            return ClassicalDirector.direct(ClassicalFeatureExtractor().analyze(p),"same-opaque-hash")
        }
        val examples=listOf(
            "sparse-separated" to direct { x,y -> if(y in 40..44 && x in 16..111)fromHsv(((x-16)/16*60).toDouble(),.9,.95) else 0xff000000 },
            "dense-chromatic" to ClassicalDirector.direct(ClassicalFeatureExtractor().analyze(SyntheticImages.image("multicolor")),"same-opaque-hash"),
            "editorial-detail" to direct { x,y -> val v=if((x/3+y/3)%2==0).24 else .62; argb(v,v,v) },
            "dramatic" to direct { x,_ -> if(x<100)0xff080408 else 0xffff3737 },
            "selective-light" to direct { x,y -> if((x-64)*(x-64)+(y-64)*(y-64)<29*29)0xffffedc5 else 0xff292c30 },
            "minimal" to direct { _,_ -> 0xff7a7a7a },
            "inconclusive" to direct { x,y -> val v=if((x+y)%2==0).46 else .53; argb(v,v,v) })
        examples.forEach { (name,r) -> println("FAMILY_FIXTURE $name -> ${r.family} ${r.familyWeights}") }
        assertEquals(ArtFamily.entries.toSet(),examples.flatMap { it.second.familyWeights.keys }.toSet())
        assertTrue(examples.first { it.first=="dramatic" }.second.familyWeights.getValue(ArtFamily.DRAMATIC_CONTRAST)>.35f)
        assertEquals(ArtFamily.SELECTIVE_LUMINOUS,examples.first { it.first=="selective-light" }.second.family)
        assertEquals(ArtFamily.NEUTRAL_FALLBACK,examples.first { it.first=="inconclusive" }.second.family)
        assertEquals(ControlMaterial.entries.toSet(),examples.map { it.second.controlSurface.material }.toSet())
    }
    @Test fun syntheticExtremesStayFiniteDeterministicLegibleAndTextured() {
        for(name in SyntheticImages.names) {
            val f=ClassicalFeatureExtractor().analyze(SyntheticImages.image(name))
            val r=ClassicalDirector.direct(f,"synthetic-$name")
            assertTrue(r.isValid(),name)
            assertEquals(r,loadRecipe(recipeJson.encodeToString(r)),name)
            assertTrue(r.familyWeights.size<=2 && r.familyWeights.values.sum()>.999f)
            assertEquals(r,ClassicalDirector.direct(f,"synthetic-$name"))
            val texture=compositeAtmosphere(AtmospherePrecomputer.render(r,48,86))
            assertTrue(texture.argb.all { it ushr 24==255 })
            assertTrue(texture.argb.distinct().size>8,"$name must retain tonal depth")
            val material=r.controlSurface
            for(i in 0..20)assertTrue(contrast(material.ink,mix(material.fill.first().color,material.fill.last().color,i/20.0))>=4.5,name)
            println("SYNTHETIC $name -> ${r.family} ${r.familyWeights} ${material.material}")
        }
    }
    @Test fun hashChangesNoiseButCannotChangeFamilyPaletteOrMaterial() {
        val f=ClassicalFeatureExtractor().analyze(SyntheticImages.image("multicolor"))
        val a=ClassicalDirector.direct(f,"content-A"); val b=ClassicalDirector.direct(f,"content-B")
        assertNotEquals(a.procedural.seed,b.procedural.seed)
        assertEquals(a.copy(sourceImageHash="same",procedural=a.procedural.copy(seed=0)),
            b.copy(sourceImageHash="same",procedural=b.procedural.copy(seed=0)))
        val p=AtmospherePrecomputer.render(a,48,86); val q=AtmospherePrecomputer.render(b,48,86)
        assertFalse(p.deep.argb.contentEquals(q.deep.argb))
        assertContentEquals(p.deep.argb,AtmospherePrecomputer.render(a,48,86).deep.argb)
        assertNotEquals(proceduralSeed("content-A"),proceduralSeed("content-A:${ClassicalDirector.VERSION}"))
    }
    @Test fun everyProceduralParameterHasARealBoundedConsumer() {
        val r=baselineRecipe("primitive-fixture").copy(environment=Environment(regions=listOf(Light(.4f,.3f,.8f,.6f,0xff254467,.7f))))
        val original=AtmospherePrecomputer.render(r,48,86).deep.argb
        val p=r.procedural
        for(changed in listOf(p.copy(noiseScale=6f),p.copy(octaves=1),p.copy(roughness=.8f),
            p.copy(organic=.9f),p.copy(mist=.8f),p.copy(curves=.5f),p.copy(vignette=.9f))) {
            assertFalse(original.contentEquals(AtmospherePrecomputer.render(r.copy(procedural=changed),48,86).deep.argb),"No response: $changed")
        }
        assertFalse(r.copy(procedural=p.copy(octaves=20)).isValid())
        assertFalse(r.copy(procedural=p.copy(mist=Float.NaN)).isValid())
        assertFalse(r.copy(familyWeights=mapOf(ArtFamily.PRISMATIC to .2f)).isValid())
    }
}

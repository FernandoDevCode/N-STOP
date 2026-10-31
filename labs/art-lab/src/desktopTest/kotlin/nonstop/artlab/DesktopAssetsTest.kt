package nonstop.artlab

import nonstop.artlab.catalog.*
import kotlin.test.*
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import nonstop.artlab.direction.*
import nonstop.artlab.features.*
import nonstop.artlab.renderer.*
import kotlinx.serialization.encodeToString

class DesktopAssetsTest {
    @Test fun replacedSelectionSkipsQueuedPreparation() {
        val directory=File("build/test-cache/canceled-${System.nanoTime()}")
        val assets=DesktopAssets(directory)
        val canceled=assets.load(validationCatalog.first()) { false }
        assertNull(canceled.cover)
        assertEquals(0,assets.analysisCount)
        assertFalse(directory.exists())
    }
    @Test fun fullCatalogIsVerifiedAndMemoryCacheEvictsWithoutRegeneration() {
        val directory=File("build/test-cache/catalog-${System.nanoTime()}")
        val assets=DesktopAssets(directory,memoryLimit=1)
        val originals=validationCatalog.map { album ->
            val a=assets.load(album)
            assertNotNull(a.cover,"${album.album}: ${a.message}")
            assertEquals(album.sha256,a.hash)
            assertEquals(CacheOrigin.MISS,a.cacheOrigin)
            assertTrue(a.timings.generationMs>0)
            assertTrue(a.retainedRasterBytes in 1..8_000_000)
            if(a.recipe.controlSurface.material==ControlMaterial.PRISMATIC)assertTrue(
                a.recipe.controlSurface.fill.all { luminance(it.color)<.025 },"Prismatic center must stay dark for every cover")
            a.recipe
        }
        assertEquals(12,assets.analysisCount)
        val evicted=assets.load(validationCatalog.first())
        assertEquals(CacheOrigin.DISK,evicted.cacheOrigin)
        assertEquals(0.0,evicted.timings.generationMs)
        assertEquals(12,assets.analysisCount)
        assertEquals(originals.first(),evicted.recipe)
        assertNotNull(assets.thumbnail(validationCatalog.first()))
        assertEquals(12,assets.analysisCount,"Thumbnail must not analyze")
        // Checksum mismatch must fail before direction even when the name/source looks valid.
        assertNull(assets.load(validationCatalog.first().copy(sha256="incorrect")).cover)
        val disguised=validationCatalog.first().copy(artist="Random",album="Nothing",source="unrelated")
        assertEquals(originals.first(),assets.load(disguised).recipe)
        // A valid, but replaced PNG must also be rejected, not only a truncated PNG.
        val composite=directory.walkTopDown().first { it.name=="composite.png" }
        ImageIO.write(BufferedImage(192,342,BufferedImage.TYPE_INT_ARGB),"png",composite)
        val reloaded=DesktopAssets(directory)
        validationCatalog.forEach { assertNotNull(reloaded.load(it).cover) }
        assertEquals(1,reloaded.analysisCount)
    }
    @Test fun corruptImageFallsBackAndValidPngJpegAreCachedByContent() {
        val assets=DesktopAssets(File("build/test-cache/corrupt-${System.nanoTime()}"))
        val folder=File("build/test-images").apply { mkdirs() }
        val broken=File(folder,"corrupt.png").apply { writeText("not an image") }
        val invalid=assets.load(Album("local","invalid",resource=broken.absolutePath,status=ArtworkStatus.LOCAL))
        assertNull(invalid.cover)
        assertTrue(invalid.message.startsWith("Falha de decode"))
        for(ext in listOf("png","jpg")) {
            val file=File(folder,"valid.$ext")
            ImageIO.write(BufferedImage(64,64,BufferedImage.TYPE_INT_RGB),ext,file)
            val album=Album("local","test",resource=file.absolutePath,status=ArtworkStatus.LOCAL)
            val cold=assets.load(album); val warm=assets.load(album)
            assertNotNull(cold.cover); assertNotNull(cold.texture)
            assertFalse(cold.cacheHit); assertTrue(warm.cacheHit)
            assertEquals(cold.hash,warm.hash)
        }
    }
    @Test fun originalCoversProduceDeterministicDifferentRecipesAndDiskMemoryHits() {
        val directory=File("build/test-cache/real-${System.nanoTime()}")
        val cold=DesktopAssets(directory)
        val first=validationCatalog.take(2).map { cold.load(it) }
        first.forEach { a -> assertNotNull(a.cover,a.message); assertNotNull(a.features); assertTrue(a.recipe.isValid()); assertFalse(a.cacheHit) }
        assertEquals(2,cold.analysisCount)
        val repeated=cold.load(validationCatalog[0]); assertEquals(CacheOrigin.MEMORY,repeated.cacheOrigin)
        assertEquals(2,cold.analysisCount)
        val warm=DesktopAssets(directory)
        val disk=validationCatalog.take(2).map { warm.load(it) }
        assertEquals(0,warm.analysisCount)
        assertTrue(disk.all { it.cacheOrigin==CacheOrigin.DISK })
        first.zip(disk).forEach { (a,b) -> assertEquals(recipeJson.encodeToString(a.recipe),recipeJson.encodeToString(b.recipe)) }
        val fresh=DesktopAssets(File("build/test-cache/recompute-${System.nanoTime()}"))
        val recomputed=fresh.load(validationCatalog[0])
        assertEquals(first[0].recipe,recomputed.recipe)
        assertNotEquals(first[0].recipe.copy(sourceImageHash="same"),first[1].recipe.copy(sourceImageHash="same"))
        assertNotEquals(first[0].recipe.canvasColor,first[1].recipe.canvasColor)
        assertNotEquals(first[0].recipe.controlSurface,first[1].recipe.controlSurface)
        assertNotEquals(first[0].recipe.progress.fill,first[1].recipe.progress.fill)
        assertTrue(first[0].recipe.controlSurface.rim.any { hsv(it.color)[0] in 210.0..300.0 },"Muted small blue/violet bands must survive negative-space separation")
        first.forEach { a ->
            val surface=a.recipe.controlSurface
            for(i in 0..32) {
                val fill=mix(surface.fill.first().color,surface.fill.last().color,i/32.0)
                assertTrue(contrast(surface.ink,fill)>=4.5)
            }
        }
        val renamed=validationCatalog[0].copy(artist="unrelated",album="unrelated")
        assertEquals(first[0].recipe,cold.load(renamed).recipe)
        val versioned=DesktopAssets(directory,pipelineVersion="new-policy-version")
        assertFalse(versioned.load(validationCatalog[0]).cacheHit)
        assertEquals(1,versioned.analysisCount)
        val report=File("build/reports/classical").apply { mkdirs() }
        first.forEachIndexed { i,a ->
            File(report,"reference-${i+1}-recipe.json").writeText(recipeJson.encodeToString(a.recipe))
            File(report,"reference-${i+1}-features.json").writeText(recipeJson.encodeToString(a.features))
            File(report,"reference-${i+1}-timings.txt").writeText(a.timings.toString())
            File(report,"reference-${i+1}-palette-contrast.json").writeText(recipeJson.encodeToString(a.features!!.palette.map { c ->
                linkedMapOf("argb" to c.color.toDouble(),"coverage" to c.coverage,"x" to c.x,"y" to c.y,
                    "contrastAgainstCanvas" to contrast(c.color,a.recipe.canvasColor))
            }))
        }
    }
    @Test fun abstractPrecomputeIsDeterministicBoundedAndEveryLayerIsEffective() {
        val assets=DesktopAssets(File("build/test-cache/layers-${System.nanoTime()}"))
        for(album in validationCatalog.take(2)) {
            val a=assets.load(album); assertNotNull(a.cover,a.message)
            val r=a.recipe
            val first=AtmospherePrecomputer.render(r,64,114)
            val second=AtmospherePrecomputer.render(r,64,114)
            assertContentEquals(first.deep.argb,second.deep.argb)
            assertContentEquals(first.directional.argb,second.directional.argb)
            val changed=AtmospherePrecomputer.render(r.copy(environment=Environment(),lights=emptyList(),accents=emptyList(),directionalDetails=emptyList()),64,114)
            assertFalse(first.deep.argb.contentEquals(changed.deep.argb))
            assertFalse(first.diffuse.argb.contentEquals(changed.diffuse.argb))
            assertFalse(first.accents.argb.contentEquals(changed.accents.argb))
            if(r.directionalDetails.isNotEmpty())assertFalse(first.directional.argb.contentEquals(changed.directional.argb))
            assertTrue(changed.diffuse.argb.all { it==0 })
            for(mode in ComparisonMode.entries)assertNotNull(comparisonCover(album,a,mode))
            assertEquals(r,assets.load(album).recipe)
        }
        // Isolated off-center masks: no axis-aligned rectangular edge or nonzero padding.
        val recipe=baselineRecipe("fixture").copy(lights=listOf(Light(.5f,.5f,.2f,.2f,0xff80bbff,.5f)),
            directionalDetails=listOf(DirectionalDetail(.5f,.5f,25f,.6f,.08f,0xff00ffff,.5f,1f)))
        val masks=AtmospherePrecomputer.render(recipe,128,228)
        for(p in listOf(masks.diffuse,masks.directional)) {
            assertTrue((0 until p.width).all { x -> (p.argb[x] ushr 24)==0 && (p.argb[(p.height-1)*p.width+x] ushr 24)==0 })
            assertTrue((0 until p.height).all { y -> (p.argb[y*p.width] ushr 24)==0 && (p.argb[y*p.width+p.width-1] ushr 24)==0 })
            var jump=0
            for(y in 1 until p.height)for(x in 1 until p.width) {
                val alpha=p.argb[y*p.width+x] ushr 24
                jump=maxOf(jump,kotlin.math.abs(alpha-(p.argb[y*p.width+x-1] ushr 24)),kotlin.math.abs(alpha-(p.argb[(y-1)*p.width+x] ushr 24)))
            }
            assertTrue(jump<60,"Unexpected abrupt mask edge: $jump")
        }
    }
    @Test fun corruptPersistedRecipeAndTextureRecomputeInsteadOfPoisoningCache() {
        val directory=File("build/test-cache/repair-${System.nanoTime()}")
        val album=validationCatalog[0]
        val first=DesktopAssets(directory).load(album)
        val json=directory.walkTopDown().first { it.name=="analysis.json" }
        json.writeText("{broken-json}")
        val repaired=DesktopAssets(directory)
        assertEquals(first.recipe,repaired.load(album).recipe)
        assertEquals(1,repaired.analysisCount)
        directory.walkTopDown().first { it.name=="directional.png" }.writeText("broken-png")
        val repairedTexture=DesktopAssets(directory)
        assertNotNull(repairedTexture.load(album).texture)
        assertEquals(1,repairedTexture.analysisCount)
    }
}

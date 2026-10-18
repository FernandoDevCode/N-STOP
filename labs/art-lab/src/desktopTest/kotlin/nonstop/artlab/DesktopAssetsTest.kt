package nonstop.artlab

import nonstop.artlab.catalog.*
import kotlin.test.*
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import nonstop.artlab.direction.*
import nonstop.artlab.features.*
import kotlinx.serialization.encodeToString

class DesktopAssetsTest {
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
                assertTrue(contrast(surface.ink,mix(fill,surface.specular,surface.specularIntensity.toDouble()))>=4.5)
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
    @Test fun precomputePreservesCornersAndUsesBlurRadius() {
        val image=BufferedImage(96,96,BufferedImage.TYPE_INT_RGB)
        for(y in 0..95) for(x in 0..95) image.setRGB(x,y,if(x<25)0xffff0000.toInt() else 0xff0000ff.toInt())
        val assets=DesktopAssets()
        val a=assets.blurFullImage(image,4f); val b=assets.blurFullImage(image,30f)
        assertEquals(96,a.width); assertEquals(96,a.height)
        assertTrue(((a.getRGB(0,0) shr 16)and 255)>200)
        assertNotEquals(a.getRGB(25,50),b.getRGB(25,50))
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
        directory.walkTopDown().first { it.name=="atmosphere.png" }.writeText("broken-png")
        val repairedTexture=DesktopAssets(directory)
        assertNotNull(repairedTexture.load(album).texture)
        assertEquals(1,repairedTexture.analysisCount)
    }
}

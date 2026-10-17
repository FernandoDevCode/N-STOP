package nonstop.artlab

import nonstop.artlab.catalog.*
import kotlin.test.*
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class DesktopAssetsTest {
    @Test fun corruptImageFallsBackAndValidPngJpegAreCachedByContent() {
        val assets=DesktopAssets()
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
}

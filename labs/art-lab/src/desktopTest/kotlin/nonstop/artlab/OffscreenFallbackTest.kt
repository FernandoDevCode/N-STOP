@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package nonstop.artlab

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontFamily
import nonstop.artlab.catalog.validationCatalog
import nonstop.artlab.direction.baselineRecipe
import nonstop.artlab.features.luminance
import nonstop.artlab.renderer.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.*

class OffscreenFallbackTest {
    @Test fun failedDecodeStillRendersDepthWithoutSourceTexture() {
        System.setProperty("java.awt.headless","true")
        val scene=ImageComposeScene(432,768) {
            V8Renderer(validationCatalog.first(),null,null,baselineRecipe("invalid-decode"),PlayerState(),{},{},{},{},{},
                FontFamily.SansSerif,DebugView.LIGHTS,modifier=Modifier.fillMaxSize())
        }
        try {
            repeat(2) { scene.render().close() }
            scene.render().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { png ->
                val pixels=ImageIO.read(ByteArrayInputStream(png.bytes))
                val lit=pixels.getRGB(121,92).toLong() and 0xffffffffL
                val dark=pixels.getRGB(431,0).toLong() and 0xffffffffL
                assertTrue(luminance(lit)>luminance(dark)+.005,"Missing texture must not flatten fallback to canvas")
            } }
        } finally { scene.close() }
    }
}

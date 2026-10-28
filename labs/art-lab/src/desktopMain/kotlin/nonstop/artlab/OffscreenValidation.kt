@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package nonstop.artlab

import androidx.compose.ui.ImageComposeScene
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import nonstop.artlab.catalog.*
import nonstop.artlab.direction.*
import nonstop.artlab.renderer.*
import nonstop.artlab.resources.*
import org.jetbrains.compose.resources.Font
import org.jetbrains.skia.EncodedImageFormat
import kotlinx.serialization.encodeToString
import java.io.File
import javax.imageio.ImageIO

/** Real Compose/Skia raster scene; no Window, Robot, screen capture, focus or OS input. */
fun runOffscreenValidation(args:Array<String>) {
    val cycle=args.firstOrNull { it.startsWith("--cycle=") }?.substringAfter('=') ?: "1"
    require(cycle.matches(Regex("[1-3]"))) { "Use --cycle=1, 2 or 3 (maximum three calibration cycles)" }
    val output=File("../../docs/player-v8/validation/phase-1b2/cycle-$cycle").canonicalFile.apply { mkdirs() }
    val assets=DesktopAssets()
    val performance=mutableListOf<String>()
    // This validation list controls acquisition/evidence only. Neither director nor compositor receives it.
    val benchmarks=listOf(Triple(validationCatalog[0],941,1672),Triple(validationCatalog[1],864,1536))
    for((album,width,height) in benchmarks) {
        val asset=assets.load(album)
        check(asset.cover!=null && asset.texture!=null) { asset.message }
        val id=album.resource!!.substringAfterLast('/').substringBeforeLast('.')
        File(output,"$id-recipe.json").writeText(recipeJson.encodeToString(asset.recipe))
        File(output,"$id-features.json").writeText(recipeJson.encodeToString(asset.features))
        performance+="$id analysis ${asset.cacheOrigin}: ${asset.timings}"
        for(mode in ComparisonMode.entries) {
            val cover=comparisonCover(album,asset,mode)
            var state by mutableStateOf(PlayerState())
            var debug by mutableStateOf(DebugView.FINAL)
            var fontScale by mutableStateOf(1f)
            val scene=ImageComposeScene(width,height,Density(1f)) {
                val font=FontFamily(Font(Res.font.inter_regular,FontWeight.Normal),Font(Res.font.inter_semibold,FontWeight.SemiBold))
                MaterialTheme(colorScheme=darkColorScheme()) {
                  CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) {
                    LabV8Renderer(album,cover,asset.texture,asset.recipe,state,{state=it},{},{},{},{},font,debug,
                        fontScale=fontScale,modifier=Modifier.fillMaxSize())
                  }
                }
            }
            try {
                // Resource fonts/recomposition settle in the same scene, without a desktop event loop.
                repeat(5) { scene.render(it*16_666_667L).close() }
                fun capture(name:String) {
                    scene.render(System.nanoTime()).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { File(output,"$name.png").writeBytes(it.bytes) }
                    }
                }
                val name="$id-${mode.name.lowercase()}-${width}x$height"
                capture(name)
                val master=ImageIO.read(File("../../docs/player-v8/references/MASTER_$id.png"))
                val actual=ImageIO.read(File(output,"$name.png"))
                check(actual.width==width && actual.height==height)
                val side=java.awt.image.BufferedImage(width*2,height,java.awt.image.BufferedImage.TYPE_INT_RGB)
                side.createGraphics().apply { drawImage(master,0,0,null); drawImage(actual,width,0,null); dispose() }
                ImageIO.write(side,"png",File(output,"compare-$name.png"))
                val times=DoubleArray(12) {
                    val start=System.nanoTime(); scene.render(start).close(); (System.nanoTime()-start)/1e6
                }.sorted()
                performance+="$id $mode offscreen CPU raster warm frames n=12: median=${times[6]} ms max=${times.last()} ms"
                if(mode==ComparisonMode.PRODUCAO) {
                    fun tap(x:Float,y:Float) {
                        scene.sendPointerEvent(PointerEventType.Press,Offset(x,y))
                        scene.sendPointerEvent(PointerEventType.Release,Offset(x,y))
                        scene.render(System.nanoTime()).close()
                    }
                    // Input is delivered only to this offscreen scene, never to the OS desktop.
                    // Find the play control through Compose semantics, avoiding title-dependent coordinates.
                    fun nodes(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::nodes)
                    val pause=scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                        .first { it.config.getOrNull(SemanticsActions.OnClick)?.label=="Pausar (simulação)" }
                    check(pause.config[SemanticsActions.OnClick].action!!.invoke())
                    scene.render(System.nanoTime()).close()
                    check(!state.playing) { "Pause semantic action did not update state" }
                    for(p in listOf(0f,.25f,.5f,1f)) { state=state.copy(progress=p); capture("$id-progress-${(p*100).toInt()}") }
                    val samples=listOf(25,50,100).map { p ->
                        val png=ImageIO.read(File(output,"$id-progress-$p.png"))
                        png.getRGB((width*.20).toInt(),(height*MasterGeometry.progressCenter).toInt())
                    }
                    check(samples.distinct().size==1) { "Gradient was rescaled during seek" }
                    capture("$id-paused")
                    tap(width*.60f,height*MasterGeometry.progressCenter)
                    check(!state.playing && kotlin.math.abs(state.progress-.60f)<.03f) { "Seek lost paused state or did not update" }
                    capture("$id-paused-seek")
                    performance+="$id internal Compose seek: paused state preserved, progress=${state.progress}"
                    fontScale=1.5f; capture("$id-font-150"); fontScale=1f
                    state=state.copy(lyrics=true); capture("$id-lyrics")
                    state=state.copy(lyrics=false); debug=DebugView.LIGHTS; capture("$id-layers")
                    debug=DebugView.BUTTON; capture("$id-button")
                    // Pixel regression: large empty corner strips around the circular control must be canvas.
                    val button=ImageIO.read(File(output,"$id-button.png"))
                    val centerY=height/2; val radius=(width*asset.recipe.controlSurface.haloRadius).toInt()
                    for(x in listOf(width/2-radius,width/2+radius))for(y in listOf(centerY-radius,centerY+radius)) {
                        check((button.getRGB(x,y) and 0xffffff)==(asset.recipe.canvasColor.toInt() and 0xffffff)) { "Rectangular halo corner" }
                    }
                }
            } finally { scene.close() }
        }
        val repeat=assets.load(album)
        check(repeat.recipe==asset.recipe && repeat.cacheOrigin==CacheOrigin.MEMORY)
        performance+="$id memory lookup ${repeat.timings.totalMs} ms; mode-independent recipe verified"
    }
    File(output,"performance.txt").writeText(performance.joinToString("\n"))
    File(output,"complete.txt").writeText("${ClassicalDirector.VERSION}\nReal ImageComposeScene raster; headless=true; no desktop windows or global input.\n")
    println("ART_LAB_OFFSCREEN_COMPLETE ${output.absolutePath}\n${performance.joinToString("\n")}")
}

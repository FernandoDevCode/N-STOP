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
import androidx.compose.ui.semantics.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import nonstop.artlab.catalog.*
import nonstop.artlab.direction.*
import nonstop.artlab.renderer.*
import nonstop.artlab.resources.*
import nonstop.artlab.validation.SyntheticImages
import org.jetbrains.compose.resources.Font
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.io.File
import javax.imageio.ImageIO

@Serializable data class ProceduralMeasurement(val id:String,val family:ArtFamily,val material:ControlMaterial,
    val seed:Long,val cache:CacheOrigin,val timings:MeasuredTimings,val retainedRgbaBytes:Long,
    val recipeSha256:String,val textureSha256:String,val initialSceneMs:Double=0.0,
    val warmRasterMedianMs:Double=0.0,val warmRasterP95Ms:Double=0.0,val warmRasterMaxMs:Double=0.0)
@Serializable data class MeasuredTimings(val decodeMs:Double,val featuresMs:Double,val directionMs:Double,
    val generationMs:Double,val texturePreparationMs:Double,val totalMs:Double)
private fun measurement(id:String,a:CoverAsset):ProceduralMeasurement {
    val pixels=IntArray(192*342); a.texture!!.composite.readPixels(pixels)
    val bytes=ByteArray(pixels.size*4)
    pixels.forEachIndexed { i,c -> repeat(4) { k -> bytes[i*4+k]=(c ushr (k*8)).toByte() } }
    val t=a.timings
    return ProceduralMeasurement(id,a.recipe.family,a.recipe.controlSurface.material,a.recipe.procedural.seed,a.cacheOrigin,
        MeasuredTimings(t.decodeMs,t.featuresMs,t.directionMs,t.generationMs,t.textureMs,t.totalMs),a.retainedRasterBytes,
        sha256(recipeJson.encodeToString(a.recipe).encodeToByteArray()),sha256(bytes))
}

/** Runs the packaged Compose renderer with no native Window or global input. */
fun runProceduralValidation(args:Array<String>) {
    val cycle=args.firstOrNull { it.startsWith("--cycle=") }?.substringAfter('=') ?: "1"
    require(cycle.matches(Regex("[1-3]")))
    val output=File("../../docs/player-v8/validation/procedural/cycle-$cycle").canonicalFile.apply { mkdirs() }
    val cacheDir=File("build/validation-cache/procedural-4-cycle-$cycle")
    val assets=DesktopAssets(cacheDir)
    val fixtures=File("build/procedural-fixtures").apply { mkdirs() }
    val inputs=validationCatalog.map { it.resource!!.substringAfterLast('/').substringBeforeLast('.') to it }+
        SyntheticImages.names.map { name ->
            val p=SyntheticImages.image(name)
            val file=File(fixtures,"$name.png")
            ImageIO.write(BufferedImage(p.width,p.height,BufferedImage.TYPE_INT_RGB).apply {
                setRGB(0,0,p.width,p.height,p.argb,0,p.width) },"png",file)
            "synthetic-$name" to Album("Fixture sintética",name,resource=file.absolutePath,status=ArtworkStatus.LOCAL,
                source=if(name.contains("photo"))"Proxy sintético de luminância/textura fotográfica; não é fotografia real" else "SyntheticImages v1")
        }
    if("--cache-probe" in args) {
        val previous=recipeJson.decodeFromString<List<ProceduralMeasurement>>(File(output,"metrics.json").readText())
        val results=inputs.map { (id,album) ->
            val a=assets.load(album); check(a.cover!=null) { "$id: ${a.message}" }
            check(a.cacheOrigin==CacheOrigin.DISK && a.timings.generationMs==0.0 && a.timings.featuresMs==0.0) { "$id did not reuse persistent cache" }
            val m=measurement(id,a); val before=previous.first { it.id==id }
            check(m.recipeSha256==before.recipeSha256 && m.textureSha256==before.textureSha256) { "$id changed across JVM restart" }
            m
        }
        check(assets.analysisCount==0)
        File(output,"cache-restart.json").writeText(recipeJson.encodeToString(results))
        println("ART_LAB_CACHE_RESTART_COMPLETE ${results.size} DISK hits, 0 analyses, 0 generations, same recipes and pixels")
        return
    }
    val results=mutableListOf<ProceduralMeasurement>()
    for((index,pair) in inputs.withIndex()) {
        val (id,album)=pair
        val a=assets.load(album); check(a.cover!=null && a.texture!=null) { "$id: ${a.message}" }
        val width=if(index==0)941 else 864; val height=if(index==0)1672 else 1536
        File(output,"$id-recipe.json").writeText(recipeJson.encodeToString(a.recipe))
        File(output,"$id-features.json").writeText(recipeJson.encodeToString(a.features))
        var state by mutableStateOf(PlayerState())
        var fontScale by mutableStateOf(1f)
        var mode by mutableStateOf(ComparisonMode.PRODUCAO)
        var debug by mutableStateOf(DebugView.FINAL)
        val initialStart=System.nanoTime()
        val scene=ImageComposeScene(width,height,Density(1f)) {
            val font=FontFamily(Font(Res.font.inter_regular,FontWeight.Normal),Font(Res.font.inter_semibold,FontWeight.SemiBold))
            val displayed=remember(mode) { comparisonCover(album,a,mode) }
            MaterialTheme(colorScheme=darkColorScheme()) {
                CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) {
                    V8Renderer(album,displayed,a.texture,a.recipe,state,{state=it},{},{},{},{},font,debug,
                        fontScale=fontScale,modifier=Modifier.fillMaxSize())
                }
            }
        }
        try {
            repeat(5) { scene.render().close() }
            val initialMs=(System.nanoTime()-initialStart)/1e6
            fun capture(name:String) { scene.render().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use {
                File(output,"$name.png").writeBytes(it.bytes) } } }
            fun nodes(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::nodes)
            fun action(label:String) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                .first { it.config.getOrNull(SemanticsActions.OnClick)?.label==label }
                .config[SemanticsActions.OnClick].action!!.invoke()
            capture(id)
            val times=DoubleArray(30) { val start=System.nanoTime(); scene.render().close(); (System.nanoTime()-start)/1e6 }.sorted()
            results+=measurement(id,a).copy(initialSceneMs=initialMs,warmRasterMedianMs=times[15],warmRasterP95Ms=times[28],warmRasterMaxMs=times.last())
            check(action("Pausar (simulação)")); scene.render().close(); check(!state.playing)
            val samples=mutableListOf<Int>()
            for(progress in listOf(0f,.25f,.5f,1f)) {
                state=state.copy(progress=progress)
                capture("$id-seek-${(progress*100).toInt()}")
                if(progress>0) samples+=ImageIO.read(File(output,"$id-seek-${(progress*100).toInt()}.png"))
                    .getRGB((width*.20).toInt(),(height*MasterGeometry.progressCenter).toInt())
            }
            check(samples.distinct().size==1) { "$id: progress gradient shifted" }
            state=state.copy(progress=.43f)
            // Settle the new state before reading semantic callbacks from the composition.
            // Otherwise the favorite action still captures the last seek state (100%).
            scene.render().close()
            val heart=scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Remover favorito (simulação)")==true }
            check(heart.config[SemanticsActions.OnClick].action!!.invoke()); scene.render().close(); check(!state.liked)
            check(!state.playing && state.progress==.43f) { "$id: favorite changed playback or seek state" }
            capture("$id-heart-outline")
            if(index<12) {
                debug=DebugView.BUTTON; capture("$id-button")
                val button=ImageIO.read(File(output,"$id-button.png"))
                val radius=(width*a.recipe.controlSurface.haloRadius).toInt()
                val canvas=a.recipe.canvasColor.toInt() and 0xffffff
                for(x in listOf(width/2-radius,width/2+radius))for(y in listOf(height/2-radius,height/2+radius)) {
                    check((button.getRGB(x,y) and 0xffffff)==canvas) { "$id: rectangular halo edge" }
                }
                check((button.getRGB(width/2+(radius*.78).toInt(),height/2) and 0xffffff)!=canvas) { "$id: halo must be visible" }
                debug=DebugView.FINAL
            }
            if(index<2) {
                state=PlayerState(); mode=ComparisonMode.FIDELIDADE; capture("$id-fidelidade")
                val master=ImageIO.read(File("../../docs/player-v8/references/MASTER_$id.png"))
                for(suffix in listOf("","-fidelidade")) {
                    val actual=ImageIO.read(File(output,"$id$suffix.png"))
                    val side=BufferedImage(width*2,height,BufferedImage.TYPE_INT_RGB)
                    side.createGraphics().apply { drawImage(master,0,0,null); drawImage(actual,width,0,null); dispose() }
                    ImageIO.write(side,"png",File(output,"compare-$id$suffix.png"))
                }
                mode=ComparisonMode.PRODUCAO; fontScale=1.5f; capture("$id-font-150"); fontScale=1f
                state=state.copy(lyrics=true); capture("$id-lyrics")
            }
            val memory=assets.load(album); check(memory.cacheOrigin==CacheOrigin.MEMORY && memory.recipe==a.recipe)
            println("PROCEDURAL $id ${a.recipe.family} ${a.recipe.controlSurface.material} ${a.cacheOrigin} generation=${a.timings.generationMs}ms rasterMedian=${times[15]}ms RGBA=${a.retainedRasterBytes}")
        } finally { scene.close() }
    }
    File(output,"metrics.json").writeText(recipeJson.encodeToString(results))
    contactSheet(output,inputs.take(12).map { it.first },"catalog-contact-sheet.png",4)
    contactSheet(output,inputs.drop(12).map { it.first },"synthetic-contact-sheet.png",4)
    verifyRapidSwitchAndTallViewport(output,assets)
    File(output,"complete.txt").writeText("${ClassicalDirector.VERSION}\n20 images, 30 warm CPU raster frames each. Real ImageComposeScene, no native windows/global input.\nNot GPU, Android, peak RAM or sustained FPS.\n")
    println("ART_LAB_PROCEDURAL_COMPLETE ${output.absolutePath}")
}

private fun contactSheet(output:File,ids:List<String>,name:String,columns:Int) {
    val sheet=BufferedImage(columns*270,((ids.size+columns-1)/columns)*505,BufferedImage.TYPE_INT_RGB)
    sheet.createGraphics().apply {
        color=java.awt.Color(12,16,24); fillRect(0,0,sheet.width,sheet.height)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        ids.forEachIndexed { i,id ->
            val image=ImageIO.read(File(output,"$id.png")); val x=i%columns*270; val y=i/columns*505
            val scale=minOf(260.0/image.width,470.0/image.height)
            drawImage(image,x+5,y+5,(image.width*scale).toInt(),(image.height*scale).toInt(),null)
            color=java.awt.Color.WHITE; drawString(id,x+8,y+492)
        }; dispose()
    }
    ImageIO.write(sheet,"png",File(output,name))
}

private fun verifyRapidSwitchAndTallViewport(output:File,assets:DesktopAssets) {
    var index by mutableIntStateOf(0)
    var asset by mutableStateOf(assets.load(validationCatalog[0]))
    var state by mutableStateOf(PlayerState())
    fun next() { index=(index+1)%12; asset=assets.load(validationCatalog[index]) }
    val scene=ImageComposeScene(1080,2340) {
        V8Renderer(validationCatalog[index],asset.cover,asset.texture,asset.recipe,state,{state=it},{},::next,{},{},
            FontFamily.SansSerif,modifier=Modifier.fillMaxSize())
    }
    try {
        repeat(5) { scene.render().close() }
        // Reuse a single live composition across all twelve covers and fallback, no relaunch.
        repeat(12) { next(); scene.render().close(); check(asset.cover!=null && asset.recipe.sourceImageHash==asset.hash) }
        scene.render().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use {
            File(output,"tall-1080x2340.png").writeBytes(it.bytes) } }
        asset=CoverAsset(); scene.render().close()
        scene.render().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use {
            File(output,"immediate-fallback-1080x2340.png").writeBytes(it.bytes) } }
    } finally { scene.close() }
}

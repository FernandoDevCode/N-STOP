@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package nonstop.artlab

import androidx.compose.ui.ImageComposeScene
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import nonstop.artlab.catalog.*
import nonstop.artlab.direction.*
import nonstop.artlab.renderer.*
import nonstop.artlab.resources.*
import org.jetbrains.compose.resources.Font
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.*
import javax.imageio.metadata.IIOMetadataNode

@Serializable data class FrameCost(val medianMs:Double,val p95Ms:Double,val maxMs:Double)
@Serializable data class MicroMeasurement(val id:String,val family:ArtFamily,val audioGain:Float,val trailGain:Float,
    val baseIdle:FrameCost,val microIdle:FrameCost,val baseSeek:FrameCost,val microSeekAudio:FrameCost,
    val maxParticles:Int,val compositionsDuringAudioFrames:Int,val inputVerified:Boolean)

/** Real renderer, explicit deterministic clock, local scene input only. No Window/Robot. */
fun runMicroValidation() {
    val output=File("../../docs/player-v8/validation/micro-v1").canonicalFile.apply { mkdirs() }
    val assets=DesktopAssets(File("build/validation-cache/micro-v1"))
    val audio=DesktopAudioEnergy.fixture()
    val measurements=mutableListOf<MicroMeasurement>()
    for(album in validationCatalog.take(6)) {
        val id=album.resource!!.substringAfterLast('/').substringBeforeLast('.')
        val asset=assets.load(album); check(asset.cover!=null && asset.texture!=null) { asset.message }
        val profile=MicroProfile.from(asset.recipe)
        val micro=PlayerMicroState()
        var state by mutableStateOf(PlayerState())
        var options by mutableStateOf(MicroOptions(automaticClock=false))
        var compositions=0
        val scene=ImageComposeScene(864,1536,Density(1f)) {
            SideEffect { compositions++ }
            val font=FontFamily(Font(Res.font.inter_regular,FontWeight.Normal),Font(Res.font.inter_semibold,FontWeight.SemiBold))
            MaterialTheme(colorScheme=darkColorScheme()) {
                LabV8Renderer(album,asset.cover,asset.texture,asset.recipe,state,{state=it},{},{},{},{},font,
                    modifier=Modifier.fillMaxSize(),microOptions=options,microState=micro)
            }
        }
        val readback=ImageBitmap(864,1536)
        val readbackPixels=IntArray(864*1536)
        val readbackImage=BufferedImage(864,1536,BufferedImage.TYPE_INT_RGB)
        try {
            repeat(5) { scene.render().close() }
            fun png():ByteArray=scene.render().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes } }
            fun capture(name:String) { File(output,"$id-$name.png").writeBytes(png()) }
            fun nodes(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::nodes)
            val pause=scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsActions.OnClick)?.label=="Pausar (simulação)" }
            check(pause.config[SemanticsActions.OnClick].action!!.invoke()); scene.render().close(); check(!state.playing)
            val y=1536*MasterGeometry.progressCenter
            val left=864*MasterGeometry.inset; val width=864*MasterGeometry.coverWidth
            scene.sendPointerEvent(PointerEventType.Press,Offset(left+width*.25f,y))
            scene.sendPointerEvent(PointerEventType.Move,Offset(left+width*.45f,y))
            scene.render().close()
            scene.sendPointerEvent(PointerEventType.Move,Offset(left+width*.60f,y))
            scene.render().close()
            check(micro.dragging && !state.playing && state.progress in .56f.. .64f) { "$id: real scene drag failed: dragging=${micro.dragging}, progress=${state.progress}" }
            micro.advance(36f,0f,profile,options); capture("input-drag")
            scene.sendPointerEvent(PointerEventType.Release,Offset(left+width*.60f,y))
            scene.render().close(); check(!micro.dragging && !state.playing)
            repeat(20) { micro.advance(16f,0f,profile,options) }; check(micro.trail.count==0)
            // Focus is exercised through the same semantics used by accessibility/keyboard focus.
            val seek=scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Progresso demonstrativo")==true }
            check(seek.config[SemanticsActions.RequestFocus].action!!.invoke())
            repeat(3) { scene.render().close() }
            repeat(12) { micro.advance(16f,0f,profile,options) }
            check(seek.config[SemanticsActions.SetProgress].action!!.invoke(.25f)); scene.render().close(); check(!state.playing)
            capture("focus")
            state=PlayerState(); micro.focus(false); micro.hover(false); micro.press(false)
            repeat(20) { micro.advance(16f,0f,profile) }; capture("idle")
            // Filmstrip + GIF are made exclusively from real Compose raster frames.
            val frames=listOf("idle","hover","drag","release","audio-high","audio-release")
            val writer=GifFrames(File(output,"$id-motion.gif"),5)
            var maxParticles=0
            try {
                for(frame in 0 until 72) {
                    val ms=frame*50
                    when(ms) {
                        200 -> micro.hover(true)
                        450 -> micro.press(true)
                        600 -> micro.beginDrag(.25f,progressColorAt(asset.recipe,.25f))
                        1300 -> { micro.endDrag(); micro.hover(false) }
                    }
                    if(ms in 600 until 1300) {
                        val p=.25f+.50f*(ms-600)/700f; state=state.copy(progress=p)
                        micro.dragTo(p,progressColorAt(asset.recipe,p))
                    }
                    val raw=if(ms in 1700..2300)audio.envelope.energyAt(3500.0) else 0f
                    // Three small updates approximate 60 Hz between 50 ms evidence samples.
                    repeat(3) { micro.advance(50f/3,raw,profile,options) }
                    maxParticles=maxOf(maxParticles,micro.trail.count)
                    scene.render().use { check(it.readPixels(readback.asSkiaBitmap())) }
                    readback.readPixels(readbackPixels)
                    readbackImage.setRGB(0,0,864,1536,readbackPixels,0,864)
                    writer.add(readbackImage)
                    val name=when(ms) { 0 -> "idle"; 400 -> "hover"; 1150 -> "drag"; 1650 -> "release"; 2250 -> "audio-high"; 3000 -> "audio-release"; else -> null }
                    if(name!=null)ImageIO.write(readbackImage,"png",File(output,"$id-$name.png"))
                }
            } finally { writer.close() }
            check(maxParticles<=MicroParameters.MAX_PARTICLES)
            micro.endDrag(); micro.hover(false); micro.focus(false); micro.press(false)
            repeat(200) { micro.advance(18f,0f,profile) }
            state=state.copy(progress=.5f); repeat(3) { scene.render().close() }
            val before=compositions
            repeat(60) { micro.advance(16.6667f,audio.envelope.energyAt(3500.0),profile); scene.render().close() }
            val recompositions=compositions-before
            check(recompositions==0) { "$id: audio-only frames recomposed outer player" }
            options=MicroOptions(reducedMotion=true,automaticClock=false); micro.advance(16f,1f,profile,options)
            check(micro.energy==0f && micro.trail.count==0); capture("reduced-motion")
            options=MicroOptions(automaticClock=false)
            val modes=listOf("baseIdle","microIdle","baseSeek","microSeekAudio")
            val costs=modes.associateWith { mutableListOf<Double>() }
            repeat(3) { round ->
                for(mode in if(round%2==0)modes else modes.reversed()) {
                    val enabled=mode.startsWith("micro"); val seekMode=mode.contains("Seek")
                    options=MicroOptions(enabled=enabled,automaticClock=false)
                    micro.endDrag(); micro.hover(false); micro.press(false); micro.focus(false)
                    repeat(200) { micro.advance(18f,0f,profile,options) }
                    if(seekMode && enabled)micro.beginDrag(.4f,progressColorAt(asset.recipe,.4f))
                    repeat(5) { scene.render().close() }
                    repeat(20) { n ->
                        if(seekMode) {
                            val p=.35f+n*.015f; state=state.copy(progress=p)
                            if(enabled)micro.dragTo(p,progressColorAt(asset.recipe,p))
                        }
                        val start=System.nanoTime()
                        if(enabled)micro.advance(16.6667f,if(seekMode)audio.envelope.energyAt(3500.0) else 0f,profile,options)
                        scene.render().close(); costs.getValue(mode)+=(System.nanoTime()-start)/1e6
                    }
                }
            }
            fun stats(name:String):FrameCost {
                val values=costs.getValue(name).sorted(); return FrameCost(values[values.size/2],values[(values.size*.95).toInt()],values.last())
            }
            measurements+=MicroMeasurement(id,asset.recipe.family,profile.audioGain,profile.trailGain,
                stats("baseIdle"),stats("microIdle"),stats("baseSeek"),stats("microSeekAudio"),maxParticles,recompositions,true)
            filmstrip(output,id,frames)
            println("MICRO $id: idle ${stats("baseIdle").medianMs} -> ${stats("microIdle").medianMs}ms; seek ${stats("baseSeek").medianMs} -> ${stats("microSeekAudio").medianMs}ms; particles=$maxParticles; outer recompositions=$recompositions")
        } finally { readback.asSkiaBitmap().close(); scene.close() }
    }
    File(output,"performance.json").writeText(recipeJson.encodeToString(measurements))
    File(output,"complete.txt").writeText("PLAYER_MICRO_INTERACTIONS_V1\n6 covers, actual Compose raster + local scene pointer/focus semantics. PCM fixture RMS, no album audio or audible playback.\n3 alternating rounds x 20 frames/mode/cover. CPU raster, not GPU/Redmi/FPS.\n")
    println("ART_LAB_MICRO_COMPLETE ${output.absolutePath}")
}

private fun filmstrip(output:File,id:String,names:List<String>) {
    val image=BufferedImage(names.size*216,410,BufferedImage.TYPE_INT_RGB)
    image.createGraphics().apply {
        color=java.awt.Color(8,12,18); fillRect(0,0,image.width,image.height)
        names.forEachIndexed { i,name -> val source=ImageIO.read(File(output,"$id-$name.png"))
            drawImage(source,i*216,0,216,384,null); color=java.awt.Color.WHITE; drawString(name,i*216+5,402) }
        dispose()
    }; ImageIO.write(image,"png",File(output,"$id-filmstrip.png"))
}

private class GifFrames(file:File,private val delay:Int):AutoCloseable {
    private val output=ImageIO.createImageOutputStream(file)
    private val writer=ImageIO.getImageWritersByFormatName("gif").next().apply { this.output=this@GifFrames.output; prepareWriteSequence(null) }
    fun add(source:BufferedImage) {
        val image=BufferedImage(432,304,BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            color=java.awt.Color(5,8,12); fillRect(0,0,432,304)
            val seekY=(source.height*MasterGeometry.progressCenter).toInt()
            drawImage(source,0,0,432,48,0,seekY-36,864,seekY+36,null)
            val controlY=(source.height*.82).toInt()
            drawImage(source,96,60,336,300,252,controlY-180,612,controlY+180,null)
            dispose() }
        val metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),writer.defaultWriteParam)
        val root=metadata.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
        val control=root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
        control.setAttribute("delayTime",delay.toString()); control.setAttribute("disposalMethod","none")
        metadata.setFromTree("javax_imageio_gif_image_1.0",root)
        writer.writeToSequence(IIOImage(image,null,metadata),writer.defaultWriteParam)
    }
    override fun close() { writer.endWriteSequence(); writer.dispose(); output.close() }
}

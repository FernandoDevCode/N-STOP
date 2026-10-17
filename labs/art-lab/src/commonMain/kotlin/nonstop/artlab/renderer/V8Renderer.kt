package nonstop.artlab.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import nonstop.artlab.catalog.Album
import nonstop.artlab.direction.ArtRecipe
import nonstop.artlab.direction.safeRecipe
import kotlin.math.*

enum class DebugView { FINAL, ART_BASE, LIGHTS, SCRIM, BUTTON }
data class PlayerState(val progress: Float = .43f, val playing: Boolean = true,
    val liked: Boolean = true, val shuffle: Boolean = false, val repeat: Boolean = false, val lyrics: Boolean = false)

// Shared geometry: measured average of the two original references, not inherited from v7.
object MasterGeometry {
    const val inset = .083f
    const val coverWidth = .834f
    const val coverTop = .102f
    const val progressGap = .057f // fraction of width from bottom of square cover
    const val transportCenter = .805f
    const val lyricsTop = .901f
}

@Composable
fun V8Renderer(album: Album, cover: ImageBitmap?, atmosphere: ImageBitmap?, recipe: ArtRecipe,
    state: PlayerState, onState: (PlayerState) -> Unit, previous: () -> Unit, next: () -> Unit,
    onClose: () -> Unit, onMenu: () -> Unit, font: FontFamily, debug: DebugView = DebugView.FINAL,
    fontScale: Float = 1f, modifier: Modifier = Modifier) {
    val r = remember(recipe) { safeRecipe(recipe) }
    BoxWithConstraints(modifier.background(Color(r.canvasColor))) {
        val w = maxWidth.value
        val h = maxHeight.value
        val u = w / 432f
        val inset = w * MasterGeometry.inset
        val contentWidth = w * MasterGeometry.coverWidth
        // Make room for accessible typography before allowing lower controls to leave the viewport.
        val artSize = contentWidth * (1f-.36f*(fontScale-1f).coerceIn(0f,.5f))
        val artY = h * MasterGeometry.coverTop
        val seekY = artY + artSize + w * MasterGeometry.progressGap
        val titleY = seekY + 43f*u
        val title=album.track ?: "Faixa não fornecida"
        val titleSize=if(title.length>23) 21f else 27f
        val measurer=rememberTextMeasurer()
        val density=LocalDensity.current
        val titleHeight=with(density) { measurer.measure(title,
            TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=(titleSize*u).sp,lineHeight=(titleSize*1.16f*u).sp),
            maxLines=2,constraints=Constraints(maxWidth=(contentWidth-33*u).dp.roundToPx())).size.height.toDp().value }
        val metadata="${album.artist}  •  ${album.album}"
        val metadataY=titleY+titleHeight+4*u
        val metadataHeight=with(density) { measurer.measure(metadata,
            TextStyle(fontFamily=font,fontSize=(16*u).sp,lineHeight=(20*u).sp),maxLines=2,
            constraints=Constraints(maxWidth=(contentWidth-20*u).dp.roundToPx())).size.height.toDp().value }
        val controlsY = max(h*MasterGeometry.transportCenter, metadataY+metadataHeight+48*u)
        val lyricsY = max(h*MasterGeometry.lyricsTop, controlsY+70*u)
        val text = Color(0xfffafbff)
        val muted = Color(0xff9296a6)
        val showArt = debug in listOf(DebugView.FINAL,DebugView.ART_BASE)
        if (showArt && atmosphere != null) Image(atmosphere, null, Modifier.fillMaxSize(),
            contentScale=ContentScale.Crop, alpha=r.artTextureOpacity)
        if (debug in listOf(DebugView.FINAL,DebugView.LIGHTS)) Canvas(Modifier.fillMaxSize()) {
            r.lights.forEach { l ->
                val center = Offset(l.x*size.width,l.y*size.height)
                val rx = l.radiusX*size.width
                val ry = l.radiusY*size.height
                scale(1f,ry/rx,center) {
                    drawCircle(Brush.radialGradient(listOf(Color(l.color).copy(alpha=l.intensity),Color.Transparent),center,rx),rx,center)
                }
            }
        }
        if (debug in listOf(DebugView.FINAL,DebugView.SCRIM)) Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Black.copy(alpha=r.scrimTop),.5f to Color.Transparent,1f to Color.Black.copy(alpha=r.scrimBottom))))
        if (debug == DebugView.FINAL) {
            RoundControl("Recolher player", "down", Modifier.offset((inset-24*u).dp,(h*.027f-4*u).dp).size((48*u).dp),onClose,outlined=true)
            RoundControl("Diagnóstico", "menu", Modifier.offset((w-inset-24*u).dp,(h*.027f-4*u).dp).size((48*u).dp),onMenu,outlined=true)
        }
        if (showArt && !state.lyrics) {
            Box(Modifier.offset(((w-artSize)/2).dp,artY.dp).size(artSize.dp).clip(RoundedCornerShape((15*u).dp))) {
                if (cover != null) Image(cover,"${album.artist} — ${album.album}; ${album.status}",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Color(0xff131b29)),contentAlignment=Alignment.Center) {
                    Text("CAPA INDISPONÍVEL\n${album.album}", color=muted, fontFamily=font,
                        fontSize=(17*u).sp,lineHeight=(24*u).sp,modifier=Modifier.padding((24*u).dp))
                }
            }
        }
        if (debug == DebugView.FINAL && !state.lyrics) {
            ProgressBar(state.progress,r,Modifier.offset(inset.dp,(seekY-24*u).dp).size(contentWidth.dp,(48*u).dp)) {
                onState(state.copy(progress=it))
            }
            Row(Modifier.offset(inset.dp,(seekY+11*u).dp).width(contentWidth.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                val total=album.durationSeconds
                Text(if(total!=null) time((total*state.progress).roundToInt()) else "—:—",color=muted,fontSize=(11*u).sp,fontFamily=font)
                Text(total?.let(::time) ?: "—:—",color=muted,fontSize=(11*u).sp,fontFamily=font)
            }
            Text(title,Modifier.offset(inset.dp,titleY.dp).width((contentWidth-33*u).dp),color=text,
                fontSize=(titleSize*u).sp,fontFamily=font,fontWeight=FontWeight.SemiBold,
                maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=(titleSize*1.16f*u).sp)
            Text(metadata,Modifier.offset(inset.dp,metadataY.dp).width((contentWidth-20*u).dp),
                color=muted,fontSize=(16*u).sp,lineHeight=(20*u).sp,fontFamily=font,maxLines=2,overflow=TextOverflow.Ellipsis)
            RoundControl(if(state.liked) "Remover favorito (simulação)" else "Favoritar (simulação)","heart",
                Modifier.offset((w-inset-34*u).dp,(titleY-2*u).dp).size((48*u).dp),
                { onState(state.copy(liked=!state.liked)) },if(state.liked) Color(r.progress.thumb) else muted)
            val buttonSize=80*u
            val small=48*u
            listOf(.135f,.315f,.685f,.865f).forEachIndexed { i,x ->
                val labels=listOf("Aleatório (simulação)","Capa anterior","Próxima capa","Repetir (simulação)")
                val icons=listOf("shuffle","previous","next","repeat")
                RoundControl(labels[i],icons[i],Modifier.offset((w*x-small/2).dp,(controlsY-small/2).dp).size(small.dp),{
                    when(i) { 0 -> onState(state.copy(shuffle=!state.shuffle)); 1 -> previous(); 2 -> next(); 3 -> onState(state.copy(repeat=!state.repeat)) }
                },if((i==0 && state.shuffle)||(i==3 && state.repeat)) Color(r.progress.thumb) else if(i==3) muted else text)
            }
            PlayControl(r,state.playing,Modifier.offset((w/2-buttonSize/2).dp,(controlsY-buttonSize/2).dp).size(buttonSize.dp)) {
                onState(state.copy(playing=!state.playing))
            }
            Box(Modifier.offset((8*u).dp,lyricsY.dp).width((w-16*u).dp).height((h-lyricsY).coerceAtLeast(60*u).dp)
                .clip(RoundedCornerShape(topStart=(36*u).dp,topEnd=(36*u).dp))
                .border((.6f*u).dp,Color(0x446b7488),RoundedCornerShape(topStart=(36*u).dp,topEnd=(36*u).dp))
                .background(Color(r.lyrics.surface)).clickable { onState(state.copy(lyrics=true)) },contentAlignment=Alignment.TopCenter) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(top=(6*u).dp)) {
                    Glyph("up",text,Modifier.size((24*u).dp))
                    Text("LETRAS",color=muted,fontSize=(10*u).sp,letterSpacing=(2.5*u).sp,fontFamily=font)
                }
            }
        }
        if (debug==DebugView.BUTTON) PlayControl(r,state.playing,Modifier.align(Alignment.Center).size((80*u).dp)) { onState(state.copy(playing=!state.playing)) }
        if (debug==DebugView.FINAL && state.lyrics) {
            Column(Modifier.offset(inset.dp,artY.dp).width(contentWidth.dp),verticalArrangement=Arrangement.spacedBy((20*u).dp)) {
                Text(album.track ?: album.album,color=text,fontSize=(22*u).sp,lineHeight=(28*u).sp,fontFamily=font,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("LETRAS · DEMONSTRAÇÃO\nSem letras reais ou versos atribuídos à faixa",color=muted,fontSize=(13*u).sp,lineHeight=(18*u).sp,fontFamily=font)
                repeat(5) { i -> Text("Linha de teste ${i+1}",color=if(i==1) Color(r.lyrics.active) else Color(r.lyrics.future).copy(alpha=if(i>1) .5f else .8f),
                    fontSize=(26*u).sp,lineHeight=(33*u).sp,fontFamily=font,fontWeight=FontWeight.SemiBold) }
                Text("Voltar ao player",color=text,fontSize=(16*u).sp,fontFamily=font,
                    modifier=Modifier.heightIn(min=(48*u).dp).clickable { onState(state.copy(lyrics=false)) }.padding((12*u).dp))
            }
        }
    }
}

fun progressX(fraction: Float,width: Float) = fraction.coerceIn(0f,1f)*width
private fun time(seconds:Int) = "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"

@Composable private fun ProgressBar(value:Float,r:ArtRecipe,modifier:Modifier,onSeek:(Float)->Unit) {
    Canvas(modifier.semantics { contentDescription="Progresso demonstrativo"; progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)
        setProgress { onSeek(it.coerceIn(0f,1f)); true } }
        .pointerInput(Unit) { detectTapGestures { onSeek((it.x/size.width).coerceIn(0f,1f)) } }
        .pointerInput(Unit) { detectDragGestures { change,_ -> change.consume(); onSeek((change.position.x/size.width).coerceIn(0f,1f)) } }) {
        val x=progressX(value,size.width)
        val thick=size.width*.009f
        drawLine(Color(r.progress.track),Offset(0f,size.height/2),Offset(size.width,size.height/2),thick,StrokeCap.Round)
        // Brush coordinates ALWAYS cover the full track. Only the reveal rectangle changes.
        val brush=Brush.horizontalGradient(*r.progress.fill.map { it.position to Color(it.color) }.toTypedArray(),startX=0f,endX=size.width)
        clipRect(right=x) { drawLine(brush,Offset(0f,size.height/2),Offset(size.width,size.height/2),thick,StrokeCap.Round) }
        drawCircle(Color(r.progress.thumb),size.width*.014f,Offset(x,size.height/2))
    }
}
@Composable private fun PlayControl(r:ArtRecipe,playing:Boolean,modifier:Modifier,onClick:()->Unit) {
    BoxWithConstraints(modifier) {
        val edge=maxWidth
        Canvas(Modifier.requiredSize(edge*1.9f).align(Alignment.Center)) {
            drawCircle(Brush.radialGradient(listOf(Color(r.controlSurface.haloColor).copy(alpha=r.controlSurface.haloIntensity),Color.Transparent)),size.minDimension/2)
        }
        Box(Modifier.fillMaxSize().padding(edge*.075f).clip(CircleShape)
            .background(Brush.linearGradient(r.controlSurface.fill.map { Color(it.color) }))
            .border(.7.dp,Color(r.controlSurface.specular).copy(alpha=.4f),CircleShape)
            .clickable(role=Role.Button,onClickLabel=if(playing) "Pausar (simulação)" else "Reproduzir (simulação)",onClick=onClick),contentAlignment=Alignment.Center) {
            Glyph(if(playing) "pause" else "play",Color(r.controlSurface.ink),Modifier.size(edge*.39f))
        }
    }
}
@Composable private fun RoundControl(label:String,icon:String,modifier:Modifier,onClick:()->Unit,color:Color=Color(0xfffafbff),outlined:Boolean=false) {
    BoxWithConstraints(modifier.semantics { contentDescription=label }.clickable(role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        val edge=maxWidth
        if(outlined) Box(Modifier.size(edge*.8f).clip(CircleShape).border(.7.dp,Color(0x407b8193),CircleShape).background(Color(0x22171c2a)))
        Glyph(icon,color,Modifier.size(edge*(if(outlined || icon=="heart") .42f else .50f)))
    }
}
@Composable private fun Glyph(kind:String,color:Color,modifier:Modifier) {
    Canvas(modifier) {
        val unit=size.minDimension
        fun p(x:Float,y:Float)=Offset(x*size.width,y*size.height)
        fun line(a:Offset,b:Offset)=drawLine(color,a,b,unit*.085f,StrokeCap.Round)
        fun triangle(reverse:Boolean=false) {
            val path=Path().apply {
                if(reverse) { moveTo(size.width*.2f,size.height*.5f); lineTo(size.width*.82f,size.height*.1f); lineTo(size.width*.82f,size.height*.9f) }
                else { moveTo(size.width*.82f,size.height*.5f); lineTo(size.width*.2f,size.height*.1f); lineTo(size.width*.2f,size.height*.9f) }
                close()
            }; drawPath(path,color)
        }
        when(kind) {
            "down","up" -> { val a=if(kind=="down") .35f else .65f; line(p(.18f,a),p(.5f,1-a)); line(p(.5f,1-a),p(.82f,a)) }
            "menu" -> listOf(.2f,.5f,.8f).forEach { drawCircle(color,unit*.065f,p(.5f,it)) }
            "pause" -> { drawRoundRect(color,p(.24f,.12f),Size(unit*.18f,size.height*.76f),androidx.compose.ui.geometry.CornerRadius(unit*.045f));
                drawRoundRect(color,p(.59f,.12f),Size(unit*.18f,size.height*.76f),androidx.compose.ui.geometry.CornerRadius(unit*.045f)) }
            "play" -> triangle()
            "previous","next" -> { triangle(kind=="previous"); val x=if(kind=="previous") .13f else .9f; line(p(x,.1f),p(x,.9f)) }
            "heart" -> { val path=Path().apply { moveTo(size.width*.5f,size.height*.92f); cubicTo(-size.width*.3f,size.height*.35f,size.width*.15f,-size.height*.2f,size.width*.5f,size.height*.22f); cubicTo(size.width*.85f,-size.height*.2f,size.width*1.3f,size.height*.35f,size.width*.5f,size.height*.92f); close() }; drawPath(path,color) }
            "shuffle" -> { line(p(.05f,.2f),p(.22f,.2f)); line(p(.22f,.2f),p(.78f,.8f)); line(p(.78f,.8f),p(.96f,.8f)); line(p(.05f,.8f),p(.22f,.8f)); line(p(.22f,.8f),p(.78f,.2f)); line(p(.78f,.2f),p(.96f,.2f)); line(p(.82f,.05f),p(.97f,.2f)); line(p(.82f,.35f),p(.97f,.2f)); line(p(.82f,.65f),p(.97f,.8f)); line(p(.82f,.95f),p(.97f,.8f)) }
            "repeat" -> { line(p(.1f,.36f),p(.25f,.18f)); line(p(.25f,.18f),p(.94f,.18f)); line(p(.8f,.03f),p(.95f,.18f)); line(p(.8f,.33f),p(.95f,.18f)); line(p(.9f,.64f),p(.75f,.82f)); line(p(.75f,.82f),p(.06f,.82f)); line(p(.2f,.67f),p(.05f,.82f)); line(p(.2f,.97f),p(.05f,.82f)) }
        }
    }
}

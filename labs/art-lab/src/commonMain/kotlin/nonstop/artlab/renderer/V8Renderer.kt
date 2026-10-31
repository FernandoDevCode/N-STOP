package nonstop.artlab.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.*
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
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
import nonstop.artlab.direction.ControlMaterial
import kotlin.math.*

enum class DebugView { FINAL, ART_BASE, LIGHTS, SCRIM, BUTTON }
data class PlayerState(val progress: Float = .43f, val playing: Boolean = true,
    val liked: Boolean = true, val shuffle: Boolean = false, val repeat: Boolean = false, val lyrics: Boolean = false)

// Shared geometry: measured average of the two original references, not inherited from v7.
object MasterGeometry {
    const val inset = .083f
    const val coverWidth = .834f
    const val coverTop = .1035f
    const val coverHeight = .4597f
    const val progressCenter = .5957f
    const val transportCenter = .795f
    const val lyricsTop = .902f
}

@Composable
fun V8Renderer(album: Album, cover: ImageBitmap?, atmosphere: AtmosphereBitmaps?, recipe: ArtRecipe,
    state: PlayerState, onState: (PlayerState) -> Unit, previous: () -> Unit, next: () -> Unit,
    onClose: () -> Unit, onMenu: () -> Unit, font: FontFamily, debug: DebugView = DebugView.FINAL,
    fontScale: Float = 1f, modifier: Modifier = Modifier,
    microOptions:MicroOptions=MicroOptions(),microState:PlayerMicroState?=null,
    production: Boolean = false, seekEnabled: Boolean = true, repeatOne: Boolean = false,
    onInfo: () -> Unit = {}, coverContent: (@Composable (Modifier) -> Unit)? = null,
    lyricsContent: (@Composable (Modifier, Float) -> Unit)? = null) {
    val r = remember(recipe) { safeRecipe(recipe) }
    val micro=microState ?: remember(r.sourceImageHash) { PlayerMicroState() }
    val profile=remember(r.familyWeights) { MicroProfile.from(r) }
    val controlsVisible=debug==DebugView.BUTTON || (debug==DebugView.FINAL && !state.lyrics)
    MicroAnimationDriver(micro,microOptions.copy(active=microOptions.active && controlsVisible),state.playing,state.progress,profile)
    BoxWithConstraints(modifier.background(Color(r.canvasColor))) {
        val w = maxWidth.value
        val h = maxHeight.value
        val u = w / 432f
        val inset = w * MasterGeometry.inset
        val contentWidth = w * MasterGeometry.coverWidth
        // Make room for accessible typography before allowing lower controls to leave the viewport.
        val artSize = contentWidth * (1f-.36f*(fontScale-1f).coerceIn(0f,.5f))
        val artHeight = min(contentWidth,h*MasterGeometry.coverHeight)*(1f-.36f*(fontScale-1f).coerceIn(0f,.5f))
        val artY = h * MasterGeometry.coverTop
        val seekY = h*MasterGeometry.progressCenter-(contentWidth-artSize)
        val titleY = seekY + 43f*u
        val title=album.track ?: "Faixa não fornecida"
        val measurer=rememberTextMeasurer()
        val density=LocalDensity.current
        val baseTitleSize=27f
        val intrinsic=with(density) { measurer.measure(title,TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,
            fontSize=(baseTitleSize*u).sp),maxLines=1).size.width.toDp().value }
        val titleSize=if(fontScale>1.05f)baseTitleSize else (baseTitleSize*(contentWidth-33*u)/intrinsic.coerceAtLeast(1f)).coerceIn(18f,baseTitleSize)
        val titleHeight=with(density) { measurer.measure(title,
            TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=(titleSize*u).sp,lineHeight=(titleSize*1.16f*u).sp),
            maxLines=2,constraints=Constraints(maxWidth=(contentWidth-33*u).dp.roundToPx())).size.height.toDp().value }
        val metadata="${album.artist}  •  ${album.album}"
        val metadataY=titleY+titleHeight+4*u
        val metadataHeight=with(density) { measurer.measure(metadata,
            TextStyle(fontFamily=font,fontSize=(16*u).sp,lineHeight=(20*u).sp),maxLines=2,
            constraints=Constraints(maxWidth=(contentWidth-20*u).dp.roundToPx())).size.height.toDp().value }
        // Typography-dependent optical spacing, shared for any title. Compact fitted headlines
        // leave more breathing room before transport; no catalog identity enters geometry.
        val controlsY = max(h*MasterGeometry.transportCenter+(baseTitleSize-titleSize)*2.8f*u, metadataY+metadataHeight+48*u)
        val lyricsY = max(h*MasterGeometry.lyricsTop, controlsY+70*u)
        val text = Color(0xfffafbff)
        val muted = Color(0xff9296a6)
        val showArt = debug in listOf(DebugView.FINAL,DebugView.ART_BASE)
        if (showArt && atmosphere != null) Image(if(debug==DebugView.FINAL)atmosphere.composite else atmosphere.deep, null, Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
        if (debug==DebugView.LIGHTS && atmosphere!=null) {
            listOf(atmosphere.diffuse,atmosphere.accents,atmosphere.directional).forEach { layer ->
                Image(layer,null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
            }
        }
        // Decode/missing-art fallback still has depth without doing precomputation on the UI thread.
        if(atmosphere==null && debug in listOf(DebugView.FINAL,DebugView.ART_BASE,DebugView.LIGHTS)) Canvas(Modifier.fillMaxSize()) {
            r.lights.forEach { l ->
                val center=Offset(l.x*size.width,l.y*size.height)
                val rx=l.radiusX*size.width; val ry=l.radiusY*size.height
                scale(1f,ry/rx,center) {
                    val stops=(0..8).map { i -> val t=i/8f; t to Color(l.color).copy(alpha=l.intensity*(1-t).pow(l.falloff)) }
                    drawCircle(Brush.radialGradient(*stops.toTypedArray(),center=center,radius=rx),rx,center)
                }
            }
        }
        if (debug in listOf(DebugView.FINAL,DebugView.SCRIM)) Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Black.copy(alpha=r.scrimTop),.5f to Color.Transparent,1f to Color.Black.copy(alpha=r.scrimBottom))))
        if (debug == DebugView.FINAL) {
            RoundControl("Recolher player", "down", Modifier.offset((inset-24*u).dp,(h*.027f-4*u).dp).size((48*u).dp),onClose,outlined=true)
            RoundControl(if (production) "Opções da faixa" else "Diagnóstico", "menu", Modifier.offset((w-inset-24*u).dp,(h*.027f-4*u).dp).size((48*u).dp),onMenu,outlined=true)
        }
        if (showArt && !state.lyrics) {
            Box(Modifier.offset(((w-artSize)/2).dp,artY.dp).size(artSize.dp,artHeight.dp).clip(RoundedCornerShape((15*u).dp))) {
                if (coverContent != null) coverContent(Modifier.fillMaxSize())
                else if (cover != null) Image(cover,"${album.artist} — ${album.album}; ${album.status}",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Color(0xff131b29)),contentAlignment=Alignment.Center) {
                    Text("CAPA INDISPONÍVEL\n${album.album}", color=muted, fontFamily=font,
                        fontSize=(17*u).sp,lineHeight=(24*u).sp,modifier=Modifier.padding((24*u).dp))
                }
            }
        }
        if (debug == DebugView.FINAL && !state.lyrics) {
            ProgressBar(state.progress,r,micro,microOptions,u,Modifier.offset(inset.dp,(seekY-24*u).dp).size(contentWidth.dp,(48*u).dp), enabled=seekEnabled) {
                if (seekEnabled) onState(state.copy(progress=it))
            }
            Row(Modifier.offset(inset.dp,(seekY+11*u).dp).width(contentWidth.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                val total=album.durationSeconds
                Text(if(total!=null) time((total*state.progress).roundToInt()) else "—:—",color=muted,fontSize=(11*u).sp,fontFamily=font)
                Text(total?.let(::time) ?: "—:—",color=muted,fontSize=(11*u).sp,fontFamily=font)
            }
            Text(title,Modifier.offset(inset.dp,titleY.dp).width((contentWidth-33*u).dp).clickable(onClick=onInfo),color=text,
                fontSize=(titleSize*u).sp,fontFamily=font,fontWeight=FontWeight.SemiBold,
                maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=(titleSize*1.16f*u).sp,letterSpacing=0.sp)
            Text(metadata,Modifier.offset(inset.dp,metadataY.dp).width((contentWidth-20*u).dp).clickable(onClick=onInfo),
                color=muted,fontSize=(16*u).sp,lineHeight=(20*u).sp,fontFamily=font,maxLines=2,overflow=TextOverflow.Ellipsis,letterSpacing=0.sp)
            RoundControl(if(state.liked) "Remover favorito" else "Favoritar","heart",
                Modifier.offset((w-inset-34*u).dp,(titleY-2*u).dp).size((48*u).dp),
                { onState(state.copy(liked=!state.liked)) },if(state.liked) Color(r.favoriteColor) else muted,heartFilled=state.liked)
            val buttonSize=w*r.controlSurface.diameterRatio/.85f
            val small=48*u
            listOf(.135f,.315f,.685f,.865f).forEachIndexed { i,x ->
                val labels=if (production) listOf("Aleatório", "Faixa anterior", "Próxima faixa", if (repeatOne) "Repetir uma faixa" else if (state.repeat) "Repetir todas" else "Repetição desligada") else listOf("Aleatório (simulação)","Capa anterior","Próxima capa","Repetir (simulação)")
                val icons=listOf("shuffle","previous","next","repeat")
                RoundControl(labels[i],icons[i],Modifier.offset((w*x-small/2).dp,(controlsY-small/2).dp).size(small.dp),{
                    when(i) { 0 -> onState(state.copy(shuffle=!state.shuffle)); 1 -> previous(); 2 -> next(); 3 -> onState(state.copy(repeat=!state.repeat)) }
                },if((i==0 && state.shuffle)||(i==3 && state.repeat)) Color(r.progress.thumb) else if(i==3) muted else text)
            }
            if (production && repeatOne) Text("1", Modifier.offset((w*.865f+9*u).dp,(controlsY-19*u).dp),
                color=Color(r.progress.thumb),fontFamily=font,fontSize=(10*u).sp)
            PlayControl(r,state.playing,w,micro,microOptions,profile,Modifier.offset((w/2-buttonSize/2).dp,(controlsY-buttonSize/2).dp).size(buttonSize.dp)) {
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
        if (debug==DebugView.BUTTON) PlayControl(r,state.playing,w,micro,microOptions,profile,Modifier.align(Alignment.Center).size((w*r.controlSurface.diameterRatio/.85f).dp)) { onState(state.copy(playing=!state.playing)) }
        if (debug==DebugView.FINAL && state.lyrics) {
            lyricsContent?.invoke(Modifier.offset(inset.dp,artY.dp).width(contentWidth.dp), u)
        }
    }
}

fun progressX(fraction: Float,width: Float) = fraction.coerceIn(0f,1f)*width
private fun time(seconds:Int) = "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"

@Composable private fun ProgressBar(value:Float,r:ArtRecipe,micro:PlayerMicroState,options:MicroOptions,unit:Float,modifier:Modifier,enabled:Boolean=true,onSeek:(Float)->Unit) {
    val latestSeek by rememberUpdatedState(onSeek)
    val latestValue by rememberUpdatedState(value)
    val latestRecipe by rememberUpdatedState(r)
    val interactions=remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    LaunchedEffect(hovered,focused,micro) { micro.hover(hovered); micro.focus(focused) }
    DisposableEffect(micro) { onDispose { micro.endDrag(); micro.hover(false); micro.focus(false) } }
    Canvas(modifier.semantics { contentDescription="Progresso"; progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)
        if (!enabled) disabled()
        setProgress { if(enabled && it.isFinite()) { onSeek(it.coerceIn(0f,1f)); true } else false } }
        .hoverable(interactions).onKeyEvent { event ->
            val p=when(event.key) { Key.DirectionLeft -> latestValue-.01f; Key.DirectionRight -> latestValue+.01f; Key.MoveHome -> 0f; Key.MoveEnd -> 1f; else -> null }
            if(!enabled || p==null)false else { micro.keyPress(event.type==KeyEventType.KeyDown); if(event.type==KeyEventType.KeyDown)latestSeek(p.coerceIn(0f,1f)); true }
        }.focusable(interactionSource=interactions)
        .pointerInput(micro,enabled) { if (enabled) detectTapGestures(onPress={
            micro.press(true)
            try { tryAwaitRelease() } finally { micro.press(false) }
        }) { latestSeek((it.x/size.width).coerceIn(0f,1f)) } }
        .pointerInput(micro,enabled) { if (enabled) detectHorizontalDragGestures(
            onDragStart={ p -> val fraction=(p.x/size.width).coerceIn(0f,1f); micro.beginDrag(fraction,progressColorAt(latestRecipe,fraction)); latestSeek(fraction) },
            onDragEnd={micro.endDrag()},onDragCancel={micro.endDrag()}) { change,_ ->
                change.consume(); val fraction=(change.position.x/size.width).coerceIn(0f,1f)
                micro.dragTo(fraction,progressColorAt(latestRecipe,fraction)); latestSeek(fraction)
            } }) {
        micro.revision // Draw-phase observation only, never a layout dependency.
        val x=progressX(value,size.width)
        val thick=size.width*.009f
        drawLine(Color(r.progress.track),Offset(0f,size.height/2),Offset(size.width,size.height/2),thick,StrokeCap.Round)
        // Brush coordinates ALWAYS cover the full track. Only the reveal rectangle changes.
        val brush=Brush.horizontalGradient(*r.progress.fill.map { it.position to Color(it.color) }.toTypedArray(),startX=0f,endX=size.width)
        clipRect(right=x) { drawLine(brush,Offset(0f,size.height/2),Offset(size.width,size.height/2),thick,StrokeCap.Round) }
        val thumbCenter=Offset(x,size.height/2)
        val logicalDp=unit.dp.toPx()
        if(options.enabled) {
            if(!options.reducedMotion)clipRect(top=size.height/2-8*logicalDp,bottom=size.height/2+8*logicalDp) {
                val p=micro.trail
                for(i in p.age.indices)if(p.age[i]<p.life[i]) {
                    val t=p.age[i]/p.life[i]; val fade=(1-t)*(1-t)
                    val center=Offset(p.x[i]*size.width+p.drift[i]*(p.age[i]/1000)*logicalDp,
                        size.height/2+p.y[i]*(1+t*.4f)*logicalDp)
                    val radius=p.size[i]*.5f*logicalDp
                    // Two faint antialiased discs approximate a soft dust point without creating
                    // a native gradient shader for every particle on every frame.
                    drawCircle(Color(p.color[i]).copy(alpha=p.alpha[i]*fade*.25f),radius,center)
                    drawCircle(Color(p.color[i]).copy(alpha=p.alpha[i]*fade*.75f),radius*.48f,center)
                }
            }
            val engagement=micro.engagement
            val radius=(MicroParameters.IDLE_RADIUS_DP+(MicroParameters.DRAG_RADIUS_DP-MicroParameters.IDLE_RADIUS_DP)*engagement)*logicalDp
            val alpha=MicroParameters.IDLE_ALPHA+(MicroParameters.DRAG_ALPHA-MicroParameters.IDLE_ALPHA)*engagement
            val tint=Color(progressColorAt(r,value))
            drawCircle(Brush.radialGradient(0f to tint.copy(alpha=alpha),.65f to tint.copy(alpha=alpha),1f to Color.Transparent,
                center=thumbCenter,radius=radius),radius,thumbCenter)
        }
        drawCircle(Color(r.progress.thumb),size.width*.014f*(if(options.enabled && !options.reducedMotion)1+.08f*micro.engagement else 1f),thumbCenter)
    }
}
@Composable private fun PlayControl(r:ArtRecipe,playing:Boolean,viewportWidth:Float,micro:PlayerMicroState,options:MicroOptions,profile:MicroProfile,modifier:Modifier,onClick:()->Unit) {
    BoxWithConstraints(modifier) {
        val edge=maxWidth
        Canvas(Modifier.requiredSize((viewportWidth*r.controlSurface.haloRadius*2+12*viewportWidth/432).dp).align(Alignment.Center)) {
            micro.revision
            val surface=r.controlSurface
            val energy=if(options.enabled && options.active && !options.reducedMotion)micro.energy else 0f
            val unit=(viewportWidth/432).dp.toPx()
            val radius=(viewportWidth*surface.haloRadius).dp.toPx()+energy*profile.glowSpreadDp*unit
            val alpha=surface.haloIntensity+energy*profile.audioGain*.12f
            if(surface.haloSpectrum.isEmpty()) {
                drawCircle(Brush.radialGradient(listOf(Color(surface.haloColor).copy(alpha=alpha),Color.Transparent),radius=radius),radius)
            } else {
                drawContext.canvas.saveLayer(Rect(Offset.Zero,size),Paint())
                rotate(surface.spectrumRotation) { drawCircle(Brush.sweepGradient(*surface.haloSpectrum.map { it.position to Color(it.color) }.toTypedArray()),radius) }
                drawRect(Brush.radialGradient(0f to Color.Transparent,.32f to Color.Transparent,
                    .5f to Color.White.copy(alpha=alpha),1f to Color.Transparent,radius=radius),blendMode=BlendMode.DstIn)
                drawContext.canvas.restore()
            }
        }
        Box(Modifier.fillMaxSize().padding(edge*.075f).graphicsLayer {
            micro.revision
            val amount=if(options.enabled && options.active && !options.reducedMotion)micro.energy*profile.audioGain else 0f
            scaleX=1f+.02f*amount; scaleY=scaleX
        }.clip(CircleShape)
            .background(Brush.linearGradient(*r.controlSurface.fill.map { it.position to Color(it.color) }.toTypedArray()))
            .clickable(role=Role.Button,onClickLabel=if(playing) "Pausar" else "Reproduzir",onClick=onClick),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                micro.revision
                val surface=r.controlSurface
                // Diffuse center only; no specular/metallic highlight. Materials differ structurally.
                if(surface.material==ControlMaterial.MATTE_FILLED) drawCircle(Brush.radialGradient(
                    listOf(Color(surface.ink).copy(alpha=.025f),Color.Transparent)),size.width*.5f)
                if(surface.rim.isNotEmpty()) rotate(surface.spectrumRotation) {
                    val rim=if(surface.haloSpectrum.isEmpty())Brush.linearGradient(*surface.rim.map { it.position to Color(it.color) }.toTypedArray())
                        else Brush.sweepGradient(*surface.rim.map { it.position to Color(it.color) }.toTypedArray())
                    drawCircle(rim,
                        radius=size.minDimension/2-(viewportWidth/432).dp.toPx()*2,
                        style=Stroke((viewportWidth/432).dp.toPx()*((if(surface.material==ControlMaterial.PRISMATIC)1.7f else .7f)+
                            if(options.enabled && options.active && !options.reducedMotion)micro.energy*profile.audioGain*1.5f else 0f)))
                }
            }
            Glyph(if(playing) "pause" else "play",Color(r.controlSurface.ink),Modifier.size(edge*.39f))
        }
    }
}
@Composable private fun RoundControl(label:String,icon:String,modifier:Modifier,onClick:()->Unit,color:Color=Color(0xfffafbff),outlined:Boolean=false,heartFilled:Boolean=true) {
    BoxWithConstraints(modifier.semantics { contentDescription=label }.clickable(role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        val edge=maxWidth
        if(outlined) Box(Modifier.size(edge*.8f).clip(CircleShape).border(edge*.015f,Color(0x507b8193),CircleShape).background(Color(0x22171c2a)))
        Glyph(icon,color,Modifier.size(edge*(if(outlined || icon=="heart") .42f else if(icon=="previous" || icon=="next") .60f else .50f)),heartFilled)
    }
}
@Composable private fun Glyph(kind:String,color:Color,modifier:Modifier,filled:Boolean=true) {
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
            "heart" -> { val path=Path().apply { moveTo(size.width*.5f,size.height*.92f); cubicTo(-size.width*.3f,size.height*.35f,size.width*.15f,-size.height*.2f,size.width*.5f,size.height*.22f); cubicTo(size.width*.85f,-size.height*.2f,size.width*1.3f,size.height*.35f,size.width*.5f,size.height*.92f); close() }; drawPath(path,color,style=if(filled)Fill else Stroke(unit*.075f,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
            "shuffle" -> { line(p(.05f,.2f),p(.22f,.2f)); line(p(.22f,.2f),p(.78f,.8f)); line(p(.78f,.8f),p(.96f,.8f)); line(p(.05f,.8f),p(.22f,.8f)); line(p(.22f,.8f),p(.78f,.2f)); line(p(.78f,.2f),p(.96f,.2f)); line(p(.82f,.05f),p(.97f,.2f)); line(p(.82f,.35f),p(.97f,.2f)); line(p(.82f,.65f),p(.97f,.8f)); line(p(.82f,.95f),p(.97f,.8f)) }
            "repeat" -> { line(p(.1f,.36f),p(.25f,.18f)); line(p(.25f,.18f),p(.94f,.18f)); line(p(.8f,.03f),p(.95f,.18f)); line(p(.8f,.33f),p(.95f,.18f)); line(p(.9f,.64f),p(.75f,.82f)); line(p(.75f,.82f),p(.06f,.82f)); line(p(.2f,.67f),p(.05f,.82f)); line(p(.2f,.97f),p(.05f,.82f)) }
        }
    }
}

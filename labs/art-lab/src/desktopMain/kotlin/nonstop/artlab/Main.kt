package nonstop.artlab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import nonstop.artlab.catalog.*
import nonstop.artlab.direction.*
import nonstop.artlab.renderer.*
import nonstop.artlab.resources.*
import org.jetbrains.compose.resources.Font
import org.jetbrains.skia.EncodedImageFormat
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import nonstop.artlab.features.contrast

fun main(args: Array<String>) {
    if("--validate-micro" in args) {
        System.setProperty("java.awt.headless","true")
        runMicroValidation()
        return
    }
    if(args.any { it=="--validate-procedural" || it=="--cache-probe" }) {
        System.setProperty("java.awt.headless","true")
        runProceduralValidation(args)
        return
    }
    if(args.any { it=="--validate-offscreen" || it=="--capture-suite" }) {
        System.setProperty("java.awt.headless","true")
        runOffscreenValidation(args)
        return
    }
    launchLab()
}
private fun launchLab() = application {
    val windowState=rememberWindowState(width=1280.dp,height=900.dp)
    Window(onCloseRequest=::exitApplication,title="NØNSTOP Art Lab v8 · Procedural Art Director",
        state=windowState) {
        MaterialTheme(colorScheme=darkColorScheme(background=Color(0xff080d17),surface=Color(0xff111a29))) {
            Surface { ArtLab(active=!windowState.isMinimized) }
        }
    }
}

@Composable private fun ArtLab(active:Boolean) {
    val font=FontFamily(Font(Res.font.inter_regular,FontWeight.Normal),Font(Res.font.inter_semibold,FontWeight.SemiBold))
    val scope=rememberCoroutineScope()
    val assets=remember { DesktopAssets() }
    val albums=remember { mutableStateListOf<Album>().apply { addAll(validationCatalog) } }
    var selected by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf(PlayerState()) }
    var asset by remember { mutableStateOf(CoverAsset()) }
    var loading by remember { mutableStateOf(false) }
    var galleryVisible by remember { mutableStateOf(true) }
    var playerOpen by remember { mutableStateOf(true) }
    var diagnostics by remember { mutableStateOf(true) }
    var viewport by remember { mutableIntStateOf(0) }
    var nativePixels by remember { mutableStateOf(false) }
    var largeFont by remember { mutableStateOf(false) }
    var debug by remember { mutableStateOf(DebugView.FINAL) }
    var comparisonMode by remember { mutableStateOf(ComparisonMode.PRODUCAO) }
    var notice by remember { mutableStateOf("") }
    var reduceMotion by remember { mutableStateOf(false) }
    var transitions by remember { mutableStateOf(true) }
    var microEnabled by remember { mutableStateOf(true) }
    var labAudio by remember { mutableStateOf<LabAudio?>(null) }
    var audioNotice by remember { mutableStateOf("Sem fonte de áudio — play em repouso") }
    val layers=rememberGraphicsLayer()
    var captureRequested by remember { mutableStateOf(false) }
    val viewports=listOf(864 to 1536,941 to 1672,1080 to 2340,720 to 1280)
    val (vw,vh)=viewports[viewport]
    val album=albums[selected]
    val recipe=asset.recipe
    val displayCover=remember(album,asset,comparisonMode) { comparisonCover(album,asset,comparisonMode) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val file=File("build/exports/catalog-manifest.json").apply { parentFile.mkdirs() }
            file.writeText(recipeJson.encodeToString(validationCatalog))
        }
    }
    LaunchedEffect(album) {
        loading=true
        asset=CoverAsset(message="Carregando…")
        asset=withContext(Dispatchers.IO) { assets.load(album) { isActive } }
        loading=false
    }
    fun select(index:Int) { selected=(index+albums.size)%albums.size; player=player.copy(lyrics=false); playerOpen=true }
    suspend fun capture(name:String) {
        captureRequested=true
        val bitmap=try {
            withFrameNanos { }; withFrameNanos { }
            layers.toImageBitmap()
        } finally { captureRequested=false }
        val directory=File("build/captures-procedural/manual").apply { mkdirs() }
        val file=File(directory,"$name.png")
        withContext(Dispatchers.IO) { org.jetbrains.skia.Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { file.writeBytes(it.bytes) }
        } }
        withContext(Dispatchers.IO) {
            File(directory,"$name-recipe.json").writeText(recipeJson.encodeToString(asset.recipe))
            asset.features?.let { File(directory,"$name-features.json").writeText(recipeJson.encodeToString(it)) }
            File(directory,"$name-timings.txt").writeText("${asset.cacheOrigin}\n${asset.timings}")
        }
        notice="Captura real do layer Compose: ${file.absolutePath}"
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("NØNSTOP / ART LAB v8",fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=19.sp)
            Text("PROCEDURAL · CONTROLES SIMULADOS",color=Color(0xffadbcd0),fontSize=11.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick={galleryVisible=!galleryVisible}) { Text("Galeria") }
            TextButton(onClick={diagnostics=!diagnostics}) { Text("Diagnóstico") }
            Button(onClick={
                SwingUtilities.invokeLater {
                    val dialog=FileDialog(null as Frame?,"Carregar capa local PNG/JPEG",FileDialog.LOAD)
                    dialog.isVisible=true
                    if(dialog.file!=null) {
                        val file=File(dialog.directory,dialog.file)
                        scope.launch {
                            albums.add(Album("Arquivo local",file.nameWithoutExtension,resource=file.absolutePath,
                                status=ArtworkStatus.LOCAL,source=file.absolutePath))
                            select(albums.lastIndex)
                        }
                    }
                    dialog.dispose()
                }
            }) { Text("Carregar capa local") }
        }
        Text("DIREÇÃO PROCEDURAL ESTÁTICA • análise clássica de pixels • sem IA • benchmark: 12 capas",color=Color(0xffd7bd88),fontSize=12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(galleryVisible) Column(Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("CATÁLOGO FIXO / 12 ÁLBUNS",fontSize=12.sp,color=Color(0xff9aabc1))
                Spacer(Modifier.height(8.dp))
                albums.chunked(2).forEachIndexed { row,pair ->
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        pair.forEachIndexed { col,item ->
                            val index=row*2+col
                            Column(Modifier.width(121.dp).clip(RoundedCornerShape(8.dp))
                                .background(if(index==selected) Color(0xff273c55) else Color(0xff121b29))
                                .clickable { select(index) }.padding(8.dp)) {
                                var thumbnail by remember(item) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                                LaunchedEffect(item) { thumbnail=withContext(Dispatchers.IO) { assets.thumbnail(item) } }
                                Box(Modifier.size(105.dp).background(Color(0xff080d16)),contentAlignment=Alignment.Center) {
                                    if(thumbnail!=null) Image(thumbnail!!,item.album,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                                    else Text("CAPA\nAUSENTE",fontSize=11.sp,color=Color(0xff8c9bb1))
                                }
                                Text(item.artist,fontSize=11.sp,maxLines=1)
                                Text(item.album,fontSize=11.sp,minLines=2,maxLines=2)
                                Text(if(item.status==ArtworkStatus.MISSING) "missing" else if(item.status==ArtworkStatus.LOCAL) "local" else "capa oficial completa",fontSize=9.sp,color=Color(0xffd7bd88))
                                Text(if(item.resource==null) "Fora desta rodada" else "Análise clássica",fontSize=9.sp,color=Color(0xff97a7bd))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                if(!playerOpen) {
                    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("Selecione uma capa da galeria para abrir o player.")
                        TextButton(onClick={playerOpen=true}) { Text("Reabrir ${album.album}") }
                    }
                } else {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={viewport=(viewport+1)%viewports.size}) { Text("${vw}×${vh} ▾") }
                    TextButton(onClick={comparisonMode=if(comparisonMode==ComparisonMode.PRODUCAO)ComparisonMode.FIDELIDADE else ComparisonMode.PRODUCAO}) {
                        Text(if(comparisonMode==ComparisonMode.PRODUCAO)"PRODUÇÃO" else "FIDELIDADE")
                    }
                    TextButton(onClick={nativePixels=!nativePixels}) { Text(if(nativePixels) "100% pixels" else "Ajustar à janela") }
                    TextButton(onClick={scope.launch { capture("manual-${selected+1}-${comparisonMode.name.lowercase()}-${vw}x$vh") }}) { Text("Capturar") }
                }
                BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff030509))) {
                    val density=LocalDensity.current
                    val availableWidth=with(density) { maxWidth.toPx() }
                    val availableHeight=with(density) { maxHeight.toPx() }
                    val scale=if(nativePixels) 1f else minOf(availableWidth/vw,availableHeight/vh).coerceAtMost(1f)
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),contentAlignment=Alignment.TopCenter) {
                        CompositionLocalProvider(LocalDensity provides Density(1f,if(largeFont) 1.5f else 1f)) {
                            Layout(content={
                                Crossfade(album to asset,animationSpec=tween(if(reduceMotion || !transitions)0 else 350),label="cover recipe") { (frameAlbum,frameAsset) ->
                                LabV8Renderer(frameAlbum,if(frameAlbum==album)displayCover else frameAsset.cover,frameAsset.texture,frameAsset.recipe,player,{player=it},{select(selected-1)},{select(selected+1)},
                                    {playerOpen=false; galleryVisible=true},{diagnostics=!diagnostics},font,debug,if(largeFont)1.5f else 1f,
                                    Modifier.fillMaxSize().drawWithContent {
                                        if(captureRequested) {
                                            layers.record { this@drawWithContent.drawContent() }
                                            drawLayer(layers)
                                        } else drawContent()
                                    },microOptions=MicroOptions(enabled=microEnabled,reducedMotion=reduceMotion,
                                        active=active && playerOpen && frameAlbum==album,audio=labAudio?.envelope))
                                }
                            }) { measurables,_ ->
                                val child=measurables.single().measure(Constraints.fixed(vw,vh))
                                layout((vw*scale).toInt(),(vh*scale).toInt()) {
                                    child.placeWithLayer(0,0) { scaleX=scale; scaleY=scale; transformOrigin=TransformOrigin(0f,0f) }
                                }
                            }
                        }
                    }
                }
                }
            }
            if(diagnostics) Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("DIAGNÓSTICO / FORA DO PLAYER",fontSize=12.sp,color=Color(0xff9aabc1))
                Text("${album.artist} — ${album.album}",fontWeight=FontWeight.SemiBold)
                Text("${album.status}\n${album.source ?: "Sem fonte verificável disponível; imagem ausente."}",fontSize=11.sp)
                Text(if(loading) "Carregando fora da UI thread…" else asset.message,fontSize=12.sp)
                Text("Exibição: $comparisonMode\nAnálise: sempre capa oficial (ou arquivo local)\nIA: nenhum modelo\nReceita: ${recipe.analysisMetadata.source}\nMotor: ${recipe.analysisMetadata.engineVersion}\nConfiança neural: indisponível\nCache: ${asset.cacheOrigin}\nWall total: ${"%.2f".format(asset.timings.totalMs)} ms\nCPU total: ${asset.timings.cpuMs?.let { "%.2f".format(it) } ?: "não medido"} ms\nDecode / features / direção / textura: ${listOf(asset.timings.decodeMs,asset.timings.featuresMs,asset.timings.directionMs,asset.timings.textureMs).joinToString(" / ") { "%.2f".format(it) }} ms\nHash: ${asset.hash}",fontSize=11.sp)
                recipe.archetypeScores.entries.sortedByDescending { it.value }.take(3).forEach { Text("${it.key}: ${"%.3f".format(it.value)} (heurística)",fontSize=11.sp) }
                Text("Família: ${recipe.family}\nMistura: ${recipe.familyWeights}\nMaterial: ${recipe.controlSurface.material}\nSeed: ${recipe.procedural.seed}\nGeração procedural: ${"%.2f".format(asset.timings.generationMs)} ms\nRGBA retido (estimativa): ${"%.2f".format(asset.retainedRasterBytes/1048576.0)} MiB",fontSize=11.sp)
                listOf("Ambiente" to recipe.colors.environmental,"Interação" to recipe.colors.interaction,"Acento" to recipe.colors.accent).forEach { (label,color) ->
                    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(20.dp).background(Color(color))); Text(" $label #${color.toString(16)}",fontSize=11.sp) }
                }
                Text("Parâmetros: ${recipe.procedural}",fontSize=11.sp)
                recipe.analysisMetadata.fiveSignals.forEach { Text(it,fontSize=11.sp) }
                Text("${recipe.analysisMetadata.fallbackReason}",fontSize=10.sp)
                asset.importance?.let { Image(it,"Mapa de importância local 8×8; não saliência humana",Modifier.size(150.dp)) }
                Text("Mapa: contraste + saturação + bordas; não reconhecimento semântico",fontSize=10.sp)
                asset.features?.let { f ->
                    Row { f.palette.filter { it.coverage>.001 }.take(12).forEach { c -> Box(Modifier.size(18.dp).background(Color(c.color))) } }
                    Text("Grade ${f.policy.gridSize}×${f.policy.gridSize}; luminância baixa <${f.policy.lowLuminance}, alta ≥${f.policy.highLuminance}",fontSize=10.sp)
                    Text(f.palette.filter { it.coverage>.001 }.take(6).joinToString("\n") { c ->
                        "Hue ${"%.0f".format(c.hue)}°; área ${"%.2f".format(c.coverage*100)}%; centro ${"%.2f, %.2f".format(c.x,c.y)}; contraste/canvas ${"%.2f".format(contrast(c.color,recipe.canvasColor))}:1"
                    },fontSize=10.sp)
                }
                Text("SEEK DEMONSTRATIVO",fontSize=11.sp)
                Row { listOf(0f,.25f,.5f,1f).forEach { p -> TextButton(onClick={player=player.copy(progress=p)}) { Text("${(p*100).toInt()}%",fontSize=11.sp) } } }
                Slider(player.progress,{player=player.copy(progress=it)})
                TextButton(onClick={player=player.copy(lyrics=!player.lyrics)}) { Text("LETRAS demo / voltar") }
                TextButton(onClick={largeFont=!largeFont}) { Text("Fonte: ${if(largeFont) "150%" else "100%"}") }
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(transitions,{transitions=it}); Text("Transição suave",fontSize=11.sp) }
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(reduceMotion,{reduceMotion=it}); Text("Reduzir movimento",fontSize=11.sp) }
                Text("PLAYER_MICRO_INTERACTIONS_V1",fontSize=11.sp)
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(microEnabled,{microEnabled=it}); Text("Microinterações",fontSize=11.sp) }
                Text("$audioNotice\n${labAudio?.source ?: "Carregue WAV para medir RMS ou use a fixture PCM identificada."}",fontSize=11.sp)
                labAudio?.let { audio -> Text("${audio.name}\nEnvelope: ${audio.envelope.size} janelas; ${"%.2f".format(audio.envelope.durationMillis/1000)} s; análise ${"%.2f".format(audio.analysisMs)} ms\nPrévia visual silenciosa; play pausa/retoma, seek reposiciona o envelope.",fontSize=10.sp) }
                Button(onClick={
                    SwingUtilities.invokeLater {
                        val dialog=FileDialog(null as Frame?,"Analisar energia de WAV local (prévia silenciosa)",FileDialog.LOAD)
                        dialog.isVisible=true
                        val file=dialog.file?.let { File(dialog.directory,it) }; dialog.dispose()
                        if(file!=null)scope.launch {
                            audioNotice="Analisando PCM fora da UI…"
                            val result=withContext(Dispatchers.IO) { runCatching { DesktopAudioEnergy.load(file) } }
                            result.onSuccess { labAudio=it; player=player.copy(progress=0f); audioNotice="RMS medido de WAV local" }
                                .onFailure { audioNotice="Falha no WAV: ${it.message}" }
                        }
                    }
                }) { Text("Carregar áudio WAV") }
                TextButton(onClick={scope.launch {
                    labAudio=withContext(Dispatchers.Default) { DesktopAudioEnergy.fixture() }
                    player=player.copy(progress=0f); audioNotice="Fixture PCM — não é áudio do álbum"
                }}) { Text("Iniciar PCM de teste (8 s)") }
                TextButton(onClick={labAudio=null; audioNotice="Sem fonte de áudio — play em repouso"}) { Text("Remover fonte de áudio") }
                Text("DEPURAÇÃO: ${debug.name}",fontSize=11.sp)
                DebugView.entries.forEach { mode -> TextButton(onClick={debug=mode}) { Text(mode.name) } }
                Button(onClick={
                    val file=File("build/exports/recipe-${selected+1}.json").apply { parentFile.mkdirs() }
                    file.writeText(recipeJson.encodeToString(recipe)); notice="Receita exportada: ${file.absolutePath}"
                }) { Text("Exportar receita JSON") }
                recipe.lights.forEachIndexed { i,l -> Text("Luz $i: centro ${l.x}, ${l.y}; raios ${l.radiusX}, ${l.radiusY}; intensidade ${l.intensity}",fontSize=11.sp) }
                Text("Aprovação visual: PENDENTE\nAnálise da capa completa; nenhuma regra por artista. A capa oficial pode diferir da arte estilizada no mockup. Nenhuma equivalência 1:1 declarada.",color=Color(0xffd7bd88),fontSize=12.sp)
            }
        }
        Text(notice.ifBlank { "Simulação local • sem áudio/conta/rede • captura usa pixels do renderer Compose em execução" },fontSize=10.sp,color=Color(0xff9aabc1),maxLines=2)
    }
}

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

fun main(args: Array<String>) = application {
    Window(onCloseRequest=::exitApplication,title="NØNSTOP Art Lab v8 · Fase 1A · SIMULAÇÃO",
        state=rememberWindowState(width=1280.dp,height=900.dp)) {
        MaterialTheme(colorScheme=darkColorScheme(background=Color(0xff080d17),surface=Color(0xff111a29))) {
            Surface { ArtLab(args.contains("--capture-suite")) }
        }
    }
}

@Composable private fun ArtLab(captureSuite:Boolean) {
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
    var notice by remember { mutableStateOf("") }
    val layers=rememberGraphicsLayer()
    val viewports=listOf(864 to 1536,941 to 1672,1080 to 2340,720 to 1280)
    val (vw,vh)=viewports[viewport]
    val album=albums[selected]
    val recipe=remember(asset.hash) { baselineRecipe(asset.hash) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val file=File("build/exports/catalog-manifest.json").apply { parentFile.mkdirs() }
            file.writeText(recipeJson.encodeToString(catalogManifest(assets,validationCatalog)))
        }
    }
    LaunchedEffect(album) {
        loading=true
        asset=CoverAsset(message="Carregando…")
        asset=withContext(Dispatchers.IO) { assets.load(album) }
        loading=false
    }
    fun select(index:Int) { selected=(index+albums.size)%albums.size; player=player.copy(lyrics=false); playerOpen=true }
    suspend fun capture(name:String) {
        val bitmap=layers.toImageBitmap()
        val file=File("build/captures/$name.png").apply { parentFile.mkdirs() }
        withContext(Dispatchers.IO) { org.jetbrains.skia.Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { file.writeBytes(it.bytes) }
        } }
        notice="Captura real do layer Compose: ${file.absolutePath}"
    }
    LaunchedEffect(captureSuite) {
        if(captureSuite) {
            delay(1800)
            for(i in validationCatalog.indices) {
                viewport=if(i==0) 1 else 0
                select(i)
                delay(900)
                while(loading) delay(100)
                delay(250)
                val dimensions=viewports[viewport]
                capture("catalog-${(i+1).toString().padStart(2,'0')}-${dimensions.first}x${dimensions.second}")
            }
            select(0)
            viewport=1
            delay(800)
            for(p in listOf(0f,.25f,.5f,1f)) { player=player.copy(progress=p); delay(300); capture("progress-${(p*100).toInt()}") }
            player=player.copy(lyrics=true); delay(400); capture("lyrics-demo")
            select(1); viewport=0; delay(700)
            player=player.copy(lyrics=true); delay(400); capture("lyrics-long-title-demo")
            player=player.copy(lyrics=false); largeFont=true; delay(400); capture("font-150-864x1536-long-title")
            select(0); largeFont=false; delay(700)
            player=player.copy(lyrics=false); viewport=2; delay(500); capture("viewport-1080x2340")
            largeFont=true; delay(500); capture("font-150-1080x2340")
            largeFont=false; viewport=0; player=PlayerState()
            notice="SUITE CONCLUÍDA — capturas em labs/art-lab/build/captures. Aprovação visual pendente."
            println("ART_LAB_CAPTURE_SUITE_COMPLETE")
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("NØNSTOP / ART LAB v8",fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=19.sp)
            Text("FASE 1A · CONTROLES SIMULADOS",color=Color(0xffadbcd0),fontSize=11.sp)
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
        Text("IA indisponível — fallback • baseline estático, sem features, classificação ou receitas cinematográficas",color=Color(0xffd7bd88),fontSize=12.sp)
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
                                LaunchedEffect(item) { thumbnail=withContext(Dispatchers.IO) { assets.load(item).cover } }
                                Box(Modifier.size(105.dp).background(Color(0xff080d16)),contentAlignment=Alignment.Center) {
                                    if(thumbnail!=null) Image(thumbnail!!,item.album,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                                    else Text("CAPA\nAUSENTE",fontSize=11.sp,color=Color(0xff8c9bb1))
                                }
                                Text(item.artist,fontSize=11.sp,maxLines=1)
                                Text(item.album,fontSize=11.sp,minLines=2,maxLines=2)
                                Text(if(item.status==ArtworkStatus.MISSING) "missing" else if(item.status==ArtworkStatus.LOCAL) "local" else "recorte de referência",fontSize=9.sp,color=Color(0xffd7bd88))
                                Text("Score: não calculado",fontSize=9.sp,color=Color(0xff97a7bd))
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
                    TextButton(onClick={nativePixels=!nativePixels}) { Text(if(nativePixels) "100% pixels" else "Ajustar à janela") }
                    TextButton(onClick={scope.launch { capture("manual-${selected+1}-${vw}x$vh") }}) { Text("Capturar") }
                }
                BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff030509))) {
                    val density=LocalDensity.current
                    val availableWidth=with(density) { maxWidth.toPx() }
                    val availableHeight=with(density) { maxHeight.toPx() }
                    val scale=if(nativePixels) 1f else minOf(availableWidth/vw,availableHeight/vh).coerceAtMost(1f)
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),contentAlignment=Alignment.TopCenter) {
                        CompositionLocalProvider(LocalDensity provides Density(1f,if(largeFont) 1.5f else 1f)) {
                            Layout(content={
                                V8Renderer(album,asset.cover,asset.texture,recipe,player,{player=it},{select(selected-1)},{select(selected+1)},
                                    {playerOpen=false; galleryVisible=true},{diagnostics=!diagnostics},font,debug,if(largeFont)1.5f else 1f,
                                    Modifier.fillMaxSize().drawWithContent {
                                        layers.record { this@drawWithContent.drawContent() }
                                        drawLayer(layers)
                                    })
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
                Text("IA: nenhum modelo\nFeatures: não calculadas\nTop-3 / confiança: indisponíveis\nReceita: PHASE_1A_STATIC_BASELINE\nCache de textura: ${if(asset.cacheHit) "hit" else "miss"}\nDecode + textura (wall): ${asset.preparationMs?.let { "%.2f ms".format(it) } ?: "não medido"}\nHash: ${asset.hash}",fontSize=11.sp)
                Text("SEEK DEMONSTRATIVO",fontSize=11.sp)
                Row { listOf(0f,.25f,.5f,1f).forEach { p -> TextButton(onClick={player=player.copy(progress=p)}) { Text("${(p*100).toInt()}%",fontSize=11.sp) } } }
                Slider(player.progress,{player=player.copy(progress=it)})
                TextButton(onClick={player=player.copy(lyrics=!player.lyrics)}) { Text("LETRAS demo / voltar") }
                TextButton(onClick={largeFont=!largeFont}) { Text("Fonte: ${if(largeFont) "150%" else "100%"}") }
                Text("Animação: não implementada nesta fase.\nRedução de movimento: ativa (render estático).",fontSize=11.sp)
                Text("DEPURAÇÃO: ${debug.name}",fontSize=11.sp)
                DebugView.entries.forEach { mode -> TextButton(onClick={debug=mode}) { Text(mode.name) } }
                Button(onClick={
                    val file=File("build/exports/recipe-${selected+1}.json").apply { parentFile.mkdirs() }
                    file.writeText(recipeJson.encodeToString(recipe)); notice="Receita exportada: ${file.absolutePath}"
                }) { Text("Exportar receita JSON") }
                recipe.lights.forEachIndexed { i,l -> Text("Luz $i: centro ${l.x}, ${l.y}; raios ${l.radiusX}, ${l.radiusY}; intensidade ${l.intensity}",fontSize=11.sp) }
                Text("Aprovação visual: PENDENTE\nBaseline sem direção cromática inferida. Recortes retêm cantos e textura dos mockups. Nenhuma equivalência 1:1 declarada.",color=Color(0xffd7bd88),fontSize=12.sp)
            }
        }
        Text(notice.ifBlank { "Simulação local • sem áudio/conta/rede • captura usa pixels do renderer Compose em execução" },fontSize=10.sp,color=Color(0xff9aabc1),maxLines=2)
    }
}

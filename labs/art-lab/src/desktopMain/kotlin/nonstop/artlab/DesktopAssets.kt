package nonstop.artlab

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import nonstop.artlab.catalog.*
import nonstop.artlab.direction.*
import nonstop.artlab.features.*
import nonstop.artlab.renderer.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.*

@Serializable data class AnalysisArtifact(val version:String,val features:ImageFeatures,val recipe:ArtRecipe,val textureHashes:List<String> = emptyList())
data class PhaseTimings(val decodeMs:Double=0.0,val featuresMs:Double=0.0,val directionMs:Double=0.0,
    val textureMs:Double=0.0,val totalMs:Double=0.0,val cpuMs:Double?=null,val generationMs:Double=0.0)
enum class CacheOrigin { MISS, MEMORY, DISK }
data class CoverAsset(val cover:ImageBitmap?=null,val texture:AtmosphereBitmaps?=null,val importance:ImageBitmap?=null,
    val hash:String="missing",val message:String="Capa indisponível",val preparationMs:Double?=null,
    val cacheHit:Boolean=false,val cacheOrigin:CacheOrigin=CacheOrigin.MISS,
    val recipe:ArtRecipe=baselineRecipe("missing"),val features:ImageFeatures?=null,val timings:PhaseTimings=PhaseTimings()) {
    /** Known retained RGBA pixels only; excludes JVM/native overhead and transient allocations. */
    val retainedRasterBytes:Long get() = (cover?.let { it.width.toLong()*it.height*4 } ?: 0)+
        (if(texture!=null)5L*192*342*4 else 0)+(importance?.let { it.width.toLong()*it.height*4 } ?: 0)
}

/** Disk cache identity is content + policy + pipeline version, never catalog identity. */
class DesktopAssets(private val cacheDirectory:File=File("build/art-cache"),
    private val pipelineVersion:String=ClassicalDirector.VERSION,private val policy:FeaturePolicy=FeaturePolicy(),
    private val memoryLimit:Int=3) {
    init { require(memoryLimit in 1..12) }
    private val cache=linkedMapOf<String,CoverAsset>()
    var analysisCount=0; private set
    @Synchronized fun load(album:Album,shouldContinue:()->Boolean={true}):CoverAsset = runCatching {
        if(!shouldContinue())return CoverAsset(message="Preparação substituída por outra seleção")
        val start=System.nanoTime()
        val path=album.resource ?: return CoverAsset()
        val bytes=if(album.status==ArtworkStatus.LOCAL) {
            val file=File(path); require(file.length()<=20*1024*1024) { "Arquivo excede 20 MiB" }; file.readBytes()
        } else javaClass.getResourceAsStream("/$path")?.use { it.readBytes() } ?: error("Recurso ausente: $path")
        val hash=sha256(bytes)
        if(album.status==ArtworkStatus.OFFICIAL_COVER) require(hash==album.sha256) { "Checksum da capa oficial diverge do manifesto" }
        val key=sha256((hash+pipelineVersion+recipeJson.encodeToString(policy)).encodeToByteArray())
        cache.remove(key)?.let { cache[key]=it; return it.copy(cacheHit=true,cacheOrigin=CacheOrigin.MEMORY,
            preparationMs=(System.nanoTime()-start)/1e6,timings=PhaseTimings(totalMs=(System.nanoTime()-start)/1e6)) }
        val bean=ManagementFactory.getThreadMXBean()
        val cpuStart=if(bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled)bean.currentThreadCpuTime else null
        var mark=System.nanoTime()
        val decoded=decode(bytes)
        if(!shouldContinue())return CoverAsset(message="Preparação substituída por outra seleção")
        val decodeMs=(System.nanoTime()-mark)/1e6
        val directory=File(cacheDirectory,key)
        val artifactFile=File(directory,"analysis.json")
        val textureFiles=listOf("deep","diffuse","accents","directional","composite").map { File(directory,"$it.png") }
        var generationMs=0.0
        val persisted=runCatching {
            val a=recipeJson.decodeFromString<AnalysisArtifact>(artifactFile.readText())
            require(a.version==pipelineVersion && a.recipe.sourceImageHash==hash && a.recipe.isValid() && a.features.isValid())
            require(a.textureHashes.size==textureFiles.size && textureFiles.zip(a.textureHashes).all { (file,digest) -> sha256(file.readBytes())==digest })
            require(textureFiles.all { it.isFile && ImageIO.read(it)?.let { image -> image.width==192 && image.height==342 }==true })
            a
        }.getOrNull()
        var featuresMs=0.0; var directionMs=0.0
        val artifact=persisted ?: run {
            mark=System.nanoTime()
            val small=resize(decoded,256)
            val pixels=small.getRGB(0,0,small.width,small.height,null,0,small.width)
            val features=ClassicalFeatureExtractor(policy).analyze(PixelImage(small.width,small.height,pixels))
            featuresMs=(System.nanoTime()-mark)/1e6; analysisCount++
            mark=System.nanoTime()
            val recipe=ClassicalDirector.direct(features,hash)
            directionMs=(System.nanoTime()-mark)/1e6
            AnalysisArtifact(pipelineVersion,features,recipe)
        }
        mark=System.nanoTime()
        if(!shouldContinue())return CoverAsset(message="Preparação substituída por outra seleção")
        val textures=if(persisted!=null) textureFiles.map { ImageIO.read(it) ?: error("Máscara de cache inválida") }
            else {
                val generationStart=System.nanoTime()
                val layers=AtmospherePrecomputer.render(artifact.recipe)
                val pixels=listOf(layers.deep,layers.diffuse,layers.accents,layers.directional,compositeAtmosphere(layers))
                generationMs=(System.nanoTime()-generationStart)/1e6
                pixels.map(::buffered)
            }
        val importance=importanceImage(artifact.features)
        if(persisted==null) {
            directory.mkdirs()
            textures.zip(textureFiles).forEach { (image,file) -> ImageIO.write(image,"png",file) }
            artifactFile.writeText(recipeJson.encodeToString(artifact.copy(textureHashes=textureFiles.map { sha256(it.readBytes()) })))
        }
        val bitmaps=textures.map { it.toComposeImageBitmap() }
        val coverBitmap=decoded.toComposeImageBitmap()
        val importanceBitmap=importance.toComposeImageBitmap()
        // Include native bitmap preparation in the reported wall/CPU cost, before publishing
        // the ready asset. Procedural generation remains its own independent measurement.
        val textureMs=(System.nanoTime()-mark)/1e6
        val totalMs=(System.nanoTime()-start)/1e6
        val cpuMs=cpuStart?.let { (bean.currentThreadCpuTime-it)/1e6 }
        CoverAsset(coverBitmap,AtmosphereBitmaps(bitmaps[0],bitmaps[1],bitmaps[2],bitmaps[3],bitmaps[4]),importanceBitmap,hash,
            "Análise clássica da capa completa; sem modelo neural",totalMs,persisted!=null,
            if(persisted!=null) CacheOrigin.DISK else CacheOrigin.MISS,artifact.recipe,artifact.features,
            PhaseTimings(decodeMs,featuresMs,directionMs,textureMs,totalMs,cpuMs,generationMs)).also {
                cache[key]=it
                while(cache.size>memoryLimit)cache.remove(cache.keys.first())
            }
    }.getOrElse { CoverAsset(message="Falha de decode/análise — fallback: ${it.message}") }

    /** Gallery thumbnails never run direction/precomputation or populate the full asset cache. */
    fun thumbnail(album:Album):ImageBitmap? = runCatching {
        val path=album.resource ?: return null
        val bytes=if(album.status==ArtworkStatus.LOCAL) {
            val file=File(path); require(file.length()<=20*1024*1024); file.readBytes()
        } else javaClass.getResourceAsStream("/$path")?.use { it.readBytes() } ?: return null
        if(album.status==ArtworkStatus.OFFICIAL_COVER) require(sha256(bytes)==album.sha256)
        resize(decode(bytes,128),128).toComposeImageBitmap()
    }.getOrNull()

    private fun decode(bytes:ByteArray,maxEdge:Int=1600):BufferedImage = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
        val readers=ImageIO.getImageReaders(stream); require(readers.hasNext()) { "PNG/JPEG inválido" }
        val reader=readers.next()
        try {
            require(reader.formatName.lowercase() in listOf("png","jpeg","jpg")) { "Use PNG ou JPEG" }
            reader.input=stream
            val w=reader.getWidth(0); val h=reader.getHeight(0)
            require(w>0 && h>0 && w.toLong()*h<=40_000_000L) { "Imagem maior que 40 MP ou dimensões inválidas" }
            val param=reader.defaultReadParam
            val sub=((max(w,h)+maxEdge-1)/maxEdge).coerceAtLeast(1)
            param.setSourceSubsampling(sub,sub,0,0)
            reader.read(0,param)
        } finally { reader.dispose() }
    }
    private fun resize(image:BufferedImage,maxEdge:Int):BufferedImage {
        val scale=min(1.0,maxEdge.toDouble()/max(image.width,image.height))
        val result=BufferedImage(max(1,(image.width*scale).roundToInt()),max(1,(image.height*scale).roundToInt()),BufferedImage.TYPE_INT_RGB)
        result.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image,0,0,result.width,result.height,null); dispose()
        }
        return result
    }
    private fun buffered(p:PixelImage) = BufferedImage(p.width,p.height,BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0,0,p.width,p.height,p.argb,0,p.width)
    }
    private fun importanceImage(features:ImageFeatures):BufferedImage {
        val size=features.policy.gridSize
        return BufferedImage(size,size,BufferedImage.TYPE_INT_RGB).apply {
            features.grid.forEachIndexed { i,c -> setRGB(i%size,i/size,fromHsv(220*(1-c.importance),.85,.2+.8*c.importance).toInt()) }
        }
    }
}
fun sha256(bytes:ByteArray):String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
@Serializable data class CatalogManifestEntry(val artist:String,val album:String,val status:String,val sourceKind:ArtworkStatus,
    val source:String?,val sha256:String?,val sourceReferenceSha256:String?)
fun catalogManifest(assets:DesktopAssets,albums:List<Album>) = albums.map { album ->
    val asset=if(album.resource!=null)assets.load(album) else CoverAsset()
    CatalogManifestEntry(album.artist,album.album,if(asset.cover==null)"missing" else "available",album.status,
        album.source,asset.hash.takeUnless { it=="missing" },if(album.status==ArtworkStatus.REFERENCE_EXTRACT)album.sha256 else null)
}

/** Display selection never participates in feature extraction or cache identity. */
enum class ComparisonMode { FIDELIDADE, PRODUCAO }
fun comparisonCover(album:Album,asset:CoverAsset,mode:ComparisonMode):ImageBitmap? {
    if(mode==ComparisonMode.PRODUCAO || album.status!=ArtworkStatus.OFFICIAL_COVER)return asset.cover
    val filename=album.resource?.substringAfterLast('/')?.substringBeforeLast('.') ?: return asset.cover
    return DesktopAssets::class.java.getResourceAsStream("/$filename.png")?.use {
        org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
    } ?: asset.cover
}

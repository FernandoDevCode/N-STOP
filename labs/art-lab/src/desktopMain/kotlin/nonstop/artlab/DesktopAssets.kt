package nonstop.artlab

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import nonstop.artlab.catalog.*
import nonstop.artlab.direction.*
import nonstop.artlab.features.*
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

@Serializable data class AnalysisArtifact(val version:String,val features:ImageFeatures,val recipe:ArtRecipe)
data class PhaseTimings(val decodeMs:Double=0.0,val featuresMs:Double=0.0,val directionMs:Double=0.0,
    val textureMs:Double=0.0,val totalMs:Double=0.0,val cpuMs:Double?=null)
enum class CacheOrigin { MISS, MEMORY, DISK }
data class CoverAsset(val cover:ImageBitmap?=null,val texture:ImageBitmap?=null,val importance:ImageBitmap?=null,
    val hash:String="missing",val message:String="Capa indisponível",val preparationMs:Double?=null,
    val cacheHit:Boolean=false,val cacheOrigin:CacheOrigin=CacheOrigin.MISS,
    val recipe:ArtRecipe=baselineRecipe("missing"),val features:ImageFeatures?=null,val timings:PhaseTimings=PhaseTimings())

/** Disk cache identity is content + policy + pipeline version, never catalog identity. */
class DesktopAssets(private val cacheDirectory:File=File("build/art-cache"),
    private val pipelineVersion:String=ClassicalDirector.VERSION,private val policy:FeaturePolicy=FeaturePolicy()) {
    private val cache=mutableMapOf<String,CoverAsset>()
    var analysisCount=0; private set
    @Synchronized fun load(album:Album):CoverAsset = runCatching {
        val start=System.nanoTime()
        val path=album.resource ?: return CoverAsset()
        val bytes=if(album.status==ArtworkStatus.LOCAL) {
            val file=File(path); require(file.length()<=20*1024*1024) { "Arquivo excede 20 MiB" }; file.readBytes()
        } else javaClass.getResourceAsStream("/$path")?.use { it.readBytes() } ?: error("Recurso ausente: $path")
        val hash=sha256(bytes)
        if(album.status==ArtworkStatus.OFFICIAL_COVER) require(hash==album.sha256) { "Checksum da capa oficial diverge do manifesto" }
        val key=sha256((hash+pipelineVersion+recipeJson.encodeToString(policy)).encodeToByteArray())
        cache[key]?.let { return it.copy(cacheHit=true,cacheOrigin=CacheOrigin.MEMORY,
            preparationMs=(System.nanoTime()-start)/1e6,timings=PhaseTimings(totalMs=(System.nanoTime()-start)/1e6)) }
        val bean=ManagementFactory.getThreadMXBean()
        val cpuStart=if(bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled)bean.currentThreadCpuTime else null
        var mark=System.nanoTime()
        val decoded=decode(bytes)
        val decodeMs=(System.nanoTime()-mark)/1e6
        val directory=File(cacheDirectory,key)
        val artifactFile=File(directory,"analysis.json")
        val textureFile=File(directory,"atmosphere.png")
        val persisted=runCatching {
            val a=recipeJson.decodeFromString<AnalysisArtifact>(artifactFile.readText())
            require(a.version==pipelineVersion && a.recipe.sourceImageHash==hash && a.recipe.isValid() && a.features.isValid())
            require(textureFile.isFile && ImageIO.read(textureFile)!=null)
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
        val texture=if(persisted!=null) ImageIO.read(textureFile) ?: error("Textura de cache inválida")
            else blurFullImage(resize(decoded,192),artifact.recipe.artBlurRadiusDp)
        val importance=importanceImage(artifact.features)
        if(persisted==null) {
            directory.mkdirs()
            ImageIO.write(texture,"png",textureFile)
            artifactFile.writeText(recipeJson.encodeToString(artifact))
        }
        val textureMs=(System.nanoTime()-mark)/1e6
        val totalMs=(System.nanoTime()-start)/1e6
        val cpuMs=cpuStart?.let { (bean.currentThreadCpuTime-it)/1e6 }
        CoverAsset(decoded.toComposeImageBitmap(),texture.toComposeImageBitmap(),importance.toComposeImageBitmap(),hash,
            "Análise clássica da capa completa; sem modelo neural",totalMs,persisted!=null,
            if(persisted!=null) CacheOrigin.DISK else CacheOrigin.MISS,artifact.recipe,artifact.features,
            PhaseTimings(decodeMs,featuresMs,directionMs,textureMs,totalMs,cpuMs)).also { cache[key]=it }
    }.getOrElse { CoverAsset(message="Falha de decode/análise — fallback: ${it.message}") }

    private fun decode(bytes:ByteArray):BufferedImage = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
        val readers=ImageIO.getImageReaders(stream); require(readers.hasNext()) { "PNG/JPEG inválido" }
        val reader=readers.next()
        try {
            require(reader.formatName.lowercase() in listOf("png","jpeg","jpg")) { "Use PNG ou JPEG" }
            reader.input=stream
            val w=reader.getWidth(0); val h=reader.getHeight(0)
            require(w>0 && h>0 && w.toLong()*h<=40_000_000L) { "Imagem maior que 40 MP ou dimensões inválidas" }
            val param=reader.defaultReadParam
            val sub=((max(w,h)+1599)/1600).coerceAtLeast(1)
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
    /** Clamped separable Gaussian: all edges and corners survive; no center crop at any step. */
    internal fun blurFullImage(image:BufferedImage,radiusDp:Float):BufferedImage {
        val w=image.width; val h=image.height
        val sigma=(radiusDp/432.0*max(w,h)).coerceAtLeast(.5)
        val radius=ceil(sigma*3).toInt().coerceAtMost(48)
        val weights=DoubleArray(radius*2+1) { exp(-((it-radius).toDouble().pow(2))/(2*sigma*sigma)) }
        val sum=weights.sum(); weights.indices.forEach { weights[it]/=sum }
        var source=image.getRGB(0,0,w,h,null,0,w)
        for(horizontal in listOf(true,false)) {
            val target=IntArray(source.size)
            for(y in 0 until h) for(x in 0 until w) {
                var r=0.0; var g=0.0; var b=0.0
                for(k in -radius..radius) {
                    val xx=if(horizontal)(x+k).coerceIn(0,w-1) else x
                    val yy=if(horizontal)y else (y+k).coerceIn(0,h-1)
                    val c=source[yy*w+xx]; val weight=weights[k+radius]
                    r+=((c shr 16)and 255)*weight; g+=((c shr 8)and 255)*weight; b+=(c and 255)*weight
                }
                target[y*w+x]=(0xff000000L or (r.roundToInt().toLong() shl 16) or (g.roundToInt().toLong() shl 8) or b.roundToInt().toLong()).toInt()
            }
            source=target
        }
        return BufferedImage(w,h,BufferedImage.TYPE_INT_RGB).apply { setRGB(0,0,w,h,source,0,w) }
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

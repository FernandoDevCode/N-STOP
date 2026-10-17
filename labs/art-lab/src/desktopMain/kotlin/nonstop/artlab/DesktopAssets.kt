package nonstop.artlab

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import nonstop.artlab.catalog.Album
import nonstop.artlab.catalog.ArtworkStatus
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.system.measureNanoTime
import kotlinx.serialization.Serializable

data class CoverAsset(val cover: ImageBitmap? = null, val texture: ImageBitmap? = null,
    val hash: String = "missing", val message: String = "Capa indisponível", val preparationMs: Double? = null, val cacheHit: Boolean = false)

class DesktopAssets {
    private val cache = ConcurrentHashMap<String,CoverAsset>()
    @Synchronized fun load(album: Album): CoverAsset = runCatching {
        val path = album.resource ?: return CoverAsset()
        val bytes = if (album.status == nonstop.artlab.catalog.ArtworkStatus.LOCAL) {
            val f=File(path); require(f.length() <= 20*1024*1024) { "Arquivo excede 20 MiB" }; f.readBytes()
        } else javaClass.getResourceAsStream("/$path")?.use { it.readBytes() } ?: error("Recurso ausente: $path")
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        cache[hash]?.let { return it.copy(cacheHit=true) }
        var image: ImageBitmap? = null
        var atmosphere: ImageBitmap? = null
        val ns=measureNanoTime {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
                val readers=ImageIO.getImageReaders(stream)
                require(readers.hasNext()) { "PNG/JPEG inválido ou formato não suportado" }
                val reader=readers.next()
                try {
                    require(reader.formatName.lowercase() in listOf("png","jpeg","jpg")) { "Use PNG ou JPEG" }
                    reader.input=stream
                    val w=reader.getWidth(0); val h=reader.getHeight(0)
                    require(w>0 && h>0 && w.toLong()*h<=40_000_000L) { "Dimensões inválidas ou maiores que 40 MP" }
                    val param=reader.defaultReadParam
                    val sub=(maxOf(w,h)/1024).coerceAtLeast(1)
                    param.setSourceSubsampling(sub,sub,0,0)
                    val decoded=reader.read(0,param)
                    image=decoded.toComposeImageBitmap()
                    // Bounded 96px texture, generated once per content hash; never per UI frame.
                    val small=BufferedImage(96,96,BufferedImage.TYPE_INT_RGB)
                    small.createGraphics().apply {
                        setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        drawImage(decoded,0,0,96,96,null); dispose()
                    }
                    val radius=12; val n=radius*2+1
                    val weights=FloatArray(n) { i -> kotlin.math.exp(-((i-radius)*(i-radius))/(2f*6f*6f)) }
                    val sum=weights.sum(); weights.indices.forEach { weights[it]/=sum }
                    var blurred=small
                    repeat(3) {
                        blurred=ConvolveOp(Kernel(n,1,weights),ConvolveOp.EDGE_NO_OP,null).filter(blurred,null)
                        blurred=ConvolveOp(Kernel(1,n,weights),ConvolveOp.EDGE_NO_OP,null).filter(blurred,null)
                    }
                    // Remove untouched convolution boundary before stretching as a background texture.
                    atmosphere=blurred.getSubimage(24,24,48,48).toComposeImageBitmap()
                } finally { reader.dispose() }
            }
        }
        CoverAsset(image,atmosphere,hash,"Textura estática de referência/local; sem análise ou inferência",ns/1_000_000.0).also { cache[hash]=it }
    }.getOrElse { CoverAsset(message="Falha de decode — fallback: ${it.message}") }
}

@Serializable
data class CatalogManifestEntry(val artist:String,val album:String,val status:String,val sourceKind:ArtworkStatus,
    val source:String?,val sha256:String?,val sourceReferenceSha256:String?)

fun catalogManifest(assets:DesktopAssets,albums:List<Album>) = albums.map { album ->
    val asset=assets.load(album)
    CatalogManifestEntry(album.artist,album.album,if(asset.cover==null) "missing" else "available",album.status,
        album.source,asset.hash.takeUnless { it=="missing" },album.sha256)
}

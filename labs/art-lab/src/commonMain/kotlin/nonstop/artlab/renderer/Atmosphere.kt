package nonstop.artlab.renderer

import androidx.compose.ui.graphics.ImageBitmap
import nonstop.artlab.direction.*
import nonstop.artlab.features.*
import kotlin.math.*

data class AtmosphereBitmaps(val deep:ImageBitmap,val diffuse:ImageBitmap,val accents:ImageBitmap,val directional:ImageBitmap,val composite:ImageBitmap)
data class AtmospherePixels(val deep:PixelImage,val diffuse:PixelImage,val accents:PixelImage,val directional:PixelImage)

fun compositeAtmosphere(p:AtmospherePixels):PixelImage {
    val pixels=p.deep.argb.copyOf()
    for(layer in listOf(p.diffuse,p.accents,p.directional)) for(i in pixels.indices) {
        val foreground=layer.argb[i]; val alpha=(foreground ushr 24)/255.0
        pixels[i]=mix(pixels[i].toLong() and 0xffffffffL,foreground.toLong() and 0xffffffffL,alpha).toInt()
    }
    return PixelImage(p.deep.width,p.deep.height,pixels)
}

/** No source bitmap enters this boundary. Only bounded low-dimensional recipe primitives survive.
 * Four masks are prepared once; frames only sample cached textures. No semantic reconstruction. */
object AtmospherePrecomputer {
    fun render(recipe:ArtRecipe,width:Int=192,height:Int=342):AtmospherePixels {
        require(width in 32..512 && height in 32..1024 && recipe.isValid())
        val deep=IntArray(width*height); val diffuse=IntArray(deep.size); val accents=IntArray(deep.size); val direction=IntArray(deep.size)
        fun field(x:Double,y:Double,lights:List<Light>,warp:Double):DoubleArray {
            val result=DoubleArray(4)
            lights.forEach { l ->
                val xx=(x-l.x)/l.radiusX+warp*sin(y*13+x*4)
                val yy=(y-l.y)/l.radiusY+warp*cos(x*11-y*3)
                val distance=sqrt(xx*xx+yy*yy)
                val a=(1-distance).coerceAtLeast(0.0).pow(l.falloff.toDouble())*l.intensity
                val c=rgb(l.color)
                val out=a+result[3]*(1-a)
                if(out>0) for(i in 0..2) result[i]=(c[i]*a+result[i]*result[3]*(1-a))/out
                result[3]=out
            }
            return result
        }
        fun pack(c:DoubleArray):Int = ((argb(c[0],c[1],c[2]) and 0xffffff) or ((c[3].coerceIn(0.0,1.0)*255).roundToInt().toLong() shl 24)).toInt()
        val base=rgb(recipe.canvasColor)
        val p=recipe.procedural
        val primitives=ProceduralPrimitives(p.seed)
        val ambient=rgb(recipe.colors.environmental)
        val accentColor=rgb(recipe.colors.accent)
        for(y in 0 until height) for(x in 0 until width) {
            val px=(x+.5)/width; val py=(y+.5)/height; val i=y*width+x
            val mass=field(px,py,recipe.environment.regions,recipe.environment.warp.toDouble())
            val turbulence=primitives.fractal(px*p.noiseScale,py*p.noiseScale,p.octaves,p.roughness.toDouble())
            val mask=primitives.organic(px,py,turbulence)
            val quiet=primitives.quietArea(py)
            val fold=(1-p.organic)+p.organic*(.35+.9*turbulence)*mask
            val fog=primitives.mist(turbulence,mask)*p.mist*quiet
            val curve=primitives.curve(px,py)*p.curves*quiet
            val depth=1-p.vignette*primitives.vignette(px,py)
            val noise=primitives.dither(x,y)*recipe.environment.grain
            val dc=DoubleArray(3) { ((base[it]*(1-mass[3])+mass[it]*mass[3]*fold+ambient[it]*fog+
                accentColor[it]*curve)*depth+noise).coerceIn(0.0,1.0) }
            deep[i]=argb(dc[0],dc[1],dc[2]).toInt()
            diffuse[i]=pack(field(px,py,recipe.lights,recipe.environment.warp.toDouble()*.5))
            accents[i]=pack(field(px,py,recipe.accents,0.0))
            val beam=DoubleArray(4)
            recipe.directionalDetails.forEach { d ->
                val dx=px-d.x; val dy=(py-d.y)*height/width
                val angle=d.angle*PI/180; val along=dx*cos(angle)+dy*sin(angle); val across=-dx*sin(angle)+dy*cos(angle)
                val end=(1-abs(along)/(d.length*.5)).coerceIn(0.0,1.0)
                val side=(1-abs(across)/(d.width*.5)).coerceIn(0.0,1.0)
                val a=end.pow(.7)*side.pow(2)*d.intensity*d.coherence
                val c=rgb(d.color); val out=a+beam[3]*(1-a)
                if(out>0) for(k in 0..2) beam[k]=(c[k]*a+beam[k]*beam[3]*(1-a))/out
                beam[3]=out
            }
            direction[i]=pack(beam)
        }
        return AtmospherePixels(PixelImage(width,height,deep),PixelImage(width,height,diffuse),PixelImage(width,height,accents),PixelImage(width,height,direction))
    }
}

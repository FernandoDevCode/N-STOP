package nonstop.artlab.renderer

import kotlin.math.*

/** Bounded scalar fields. Used only by precomputation, never by Compose frames. */
class ProceduralPrimitives(private val seed:Long) {
    private fun hash(x:Int,y:Int):Double {
        var n=(x.toLong()*374761393L+y.toLong()*668265263L+seed) and 0xffffffffL
        n=((n xor (n ushr 13))*1274126177L) and 0xffffffffL
        return ((n xor (n ushr 16)) and 0xffffffffL)/4294967295.0
    }
    fun noise(x:Double,y:Double):Double {
        val ix=floor(x).toInt(); val iy=floor(y).toInt()
        val tx=x-ix; val ty=y-iy
        val u=tx*tx*(3-2*tx); val v=ty*ty*(3-2*ty)
        val a=hash(ix,iy)*(1-u)+hash(ix+1,iy)*u
        val b=hash(ix,iy+1)*(1-u)+hash(ix+1,iy+1)*u
        return a*(1-v)+b*v
    }
    fun fractal(x:Double,y:Double,octaves:Int,roughness:Double):Double {
        var result=0.0; var weight=1.0; var total=0.0; var frequency=1.0
        repeat(octaves) { result+=noise(x*frequency+it*7.13,y*frequency-it*3.71)*weight
            total+=weight; weight*=roughness; frequency*=2.03 }
        return result/total
    }
    fun organic(x:Double,y:Double,turbulence:Double):Double {
        val dx=x-.5+.18*(turbulence-.5); val dy=(y-.38)*.85
        return (1-sqrt(dx*dx+dy*dy)*1.5).coerceIn(0.0,1.0).pow(1.6)
    }
    fun mist(turbulence:Double,mask:Double):Double = ((turbulence-.34)*1.7).coerceIn(0.0,1.0).pow(2)*mask
    fun curve(x:Double,y:Double):Double {
        val phase=(seed%1024)/1024.0*2*PI
        val center=.23+.07*sin(y*5+phase)+.08*y
        val side=(1-abs(x-center)/.055).coerceIn(0.0,1.0).pow(3)
        return side*sin(PI*y).coerceAtLeast(0.0).pow(2)
    }
    fun vignette(x:Double,y:Double):Double = ((x-.5).pow(2)*1.4+(y-.43).pow(2)*.8).coerceIn(0.0,1.0)
    fun quietArea(y:Double):Double = 1-.65*exp(-((y-.76)/.18).pow(2))
    fun dither(x:Int,y:Int):Double = hash(x,y)-.5
}

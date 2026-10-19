package nonstop.artlab.validation

import nonstop.artlab.features.*
import nonstop.artlab.renderer.ProceduralPrimitives
import kotlin.math.*

/** Synthetic stress fixtures, never used as album art or as evidence of object recognition. */
object SyntheticImages {
    val names=listOf("black","white","intense-red","monochrome","multicolor","smooth-gradient","bright-photo-surrogate","low-contrast")
    fun image(name:String,size:Int=128):PixelImage {
        require(name in names && size in 32..256)
        val noise=ProceduralPrimitives(751)
        return PixelImage(size,size,IntArray(size*size) { i ->
            val x=(i%size+.5)/size; val y=(i/size+.5)/size
            when(name) {
                "black" -> 0xff000000L
                "white" -> 0xffffffffL
                "intense-red" -> 0xffff0000L
                "monochrome" -> { val v=.16+.65*(.5+.5*sin(x*11)*cos(y*13)); argb(v,v,v) }
                "multicolor" -> fromHsv(((x*6).toInt()*60+(y*5).toInt()*33).toDouble(),.82,.35+.6*y)
                "smooth-gradient" -> mix(0xff214f68,0xffe4c7a0,(x+y)/2)
                // Photographic luminance/texture proxy, explicitly synthetic; actual photos also
                // enter via the official album catalog. Does not test semantic recognition.
                "bright-photo-surrogate" -> {
                    val v=(.76+.18*noise.fractal(x*6,y*6,4,.5)-.16*exp(-((x-.4).pow(2)+(y-.6).pow(2))*24)).coerceIn(0.0,1.0)
                    argb(v,v*.96,v*.87)
                }
                else -> { val v=.39+.025*sin(x*9)*cos(y*7); argb(v,v*1.01,v*.99) }
            }.toInt()
        })
    }
}

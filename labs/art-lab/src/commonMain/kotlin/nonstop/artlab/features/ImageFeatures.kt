package nonstop.artlab.features

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable data class FeaturePolicy(val version: Int = 2, val gridSize: Int = 8,
    val lowLuminance: Double = .035, val highLuminance: Double = .32,
    val saturatedThreshold: Double = .5, val accentMinValue: Double = .18, val negativeMaxValue: Double = .12,
    val accentMinLuminance:Double=.004) {
    fun isValid() = version==2 && gridSize in 4..16 &&
        listOf(lowLuminance,highLuminance,saturatedThreshold,accentMinValue,negativeMaxValue,accentMinLuminance).all { it.isFinite() && it in 0.0..1.0 } && lowLuminance<highLuminance
}
data class PixelImage(val width: Int, val height: Int, val argb: IntArray) {
    init { require(width > 0 && height > 0 && argb.size == width * height) }
}
@Serializable data class LabColor(val l: Double, val a: Double, val b: Double) {
    fun distance(other: LabColor) = sqrt((l-other.l).pow(2)+(a-other.a).pow(2)+(b-other.b).pow(2))
}
@Serializable data class ColorCluster(val color: Long, val coverage: Double, val x: Double, val y: Double,
    val hue: Double, val saturation: Double, val value: Double, val lab: LabColor,
    val spreadX: Double, val spreadY: Double)
@Serializable data class SpatialCell(val x: Double, val y: Double, val color: Long,
    val luminance: Double, val saturation: Double, val edgeDensity: Double,
    val contrast: Double, val importance: Double, val darkFraction: Double)
@Serializable data class ImageFeatures(
    val schemaVersion: Int = 2, val policy: FeaturePolicy, val width: Int, val height: Int,
    val lowFraction: Double, val midFraction: Double, val highFraction: Double,
    val meanLuminance: Double, val meanSaturation: Double, val saturatedFraction: Double,
    val saturatedX: Double, val saturatedY: Double, val luminanceX: Double, val luminanceY: Double,
    val coolCoverage: Double, val edgeDensity: Double, val complexity: Double,
    val grid: List<SpatialCell>, val palette: List<ColorCluster>,
) {
    fun summary() = linkedMapOf("lowFraction" to lowFraction,"midFraction" to midFraction,
        "highFraction" to highFraction,"meanLuminance" to meanLuminance,"meanSaturation" to meanSaturation,
        "saturatedFraction" to saturatedFraction,"saturatedX" to saturatedX,"saturatedY" to saturatedY,
        "coolCoverage" to coolCoverage,"edgeDensity" to edgeDensity,"complexity" to complexity)
    fun isValid() = schemaVersion==2 && policy.isValid() && width in 1..1024 && height in 1..1024 && grid.size == policy.gridSize*policy.gridSize &&
        listOf(luminanceX,luminanceY).all { it.isFinite() && it in 0.0..1.0 } &&
        summary().values.all { it.isFinite() && it in 0.0..1.0 } &&
        abs(lowFraction+midFraction+highFraction-1.0)<.00001 && palette.isNotEmpty() &&
        palette.all { p -> listOf(p.coverage,p.x,p.y,p.saturation,p.value,p.spreadX,p.spreadY,p.lab.l).all { it.isFinite() && it in 0.0..1.0 } && p.hue.isFinite() && p.lab.a.isFinite() && p.lab.b.isFinite() } &&
        grid.all { c -> listOf(c.x,c.y,c.luminance,c.saturation,c.edgeDensity,c.contrast,c.importance,c.darkFraction).all { it.isFinite() && it in 0.0..1.0 } }
}

fun rgb(color: Long) = doubleArrayOf(((color shr 16) and 255)/255.0,((color shr 8) and 255)/255.0,(color and 255)/255.0)
fun argb(r: Double,g: Double,b: Double): Long = 0xff000000L or
    ((r.coerceIn(0.0,1.0)*255).roundToInt().toLong() shl 16) or
    ((g.coerceIn(0.0,1.0)*255).roundToInt().toLong() shl 8) or (b.coerceIn(0.0,1.0)*255).roundToInt().toLong()
fun linear(v: Double) = if(v<=.04045) v/12.92 else ((v+.055)/1.055).pow(2.4)
fun luminance(color: Long): Double { val c=rgb(color); return .2126*linear(c[0])+.7152*linear(c[1])+.0722*linear(c[2]) }
fun contrast(a: Long,b: Long) = (max(luminance(a),luminance(b))+.05)/(min(luminance(a),luminance(b))+.05)
fun mix(a: Long,b: Long,t: Double): Long { val x=rgb(a); val y=rgb(b); val f=t.coerceIn(0.0,1.0); return argb(x[0]*(1-f)+y[0]*f,x[1]*(1-f)+y[1]*f,x[2]*(1-f)+y[2]*f) }
fun hsv(color: Long): DoubleArray {
    val c=rgb(color); val hi=c.max(); val lo=c.min(); val d=hi-lo
    val h=when { d<1e-8 -> 0.0; hi==c[0] -> 60*((c[1]-c[2])/d%6); hi==c[1] -> 60*((c[2]-c[0])/d+2); else -> 60*((c[0]-c[1])/d+4) }
    return doubleArrayOf((h+360)%360,if(hi>0)d/hi else 0.0,hi)
}
fun fromHsv(hue: Double,s: Double,v: Double): Long {
    val h=((hue%360)+360)%360/60; val c=v*s; val x=c*(1-abs(h%2-1)); val m=v-c
    val p=when(h.toInt()) { 0 -> doubleArrayOf(c,x,0.0); 1 -> doubleArrayOf(x,c,0.0); 2 -> doubleArrayOf(0.0,c,x); 3 -> doubleArrayOf(0.0,x,c); 4 -> doubleArrayOf(x,0.0,c); else -> doubleArrayOf(c,0.0,x) }
    return argb(p[0]+m,p[1]+m,p[2]+m)
}
fun oklab(color: Long): LabColor {
    val c=rgb(color).map(::linear)
    val l=(.4122214708*c[0]+.5363325363*c[1]+.0514459929*c[2]).pow(1.0/3)
    val m=(.2119034982*c[0]+.6806995451*c[1]+.1073969566*c[2]).pow(1.0/3)
    val s=(.0883024619*c[0]+.2817188376*c[1]+.6299787005*c[2]).pow(1.0/3)
    return LabColor(.2104542553*l+.793617785*m-.0040720468*s,1.9779984951*l-2.428592205*m+.4505937099*s,.0259040371*l+.7827717662*m-.808675766*s)
}

interface FeatureExtractor { suspend fun extract(argb: IntArray,width: Int,height: Int): ImageFeatures }
class ClassicalFeatureExtractor(val policy: FeaturePolicy = FeaturePolicy()) : FeatureExtractor {
    override suspend fun extract(argb: IntArray,width: Int,height: Int) = analyze(PixelImage(width,height,argb))
    fun analyze(image: PixelImage): ImageFeatures {
        require(policy.isValid())
        val w=image.width; val h=image.height; val n=w*h; val grid=policy.gridSize
        val luma=DoubleArray(n) { luminance(image.argb[it].toLong() and 0xffffffffL) }
        val bins=Array(28) { Accumulator() }; val cells=Array(grid*grid) { Accumulator() }
        var dark=0; var bright=0; var saturated=0; var cold=0; var sx=0.0; var sy=0.0
        var lx=0.0; var ly=0.0; var totalL=0.0; var totalS=0.0; var edges=0.0
        for(y in 0 until h) for(x in 0 until w) {
            val i=y*w+x; val color=image.argb[i].toLong() and 0xffffffffL; val c=rgb(color); val hsv=hsv(color)
            val lum=luma[i]; val px=(x+.5)/w; val py=(y+.5)/h
            val low=lum<policy.lowLuminance
            if(low) dark++ else if(lum>=policy.highLuminance) bright++
            if(hsv[1]>=policy.saturatedThreshold && hsv[2]>=policy.accentMinValue && lum>=policy.accentMinLuminance) { saturated++; sx+=px; sy+=py }
            if(hsv[0] in 180.0..280.0 && hsv[1]>.28 && hsv[2]>.08) cold++
            var localContrast=0.0; var edge=0.0; var neighbors=0
            for(dy in -1..1) for(dx in -1..1) if((dx!=0||dy!=0) && x+dx in 0 until w && y+dy in 0 until h) {
                val j=(y+dy)*w+x+dx
                localContrast+=abs(lum-luma[j]); val other=rgb(image.argb[j].toLong() and 0xffffffffL)
                edge+=sqrt(c.indices.sumOf { (c[it]-other[it]).pow(2) }/3); neighbors++
            }
            localContrast=(localContrast/max(1,neighbors)*4).coerceIn(0.0,1.0)
            edge=(edge/max(1,neighbors)*3).coerceIn(0.0,1.0)
            val importance=(.32*localContrast+.38*hsv[1]*sqrt(lum)+.30*edge).coerceIn(0.0,1.0)
            val cell=cells[min(grid-1,y*grid/h)*grid+min(grid-1,x*grid/w)]
            cell.add(c,px,py,lum,hsv[1],edge,localContrast,importance,low)
            // Near-black JPEG noise must not dilute a small bright color sharing the same hue.
            val bin=if(hsv[2]<policy.negativeMaxValue)24 else if(hsv[1]<.2) 24+min(3,(hsv[2]*4).toInt()) else min(23,(hsv[0]/15).toInt())
            bins[bin].add(c,px,py,lum,hsv[1],edge,localContrast,importance,low)
            totalL+=lum; totalS+=hsv[1]; lx+=px*lum; ly+=py*lum; edges+=edge
        }
        val maxImportance=cells.maxOf { it.importance/max(1,it.count) }.coerceAtLeast(1e-8)
        val palette=bins.filter { it.count>0 }.map { a ->
            val color=a.color(); val hsv=hsv(color)
            ColorCluster(color,a.count.toDouble()/n,a.x/a.count,a.y/a.count,hsv[0],hsv[1],hsv[2],oklab(color),
                sqrt(max(0.0,a.x2/a.count-(a.x/a.count).pow(2))),sqrt(max(0.0,a.y2/a.count-(a.y/a.count).pow(2))))
        }.sortedByDescending { it.coverage }
        val spatial=cells.mapIndexed { i,a ->
            val count=max(1,a.count)
            SpatialCell((i%grid+.5)/grid,(i/grid+.5)/grid,a.color(),a.luma/count,a.saturation/count,a.edge/count,
                a.contrast/count,(a.importance/count/maxImportance).coerceIn(0.0,1.0),a.dark.toDouble()/count)
        }
        val low=dark.toDouble()/n; val high=bright.toDouble()/n
        val variation=sqrt(spatial.sumOf { (it.luminance-totalL/n).pow(2) }/spatial.size)
        return ImageFeatures(policy=policy,width=w,height=h,lowFraction=low,midFraction=(n-dark-bright).toDouble()/n,highFraction=high,
            meanLuminance=totalL/n,meanSaturation=totalS/n,saturatedFraction=saturated.toDouble()/n,
            saturatedX=if(saturated>0)sx/saturated else .5,saturatedY=if(saturated>0)sy/saturated else .5,
            luminanceX=if(totalL>1e-8)lx/totalL else .5,luminanceY=if(totalL>1e-8)ly/totalL else .5,
            coolCoverage=cold.toDouble()/n,edgeDensity=edges/n,complexity=(edges/n*2+variation*2).coerceIn(0.0,1.0),grid=spatial,palette=palette)
    }
    private class Accumulator {
        var count=0; var r=0.0; var g=0.0; var b=0.0; var x=0.0; var y=0.0; var x2=0.0; var y2=0.0
        var luma=0.0; var saturation=0.0; var edge=0.0; var contrast=0.0; var importance=0.0; var dark=0
        fun color()=argb(r/max(1,count),g/max(1,count),b/max(1,count))
        fun add(c:DoubleArray,px:Double,py:Double,l:Double,s:Double,e:Double,ct:Double,imp:Double,low:Boolean) {
            count++; r+=c[0]; g+=c[1]; b+=c[2]; x+=px; y+=py; x2+=px*px; y2+=py*py
            luma+=l; saturation+=s; edge+=e; contrast+=ct; importance+=imp; if(low)dark++
        }
    }
}

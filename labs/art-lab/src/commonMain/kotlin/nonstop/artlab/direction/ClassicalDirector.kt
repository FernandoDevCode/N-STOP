package nonstop.artlab.direction

import nonstop.artlab.features.*
import kotlin.math.*

/** Only pixels/features enter this boundary. Album metadata and known identities cannot enter. */
object ClassicalDirector {
    const val VERSION = "classical-1b-c3"
    fun direct(f: ImageFeatures, sourceHash: String): ArtRecipe {
        require(f.isValid())
        val chroma=f.palette.filter { it.saturation>.28 && it.value>.18 && it.coverage>.0008 }
        val distinct=mutableListOf<ColorCluster>()
        chroma.sortedByDescending { it.coverage }.forEach { c -> if(distinct.none { it.lab.distance(c.lab)<.065 }) distinct+=c }
        val diversity=(distinct.size/6.0).coerceIn(0.0,1.0)
        val sparse=(((f.lowFraction-.55)/.35).coerceIn(0.0,1.0)*
            (1-f.saturatedFraction/.35).coerceIn(0.0,1.0)*diversity).coerceIn(0.0,1.0)
        val cool=(f.coolCoverage*1.5).coerceIn(0.0,1.0)*(1-sparse)
        val dense=((1-f.lowFraction)*.7+f.complexity*.3).coerceIn(0.0,1.0)*(1-sparse)
        val dominant=chroma.maxByOrNull { it.coverage } ?: f.palette.first()
        val hue=dominant.hue
        val accent=fromHsv(hue-25*cool, (dominant.saturation*.72).coerceAtMost(.78), .96)
        val canvas=fromHsv(hue,dominant.saturation*.78,(.026+.055*(1-sparse)*cool).coerceIn(.02,.1))
        val scores=linkedMapOf(
            Archetype.PRISMATIC_ACCENTS to sparse.toFloat(), Archetype.COOL_DEPTH to cool.toFloat(),
            Archetype.CINEMATIC_DENSE to dense.toFloat(), Archetype.DARK_SYMBOLIC to (f.lowFraction*(1-diversity*.55)).toFloat(),
            Archetype.WARM_DIFFUSE to ((1-cool)*(1-f.lowFraction)*f.meanSaturation*.7).toFloat(),
            Archetype.MONOCHROME_EDITORIAL to ((1-f.meanSaturation)*(1-diversity)*.5).toFloat(),
            Archetype.MULTICOLOR_GRAPHIC to (diversity*(1-f.lowFraction)*(1-cool)).toFloat())
        val top=scores.entries.sortedByDescending { it.value }.take(2)
        val total=top.sumOf { it.value.toDouble() }.coerceAtLeast(.001)
        val treatments=top.associate { it.key to (it.value/total).toFloat() }
        val lights=mutableListOf<Light>()
        // Broad, independent regions retain both source position and regional hue/brightness.
        val selected=mutableListOf<SpatialCell>()
        val regions=if(cool>.5) f.grid.filter { hsv(it.color)[0] in 180.0..280.0 && hsv(it.color)[1]>.28 }.ifEmpty { f.grid } else f.grid
        regions.sortedByDescending { c -> c.importance*(.35+if(hsv(c.color)[0] in 180.0..280.0)cool else 1-cool) }.forEach { c ->
            if(selected.size<3 && selected.none { hypot(it.x-c.x,it.y-c.y)<.27 }) selected+=c
        }
        selected.forEach { c -> lights+=Light(c.x.toFloat(),c.y.toFloat(),(.42-.10*sparse).toFloat(),(.32-.12*sparse).toFloat(),
            c.color,((.17+.18*c.importance)*(1-sparse)).toFloat(),2f) }
        // Negative space + separated small chromatic clusters yields localized spectral reflections.
        fun angle(c:ColorCluster)=if(c.hue>330)c.hue-360 else c.hue
        val spectral=chroma.groupBy { ((it.hue+22.5)%360/45).toInt() }.values
            .map { family -> family.maxBy { it.coverage*it.value } }.sortedByDescending { it.coverage }.take(7).sortedBy(::angle)
        fun spread(count:Int):List<ColorCluster> = if(spectral.size<=count)spectral else
            (0 until count).map { spectral[(it.toDouble()*(spectral.size-1)/(count-1)).roundToInt()] }
        if(sparse>.08) {
            lights.clear()
            val white=f.palette.filter { it.saturation<.25 && it.value>.35 }.maxByOrNull { it.value*it.coverage }
            if(white!=null) lights+=Light(white.x.toFloat(),(white.y-.35).coerceIn(0.0,1.0).toFloat(),.42f,.29f,
                mix(white.color,0xffc9efff,.4),(.55*sparse).toFloat(),2f)
            spread(4).forEach { c ->
                lights+=Light(c.x.toFloat(),((c.y-.35)*1.6).coerceIn(0.0,1.0).toFloat(),(.18+c.spreadX).toFloat(),(.10+c.spreadY).toFloat(),
                    fromHsv(c.hue,c.saturation,.9),(.32*sparse).toFloat(),1.8f)
            }
            spread(3).forEach { c ->
                lights+=Light(c.x.toFloat(),(.75+c.y*.22).coerceIn(0.0,1.0).toFloat(),.33f,.21f,
                    fromHsv(c.hue,c.saturation,.75),(.15*sparse).toFloat(),2.2f)
            }
        }
        val ordered=spectral
        fun spectrum(): List<Stop> {
            if(ordered.size<2) return emptyList()
            val colors=ordered.map { fromHsv(it.hue,it.saturation.coerceAtLeast(.65),.95) }
            return colors.mapIndexed { i,c -> Stop(i.toFloat()/colors.size,c) }+Stop(1f,colors.first())
        }
        val spectralStops=if(sparse>.25 && ordered.size>=4) spectrum() else emptyList()
        val material=listOf(Stop(0f,mix(fromHsv(hue,.55,.075),accent,1-sparse)),
            Stop(1f,mix(fromHsv(hue,.65,.03),mix(accent,0xff000000,.12),1-sparse)))
        val white=0xfff8fbffL; val black=0xff041326L
        val ink=if(material.all { contrast(black,it.color)>=4.5 }) black else white
        val safeMaterial=if(material.all { contrast(ink,it.color)>=4.5 }) material else material.map { it.copy(color=mix(it.color,0xff000000,.45)) }
        val fill=if(spectralStops.isNotEmpty()) {
            val colors=ordered.map { fromHsv(it.hue,it.saturation.coerceAtLeast(.7),1.0) }
            val firstAngle=angle(ordered.first()); val range=(angle(ordered.last())-firstAngle).coerceAtLeast(1.0)
            colors.mapIndexed { i,c -> Stop(((angle(ordered[i])-firstAngle)/range*.46).toFloat(),c) }+Stop(1f,colors.last())
        } else listOf(Stop(0f,fromHsv(hue,dominant.saturation*.5,.96)),Stop(1f,accent))
        val control=ControlSurface(safeMaterial,0xffeffaff,
            accent,(.20+.30*(1-sparse)).toFloat(),(.12+.04*(1-sparse)).toFloat(),ink,
            rim=spectralStops,haloSpectrum=spectralStops,specularIntensity=(.06+.16*(1-sparse)).toFloat(),
            diameterRatio=(.1574+.013*sparse).toFloat())
        val favorite=if(spectralStops.isNotEmpty())fromHsv(ordered.first().hue,ordered.first().saturation,1.0) else accent
        val recipe=ArtRecipe(sourceImageHash=sourceHash,canvasColor=canvas,favoriteColor=favorite,
            scrimTop=(.38-.08*sparse).toFloat(),scrimBottom=(.60-.05*sparse).toFloat(),
            artTextureOpacity=(.40-.17*sparse).toFloat(),artBlurRadiusDp=(6+12*sparse).toFloat(),lights=lights,
            controlSurface=control,progress=ProgressStyle(fill,0xff303445,mix(accent,white,.35),mix(accent,white,.15)),
            lyrics=LyricsStyle(white,0xff9aacc3,listOf(.72f,1f,.52f,.36f,.24f),(mix(canvas,0xff000000,.6) and 0x00ffffffL) or 0x40000000L),
            archetypeScores=scores,analysisMetadata=AnalysisMetadata(DecisionSource.CLASSICAL_PIXELS,
                "Modelo neural ausente; direção clássica determinística. Scores são evidências heurísticas, não probabilidades.",
                f.summary()+mapOf("sparseSpectral" to sparse,"perceptualHueDiversity" to diversity),VERSION,
                listOf("Área negativa escura: ${f.lowFraction}","Cobertura fria: ${f.coolCoverage}",
                    "Pequenos sinais saturados: ${f.saturatedFraction}; centro ${f.saturatedX},${f.saturatedY}",
                    "Grupos cromáticos separados em OKLab: ${distinct.size}","Bordas: ${f.edgeDensity}; complexidade: ${f.complexity}"),treatments))
        check(recipe.isValid()) { "Classical director produced invalid recipe" }
        return recipe
    }
}

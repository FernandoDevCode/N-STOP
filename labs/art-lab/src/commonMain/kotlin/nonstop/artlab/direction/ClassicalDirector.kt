package nonstop.artlab.direction

import nonstop.artlab.features.*
import kotlin.math.*

/** Only pixels/features enter this boundary. Album metadata and known identities cannot enter. */
object ClassicalDirector {
    const val VERSION = "procedural-art-director-4.2"
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
        val accents=mutableListOf<Light>()
        val details=mutableListOf<DirectionalDetail>()
        val deep=mutableListOf<Light>()
        // Broad, independent regions retain both source position and regional hue/brightness.
        val selected=mutableListOf<SpatialCell>()
        val regions=if(cool>.5) f.grid.filter { hsv(it.color)[0] in 180.0..280.0 && hsv(it.color)[1]>.28 }.ifEmpty { f.grid } else f.grid
        regions.sortedByDescending { c -> c.importance*(.35+if(hsv(c.color)[0] in 180.0..280.0)cool else 1-cool) }.forEach { c ->
            if(selected.size<5 && selected.none { hypot(it.x-c.x,it.y-c.y)<.25 }) selected+=c
        }
        selected.forEach { c ->
            val ch=hsv(c.color)
            val regionalSaturation=max(ch[1],dominant.saturation*.85)
            deep+=Light(c.x.toFloat(),c.y.toFloat(),.62f,.44f,fromHsv(ch[0],regionalSaturation,.42),((.55+.3*c.importance)*(1-sparse)).toFloat(),1.6f)
            lights+=Light(c.x.toFloat(),c.y.toFloat(),(.32-.10*sparse).toFloat(),(.26-.12*sparse).toFloat(),
                fromHsv(ch[0],regionalSaturation,.72),((.13+.15*c.importance)*(1-sparse)).toFloat(),2.5f)
        }
        // Negative space + separated small chromatic clusters yields localized spectral reflections.
        fun angle(c:ColorCluster)=if(c.hue>330)c.hue-360 else c.hue
        val spectral=chroma.groupBy { ((it.hue+15)%360/30).toInt() }.values
            .map { family -> family.maxBy { it.coverage*it.value } }.sortedByDescending { it.coverage }.take(7).sortedBy(::angle)
        fun spread(count:Int):List<ColorCluster> = if(spectral.size<=count)spectral else
            (0 until count).map { spectral[(it.toDouble()*(spectral.size-1)/(count-1)).roundToInt()] }
        if(sparse>.08) {
            lights.clear()
            val white=f.palette.filter { it.saturation<.25 && it.value>.35 }.maxByOrNull { it.value*it.coverage }
            if(white!=null) {
                lights+=Light(white.x.toFloat(),(white.y-.35).coerceIn(0.0,1.0).toFloat(),.40f,.20f,
                    mix(white.color,0xffc9efff,.4),(.24*sparse).toFloat(),2f)
                // Edge peaks are axial, not a claim that a prism was detected.
                if(white.coherence>.5) details+=DirectionalDetail(white.x.toFloat(),(white.y-.33).coerceIn(0.0,1.0).toFloat(),white.orientation.toFloat(),1.5f,.17f,
                    mix(white.color,0xffb9dced,.5),(.34*sparse).toFloat(),white.coherence.toFloat())
            }
            spread(4).forEach { c ->
                accents+=Light(c.x.toFloat(),((c.y-.35)*1.6).coerceIn(0.0,1.0).toFloat(),(.17+c.spreadX).toFloat(),(.07+c.spreadY).toFloat(),
                    fromHsv(c.hue,c.saturation,.9),(.18*sparse).toFloat(),2.2f)
            }
            spectral.filter { it.coherence>.45 }.forEach { c ->
                details+=DirectionalDetail(c.x.toFloat(),((c.y-.35)*1.6).coerceIn(0.0,1.0).toFloat(),c.orientation.toFloat(),
                    (.5+2*c.spreadX).coerceAtMost(1.5).toFloat(),(.08+c.spreadY*.3).toFloat(),
                    fromHsv(c.hue,c.saturation,.95),(.50*sparse).toFloat(),c.coherence.toFloat())
            }
            spread(3).forEach { c ->
                accents+=Light(c.x.toFloat(),(.40+(c.y-.35)*3.5).coerceIn(0.0,1.0).toFloat(),.35f,.24f,
                    fromHsv(c.hue,c.saturation,.75),(.30*sparse).toFloat(),2.2f)
            }
        } else {
            // Conservative elongated reflections only when edge evidence is concentrated.
            selected.filter { it.coherence>.18 }.take(3).forEach { c ->
                details+=DirectionalDetail(c.x.toFloat(),c.y.toFloat(),c.orientation.toFloat(),1.3f,.10f,
                    fromHsv(hsv(c.color)[0],.65,.75),(.20*cool).toFloat(),c.coherence.toFloat())
            }
            selected.take(2).forEach { c -> accents+=Light(c.x.toFloat(),c.y.toFloat(),.14f,.09f,
                fromHsv(hsv(c.color)[0],.65,.9),(.07*cool).toFloat(),3f) }
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
        val rim=if(spectralStops.isNotEmpty())spectralStops else listOf(Stop(0f,mix(accent,white,.40)),Stop(1f,mix(accent,0xff000000,.16)))
        val control=ControlSurface(safeMaterial,
            accent,(.25+.10*(1-sparse)).toFloat(),(.14+.015*(1-sparse)).toFloat(),ink,
            rim=rim,haloSpectrum=spectralStops,
            diameterRatio=(.1574+.013*sparse).toFloat())
        val favorite=if(spectralStops.isNotEmpty())fromHsv(ordered.first().hue,ordered.first().saturation,1.0) else accent
        val recipe=ArtRecipe(sourceImageHash=sourceHash,canvasColor=canvas,favoriteColor=favorite,
            scrimTop=(.38-.08*sparse).toFloat(),scrimBottom=(.60-.05*sparse).toFloat(),
            environment=Environment(deep,(.012*(1-sparse)).toFloat(),(.12*(1-sparse)).toFloat()),
            lights=lights,accents=accents,directionalDetails=details,
            controlSurface=control,progress=ProgressStyle(fill,0xff303445,mix(accent,white,.35),mix(accent,white,.15)),
            lyrics=LyricsStyle(white,0xff9aacc3,listOf(.72f,1f,.52f,.36f,.24f),(mix(canvas,0xff000000,.6) and 0x00ffffffL) or 0x40000000L),
            archetypeScores=scores,analysisMetadata=AnalysisMetadata(DecisionSource.CLASSICAL_PIXELS,
                "Modelo neural ausente; direção clássica determinística. Scores são evidências heurísticas, não probabilidades.",
                f.summary()+mapOf("sparseSpectral" to sparse,"perceptualHueDiversity" to diversity,
                    "directionPeak" to (f.directions.firstOrNull()?.strength ?: 0.0)),VERSION,
                listOf("Área negativa escura: ${f.lowFraction}","Cobertura fria: ${f.coolCoverage}",
                    "Pequenos sinais saturados: ${f.saturatedFraction}; centro ${f.saturatedX},${f.saturatedY}",
                    "Grupos cromáticos separados em OKLab: ${distinct.size}","Bordas: ${f.edgeDensity}; complexidade: ${f.complexity}"),treatments))
        return FamilyDirector.refine(f,recipe).also { check(it.isValid()) { "Classical director produced invalid recipe" } }
    }
}

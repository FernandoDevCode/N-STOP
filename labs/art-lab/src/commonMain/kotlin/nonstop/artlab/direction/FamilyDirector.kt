package nonstop.artlab.direction

import nonstop.artlab.features.*
import kotlin.math.*

/** Measured visual evidence only. No identity, catalog index, genre or hash is read here. */
object FamilyDirector {
    fun refine(f:ImageFeatures,r:ArtRecipe):ArtRecipe {
        val diversity=r.analysisMetadata.measuredFeatures.getValue("perceptualHueDiversity")
        val sparse=r.analysisMetadata.measuredFeatures.getValue("sparseSpectral")
        val range=(f.palette.maxOf { it.lab.l }-f.palette.minOf { it.lab.l }).coerceIn(0.0,1.0)
        val spatial=sqrt(f.grid.sumOf { (it.luminance-f.meanLuminance).pow(2) }/f.grid.size).coerceIn(0.0,1.0)
        val detail=(f.edgeDensity*4).coerceIn(0.0,1.0)
        val mono=(1-diversity)*(1-f.meanSaturation*.65)
        val scores=linkedMapOf(
            ArtFamily.PRISMATIC to sparse,
            ArtFamily.DENSE_CHROMATIC to ((.40*f.saturatedFraction+.35*diversity+.25*detail)*(1-sparse)),
            ArtFamily.EDITORIAL_NATURAL to ((1-f.meanSaturation)*(.30+.50*detail+.20*(1-f.lowFraction))*(1-f.lowFraction*.5)),
            ArtFamily.DRAMATIC_CONTRAST to (1.45*range*(.30+.70*f.lowFraction)*(1-sparse)*(1-mono*.5)),
            ArtFamily.SELECTIVE_LUMINOUS to (1.20*sqrt(f.highFraction*4).coerceAtMost(1.0)*(1-f.highFraction)*range*(.85+spatial*2)*(1-sparse)*(1-.4*detail)),
            ArtFamily.MINIMAL_MONOCHROME to (mono*(.35+.65*(1-detail))*(1-range*.6) + (1-range)*.25))
            .mapValues { it.value.coerceIn(0.0,1.0) }
        val ordered=scores.entries.sortedByDescending { it.value }
        val inconclusive=ordered.first().value<.34 || (range<.12 && detail>.20 && f.meanSaturation<.16)
        val first=if(inconclusive)ArtFamily.NEUTRAL_FALLBACK else ordered.first().key
        // Mix a second compatible treatment only when it adds independent measurable evidence.
        val second=ordered.drop(1).firstOrNull { it.value>ordered.first().value*.72 && it.key!=ArtFamily.PRISMATIC &&
            !(first==ArtFamily.MINIMAL_MONOCHROME && it.key==ArtFamily.DENSE_CHROMATIC) }
        val weights=if(inconclusive || second==null) mapOf(first to 1f) else {
            val sum=ordered.first().value+second.value
            linkedMapOf(first to (ordered.first().value/sum).toFloat(),second.key to (second.value/sum).toFloat())
        }
        fun blend(values:Map<ArtFamily,Double>,fallback:Double) = weights.entries.sumOf { (values[it.key] ?: fallback)*it.value }.toFloat()
        // Environmental mass favors coverage and spatial spread; interaction favors contrast and
        // saturation; accent favors local separated evidence. None is simply the most frequent bin.
        val chroma=f.palette.filter { it.value>.18 && it.saturation>.20 && it.coverage>.0008 }
        val environmental=chroma.maxByOrNull { sqrt(it.coverage)*(it.spreadX+it.spreadY+.1) }
        val interaction=chroma.maxByOrNull { sqrt(it.coverage)*it.saturation*min(contrast(it.color,r.canvasColor),10.0) }
        val local=chroma.maxByOrNull { it.saturation*sqrt(it.value)*min(contrast(it.color,r.canvasColor),10.0)/(1+it.coverage*8) }
        val neutral=first in listOf(ArtFamily.NEUTRAL_FALLBACK,ArtFamily.MINIMAL_MONOCHROME) && f.meanSaturation<.2
        val roles=if(inconclusive) ColorRoles() else ColorRoles(
            environmental?.let { fromHsv(it.hue,it.saturation*.6,.22) } ?: 0xff17202b,
            if(neutral)0xffc5d0d8 else interaction?.let { fromHsv(it.hue,it.saturation*.70,.94) } ?: 0xffb8d4e8,
            if(neutral)0xffaab6c3 else local?.let { fromHsv(it.hue,it.saturation*.78,.9) } ?: 0xff9cb6d4)
        val material=when(first) {
            ArtFamily.PRISMATIC -> ControlMaterial.PRISMATIC
            ArtFamily.DENSE_CHROMATIC,ArtFamily.DRAMATIC_CONTRAST,ArtFamily.SELECTIVE_LUMINOUS -> ControlMaterial.MATTE_FILLED
            else -> ControlMaterial.EDITORIAL
        }
        val dark=fromHsv(hsv(roles.environmental)[0],if(neutral).08 else .35,.065)
        val prismBase=mix(r.canvasColor,0xff070b11,.65)
        val fill=when(material) {
            ControlMaterial.PRISMATIC -> listOf(Stop(0f,mix(prismBase,roles.environmental,.10)),Stop(1f,prismBase))
            ControlMaterial.MATTE_FILLED -> listOf(Stop(0f,roles.interaction),Stop(1f,mix(roles.interaction,0xff000000,.12)))
            ControlMaterial.EDITORIAL -> listOf(Stop(0f,mix(dark,roles.environmental,.28)),Stop(1f,dark))
        }
        val ink=if(fill.all { contrast(0xff050a10,it.color)>=4.5 })0xff050a10 else 0xfffafcff
        val safeFill=if(fill.all { contrast(ink,it.color)>=4.5 }) fill else fill.map { it.copy(color=mix(it.color,0xff000000,.55)) }
        val control=r.controlSurface.copy(fill=safeFill,ink=ink,material=material,
            haloColor=roles.interaction,haloIntensity=if(material==ControlMaterial.EDITORIAL).16f else .30f,
            haloSpectrum=if(material==ControlMaterial.PRISMATIC)r.controlSurface.haloSpectrum else emptyList(),
            rim=when(material) {
                ControlMaterial.PRISMATIC -> r.controlSurface.rim
                ControlMaterial.MATTE_FILLED -> emptyList()
                ControlMaterial.EDITORIAL -> listOf(Stop(0f,mix(roles.interaction,dark,.48)),Stop(1f,mix(roles.interaction,dark,.72)))
            })
        val params=ProceduralParameters(seed=proceduralSeed(r.sourceImageHash),
            noiseScale=blend(mapOf(ArtFamily.PRISMATIC to 2.2,ArtFamily.DENSE_CHROMATIC to 4.5,ArtFamily.EDITORIAL_NATURAL to 3.0),2.8),
            organic=blend(mapOf(ArtFamily.PRISMATIC to .06,ArtFamily.DENSE_CHROMATIC to .48,ArtFamily.EDITORIAL_NATURAL to .30),.16),
            mist=blend(mapOf(ArtFamily.SELECTIVE_LUMINOUS to .32,ArtFamily.DENSE_CHROMATIC to .20,ArtFamily.MINIMAL_MONOCHROME to .07),.12),
            curves=blend(mapOf(ArtFamily.PRISMATIC to .015,ArtFamily.EDITORIAL_NATURAL to .045,ArtFamily.MINIMAL_MONOCHROME to .012),.025),
            vignette=blend(mapOf(ArtFamily.DRAMATIC_CONTRAST to .48,ArtFamily.SELECTIVE_LUMINOUS to .32),.28))
        val base=if(inconclusive)baselineRecipe(r.sourceImageHash) else r
        return base.copy(family=first,familyWeights=weights,colors=roles,procedural=params,
            controlSurface=control,favoriteColor=roles.accent,
            progress=base.progress.copy(thumb=roles.interaction,halo=roles.interaction),
            analysisMetadata=r.analysisMetadata.copy(engineVersion=ClassicalDirector.VERSION,
                fallbackReason=if(inconclusive)"Evidência inconclusiva: composição neutra procedural. Sem IA." else "Direção por pixels; sem IA e sem reconhecimento semântico.",
                measuredFeatures=r.analysisMetadata.measuredFeatures+scores.mapKeys { "family.${it.key}" }+
                    mapOf("luminanceRange" to range,"spatialLuminanceDeviation" to spatial),
                fiveSignals=listOf("Famílias: $weights; detalhe=$detail; faixa tonal=$range",
                    "Área negativa=${f.lowFraction}; sinais isolados=$sparse",
                    "Diversidade perceptual=$diversity; cobertura saturada=${f.saturatedFraction}",
                    "Cor ambiental: cobertura × dispersão; interação: contraste × saturação",
                    "Acento: contraste local; variação espacial de luminância=$spatial")))
    }
}

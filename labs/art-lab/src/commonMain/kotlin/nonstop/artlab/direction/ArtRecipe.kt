package nonstop.artlab.direction

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nonstop.artlab.features.contrast

@Serializable enum class Archetype { PREMIUM_FALLBACK, DARK_SYMBOLIC, PRISMATIC_ACCENTS, CINEMATIC_DENSE, COOL_DEPTH, WARM_DIFFUSE, MONOCHROME_EDITORIAL, MULTICOLOR_GRAPHIC }
@Serializable enum class DecisionSource { PHASE_1A_STATIC_BASELINE, CLASSICAL_PIXELS }
@Serializable enum class ArtFamily { PRISMATIC, DENSE_CHROMATIC, EDITORIAL_NATURAL, DRAMATIC_CONTRAST, SELECTIVE_LUMINOUS, MINIMAL_MONOCHROME, NEUTRAL_FALLBACK }
@Serializable enum class ControlMaterial { PRISMATIC, MATTE_FILLED, EDITORIAL }
@Serializable data class ColorRoles(val environmental:Long=0xff101c2c, val interaction:Long=0xffb8d4e8, val accent:Long=0xff9cb6d4)
@Serializable data class ProceduralParameters(val seed:Long=0, val noiseScale:Float=3.2f, val octaves:Int=4,
    val roughness:Float=.5f, val organic:Float=.22f, val mist:Float=.12f, val curves:Float=.025f, val vignette:Float=.28f)
@Serializable data class Light(val x: Float, val y: Float, val radiusX: Float, val radiusY: Float,
    val color: Long, val intensity: Float, val falloff: Float = 2f)
@Serializable data class Stop(val position: Float, val color: Long)
@Serializable data class Environment(val regions: List<Light> = emptyList(), val grain:Float=.018f, val warp:Float=.08f)
@Serializable data class DirectionalDetail(val x:Float,val y:Float,val angle:Float,val length:Float,
    val width:Float,val color:Long,val intensity:Float,val coherence:Float)
@Serializable data class ControlSurface(val fill: List<Stop>,
    val haloColor: Long, val haloIntensity: Float, val haloRadius: Float, val ink: Long,
    val rim: List<Stop> = emptyList(), val haloSpectrum: List<Stop> = emptyList(),
    val spectrumRotation: Float = 135f, val diameterRatio:Float=.1574f,
    val material:ControlMaterial=ControlMaterial.EDITORIAL)
@Serializable data class ProgressStyle(val fill: List<Stop>, val track: Long, val thumb: Long, val halo: Long)
@Serializable data class LyricsStyle(val active: Long, val future: Long, val alphaProgression: List<Float>, val surface: Long)
@Serializable data class AnalysisMetadata(val source: DecisionSource, val fallbackReason: String,
    val measuredFeatures: Map<String, Double> = emptyMap(), val engineVersion: String = "baseline",
    val fiveSignals: List<String> = emptyList(), val treatmentWeights: Map<Archetype,Float> = emptyMap())
@Serializable data class ArtRecipe(
    val schemaVersion: Int = 4,
    val sourceImageHash: String,
    val family:ArtFamily=ArtFamily.NEUTRAL_FALLBACK,
    val familyWeights:Map<ArtFamily,Float> = mapOf(ArtFamily.NEUTRAL_FALLBACK to 1f),
    val colors:ColorRoles=ColorRoles(),
    val procedural:ProceduralParameters=ProceduralParameters(),
    val modelVersion: String = "none",
    val confidence: Float? = null,
    val archetypeScores: Map<Archetype, Float> = emptyMap(),
    val canvasColor: Long = 0xff05080f,
    val favoriteColor:Long = 0xffa6dbfa,
    val scrimTop: Float = .40f,
    val scrimBottom: Float = .78f,
    val environment: Environment = Environment(),
    val accents: List<Light> = emptyList(),
    val directionalDetails: List<DirectionalDetail> = emptyList(),
    val lights: List<Light> = listOf(
        Light(.28f,.12f,.65f,.43f,0xffb7d7eb,.12f),
        Light(.76f,.78f,.53f,.35f,0xff677da8,.13f)),
    val controlSurface: ControlSurface = ControlSurface(listOf(Stop(0f,0xff35485e),Stop(1f,0xff152234)),
        0xff8ac5eb,.3f,.14f,0xfff8fbff),
    val progress: ProgressStyle = ProgressStyle(listOf(Stop(0f,0xff8ba4d0),Stop(1f,0xffb9e6fa)),0xff343948,0xffa6dbfa,0xffa6dbfa),
    val lyrics: LyricsStyle = LyricsStyle(0xfff5f7fc,0xff919aaa,listOf(1f,.65f,.4f),0x88060910),
    val analysisMetadata: AnalysisMetadata = AnalysisMetadata(DecisionSource.PHASE_1A_STATIC_BASELINE,
        "Capa indisponível ou análise inválida — fallback estável, sem modelo neural."),
)

val recipeJson = Json { prettyPrint = true; encodeDefaults = true }
/** Hash affects stochastic placement/cache only, never the treatment decision. FNV-1a, fixed 32 bits. */
fun proceduralSeed(hash:String):Long {
    var seed=2166136261L
    for(c in "$hash:${ClassicalDirector.VERSION}") seed=((seed xor c.code.toLong())*16777619L) and 0xffffffffL
    return seed
}
fun baselineRecipe(hash: String) = ArtRecipe(sourceImageHash = hash,procedural=ProceduralParameters(seed=proceduralSeed(hash)))
fun ArtRecipe.isValid(): Boolean {
    fun unit(v: Float) = v.isFinite() && v in 0f..1f
    fun stops(s: List<Stop>) = s.size in 2..8 && s.all { unit(it.position) && it.color in 0..0xffffffff } &&
        s.first().position == 0f && s.last().position == 1f && s.zipWithNext().all { (a,b) -> a.position < b.position }
    fun color(v: Long) = v in 0..0xffffffffL
    fun light(l:Light) = unit(l.x) && unit(l.y) && unit(l.intensity) && color(l.color) &&
        l.radiusX.isFinite() && l.radiusX in .01f..2f && l.radiusY.isFinite() && l.radiusY in .01f..2f &&
        l.falloff.isFinite() && l.falloff in 1f..8f
    return schemaVersion == 4 && sourceImageHash.isNotBlank() && color(canvasColor) && color(favoriteColor) &&
        listOf(colors.environmental,colors.interaction,colors.accent).all { color(it) && (it ushr 24)==255L } &&
        familyWeights.size in 1..2 && familyWeights.containsKey(family) && familyWeights.values.all(::unit) &&
        kotlin.math.abs(familyWeights.values.sum()-1f)<.001f &&
        procedural.seed in 0..0xffffffffL && procedural.noiseScale.isFinite() && procedural.noiseScale in 1f..8f &&
        procedural.octaves in 1..5 && listOf(procedural.roughness,procedural.organic,procedural.mist,procedural.curves,procedural.vignette).all(::unit) &&
        listOf(scrimTop,scrimBottom,environment.grain,environment.warp).all(::unit) &&
        environment.regions.size<=8 && environment.regions.all(::light) && accents.size<=8 && accents.all(::light) &&
        directionalDetails.size<=16 && directionalDetails.all { d ->
            listOf(d.x,d.y,d.intensity,d.coherence).all(::unit) && color(d.color) && d.angle.isFinite() && d.angle in -180f..180f &&
            d.length.isFinite() && d.length in .02f..2f && d.width.isFinite() && d.width in .002f..1f } &&
        (confidence == null || unit(confidence)) && archetypeScores.values.all(::unit) && lights.size <= 8 &&
        lights.all { unit(it.x) && unit(it.y) && unit(it.intensity) && color(it.color) &&
            it.radiusX.isFinite() && it.radiusX in .01f..2f && it.radiusY.isFinite() && it.radiusY in .01f..2f &&
            it.falloff.isFinite() && it.falloff in 1f..8f } &&
        stops(progress.fill) && stops(controlSurface.fill) && unit(controlSurface.haloIntensity) &&
        controlSurface.haloRadius.isFinite() && controlSurface.haloRadius in .01f..1f &&
        listOf(progress.track,progress.thumb,progress.halo,controlSurface.haloColor,
            controlSurface.ink,lyrics.active,lyrics.future,lyrics.surface).all(::color) &&
        controlSurface.spectrumRotation.isFinite() &&
        controlSurface.diameterRatio.isFinite() && controlSurface.diameterRatio in .12f..0.22f &&
        controlSurface.spectrumRotation in -360f..360f &&
        (controlSurface.rim.isEmpty() || stops(controlSurface.rim)) &&
        (controlSurface.haloSpectrum.isEmpty() || stops(controlSurface.haloSpectrum)) &&
        lyrics.alphaProgression.size in 1..8 && lyrics.alphaProgression.all(::unit) &&
        analysisMetadata.measuredFeatures.values.all { it.isFinite() } &&
        analysisMetadata.treatmentWeights.size<=2 && analysisMetadata.treatmentWeights.values.all(::unit) &&
        listOf(canvasColor,controlSurface.ink,lyrics.active,controlSurface.haloColor,
            progress.track,progress.thumb,progress.halo).all { (it ushr 24)==255L } &&
        controlSurface.fill.all { (it.color ushr 24)==255L && contrast(controlSurface.ink,it.color)>=4.5 } && contrast(lyrics.active,canvasColor)>=4.5
}
fun safeRecipe(candidate: ArtRecipe): ArtRecipe = if (candidate.isValid()) candidate else baselineRecipe(candidate.sourceImageHash.ifBlank { "missing" })
fun loadRecipe(json: String): ArtRecipe = runCatching { safeRecipe(recipeJson.decodeFromString<ArtRecipe>(json)) }
    .getOrElse { baselineRecipe("invalid-json") }

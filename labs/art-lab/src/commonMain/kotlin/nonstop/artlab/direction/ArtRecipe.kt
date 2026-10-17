package nonstop.artlab.direction

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class Archetype { PREMIUM_FALLBACK }
@Serializable enum class DecisionSource { PHASE_1A_STATIC_BASELINE }
@Serializable data class Light(val x: Float, val y: Float, val radiusX: Float, val radiusY: Float,
    val color: Long, val intensity: Float, val falloff: Float = 2f, val motionAmplitude: Float = 0f)
@Serializable data class Stop(val position: Float, val color: Long)
@Serializable data class ControlSurface(val fill: List<Stop>, val specular: Long,
    val haloColor: Long, val haloIntensity: Float, val haloRadius: Float, val ink: Long)
@Serializable data class ProgressStyle(val fill: List<Stop>, val track: Long, val thumb: Long, val halo: Long)
@Serializable data class LyricsStyle(val active: Long, val future: Long, val alphaProgression: List<Float>, val surface: Long)
@Serializable data class AnalysisMetadata(val source: DecisionSource, val fallbackReason: String,
    val measuredFeatures: Map<String, Double> = emptyMap())
@Serializable data class ArtRecipe(
    val schemaVersion: Int = 1,
    val sourceImageHash: String,
    val modelVersion: String = "none",
    val confidence: Float? = null,
    val archetypeScores: Map<Archetype, Float> = emptyMap(),
    val canvasColor: Long = 0xff05080f,
    val scrimTop: Float = .40f,
    val scrimBottom: Float = .78f,
    val artTextureOpacity: Float = .55f,
    val artBlurRadiusDp: Float = 18f,
    val lights: List<Light> = listOf(
        Light(.28f,.12f,.65f,.43f,0xffb7d7eb,.12f),
        Light(.76f,.78f,.53f,.35f,0xff677da8,.13f)),
    val controlSurface: ControlSurface = ControlSurface(listOf(Stop(0f,0xff35485e),Stop(1f,0xff152234)),
        0xffa8c4d9,0xff8ac5eb,.3f,.14f,0xfff8fbff),
    val progress: ProgressStyle = ProgressStyle(listOf(Stop(0f,0xff8ba4d0),Stop(1f,0xffb9e6fa)),0xff343948,0xffa6dbfa,0xffa6dbfa),
    val lyrics: LyricsStyle = LyricsStyle(0xfff5f7fc,0xff919aaa,listOf(1f,.65f,.4f),0x88060910),
    val analysisMetadata: AnalysisMetadata = AnalysisMetadata(DecisionSource.PHASE_1A_STATIC_BASELINE,
        "Fase 1A: baseline estático neutro. Features, classificação e receitas por capa são Fase 1B."),
)

val recipeJson = Json { prettyPrint = true; encodeDefaults = true }
fun baselineRecipe(hash: String) = ArtRecipe(sourceImageHash = hash)
fun ArtRecipe.isValid(): Boolean {
    fun unit(v: Float) = v.isFinite() && v in 0f..1f
    fun stops(s: List<Stop>) = s.size in 2..8 && s.all { unit(it.position) && it.color in 0..0xffffffff } &&
        s.first().position == 0f && s.last().position == 1f && s.zipWithNext().all { (a,b) -> a.position < b.position }
    return schemaVersion == 1 && sourceImageHash.isNotBlank() && canvasColor in 0..0xffffffff &&
        listOf(scrimTop,scrimBottom,artTextureOpacity).all(::unit) &&
        artBlurRadiusDp.isFinite() && artBlurRadiusDp in 0f..64f &&
        (confidence == null || unit(confidence)) && archetypeScores.values.all(::unit) && lights.size <= 8 &&
        lights.all { unit(it.x) && unit(it.y) && unit(it.intensity) &&
            it.radiusX.isFinite() && it.radiusX in .01f..2f && it.radiusY.isFinite() && it.radiusY in .01f..2f &&
            it.falloff.isFinite() && it.falloff in 1f..8f && unit(it.motionAmplitude) } &&
        stops(progress.fill) && stops(controlSurface.fill) && unit(controlSurface.haloIntensity) &&
        controlSurface.haloRadius.isFinite() && controlSurface.haloRadius in .01f..1f &&
        lyrics.alphaProgression.all(::unit) && controlSurface.ink != controlSurface.fill.first().color
}
fun safeRecipe(candidate: ArtRecipe): ArtRecipe = if (candidate.isValid()) candidate else baselineRecipe(candidate.sourceImageHash.ifBlank { "missing" })
fun loadRecipe(json: String): ArtRecipe = runCatching { safeRecipe(recipeJson.decodeFromString<ArtRecipe>(json)) }
    .getOrElse { baselineRecipe("invalid-json") }

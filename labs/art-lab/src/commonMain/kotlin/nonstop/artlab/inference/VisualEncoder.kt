package nonstop.artlab.inference

sealed interface EncodingResult {
    data class Available(val embedding: List<Float>, val modelVersion: String) : EncodingResult
    data class Unavailable(val reason: String) : EncodingResult
}
interface VisualEncoder { suspend fun encode(argb: IntArray, width: Int, height: Int): EncodingResult }
class NoModelEncoder : VisualEncoder {
    override suspend fun encode(argb: IntArray, width: Int, height: Int) =
        EncodingResult.Unavailable("IA indisponível — fallback. Fase 1A; nenhum modelo instalado ou executado.")
}

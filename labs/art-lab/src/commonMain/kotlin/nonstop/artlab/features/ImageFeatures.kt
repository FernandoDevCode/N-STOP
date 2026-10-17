package nonstop.artlab.features

// Phase 1B extension point. No unmeasured feature values are manufactured in Phase 1A.
interface FeatureExtractor { suspend fun extract(argb: IntArray, width: Int, height: Int): ImageFeatures }
data class ImageFeatures(val schemaVersion: Int, val values: Map<String, Double>)

package android.util

/** Equivalent Base64 operations for the existing Spotify client on the JVM. */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8
    @JvmStatic fun decode(value: String, flags: Int): ByteArray =
        if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder().decode(value)
        else java.util.Base64.getMimeDecoder().decode(value)
    @JvmStatic fun encodeToString(value: ByteArray, flags: Int): String {
        var encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        return encoder.encodeToString(value)
    }
}

package android.util

/** Android-client compatibility boundary. Auth/network diagnostics never enter Desktop logs. */
@Suppress("UNUSED_PARAMETER")
object Log {
    @JvmStatic fun d(tag: String, message: String, error: Throwable? = null): Int = 0
    @JvmStatic fun i(tag: String, message: String, error: Throwable? = null): Int = 0
    @JvmStatic fun w(tag: String, message: String, error: Throwable? = null): Int = 0
    @JvmStatic fun e(tag: String, message: String, error: Throwable? = null): Int = 0
    @JvmStatic fun v(tag: String, message: String, error: Throwable? = null): Int = 0
}

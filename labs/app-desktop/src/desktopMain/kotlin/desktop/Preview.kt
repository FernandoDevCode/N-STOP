package androidx.compose.ui.tooling.preview

/** Retains source annotations on Android composables compiled on Desktop. */
@Target(AnnotationTarget.FUNCTION)
annotation class Preview(val showBackground: Boolean = false, val name: String = "", val backgroundColor: Long = 0)

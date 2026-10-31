package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.stash.desktop.resources.resourceIndex
import com.stash.desktop.resources.resourceStringFiles
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

private val resourceLoader = Thread.currentThread().contextClassLoader
private val stringTables = mutableMapOf<Pair<String, String>, Map<String, String>>()
private fun loadStrings(module: String, language: String): Map<String, String> =
    synchronized(stringTables) {
        stringTables.getOrPut(module to language) {
            val result = linkedMapOf<String, String>()
            for (folder in listOf("values", "values-$language", "values-$language-r${Locale.getDefault().country}")) {
                for (file in resourceStringFiles["$module/$folder"].orEmpty()) {
                val stream = resourceLoader.getResourceAsStream("android/$module/$folder/$file") ?: continue
                stream.use {
                    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).getElementsByTagName("string")
                    for (i in 0 until nodes.length) {
                        val node = nodes.item(i) as Element
                        result[node.getAttribute("name")] = node.textContent
                            .replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")
                    }
                }
                }
            }
            result
        }
    }

/** Loads the app's original XML strings. Int overload mirrors Android's resource boundary. */
@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String {
    val entry = checkNotNull(resourceIndex[id]) { "Unknown resource $id" }
    val text = checkNotNull(loadStrings(entry.module, Locale.getDefault().language)[entry.name]) {
        "Missing original string ${entry.module}/${entry.name}"
    }
    return if (formatArgs.isEmpty()) text else String.format(Locale.getDefault(), text, *formatArgs)
}

/** VectorDrawable adapter: original path geometry/colours, no recreated wordmark. */
@Composable
fun painterResource(id: Int): Painter {
    val vector = remember(id) {
        val entry = checkNotNull(resourceIndex[id])
        val path = "android/${entry.module}/drawable/${entry.name}.xml"
        val stream = checkNotNull(resourceLoader.getResourceAsStream(path)) { "Missing vector $path" }
        val root = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement }
        fun Element.attr(name: String) = getAttribute("android:$name")
        fun number(value: String, default: Float) = value.removeSuffix("dp").toFloatOrNull() ?: default
        fun colour(value: String): SolidColor? {
            if (value.isBlank()) return null
            require(value.startsWith("#")) { "Unsupported vector colour: $value" }
            val hex = value.drop(1)
            val argb = if (hex.length == 6) 0xff000000L or hex.toLong(16) else hex.toLong(16)
            return SolidColor(Color(argb.toInt()))
        }
        require(root.tagName == "vector") { "Unsupported drawable ${entry.name}" }
        val builder = ImageVector.Builder(
            name = entry.name,
            defaultWidth = number(root.attr("width"), 24f).dp,
            defaultHeight = number(root.attr("height"), 24f).dp,
            viewportWidth = number(root.attr("viewportWidth"), 24f),
            viewportHeight = number(root.attr("viewportHeight"), 24f),
        )
        val paths = root.getElementsByTagName("path")
        for (i in 0 until paths.length) {
            val node = paths.item(i) as Element
            builder.addPath(
                pathData = PathParser().parsePathString(node.attr("pathData")).toNodes(),
                fill = colour(node.attr("fillColor")),
                fillAlpha = number(node.attr("fillAlpha"), 1f),
                stroke = colour(node.attr("strokeColor")),
                strokeAlpha = number(node.attr("strokeAlpha"), 1f),
                strokeLineWidth = number(node.attr("strokeWidth"), 0f),
                strokeLineCap = if (node.attr("strokeLineCap") == "round") StrokeCap.Round else StrokeCap.Butt,
                strokeLineJoin = if (node.attr("strokeLineJoin") == "round") StrokeJoin.Round else StrokeJoin.Miter,
            )
        }
        builder.build()
    }
    return rememberVectorPainter(vector)
}

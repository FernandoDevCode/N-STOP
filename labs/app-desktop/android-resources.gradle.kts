import javax.xml.parsers.DocumentBuilderFactory

val resourceModules = listOf("core/ui", "feature/home", "feature/settings", "feature/nowplaying", "app")
val generatedResources = layout.buildDirectory.dir("generated/android-resources")
val generateAndroidResources by tasks.registering {
    resourceModules.forEach { inputs.dir(rootDir.resolve("../../$it/src/main/res")) }
    outputs.dir(generatedResources)
    doLast {
        val output = generatedResources.get().asFile
        val registry = mutableListOf<String>()
        val stringFiles = linkedMapOf<String, MutableSet<String>>()
        var nextId = 1
        resourceModules.forEach { module ->
            val original = rootDir.resolve("../../$module/src/main/res")
            copy { from(original); into(output.resolve("resources/android/$module")) }
            val names = linkedMapOf<String, MutableSet<String>>()
            original.walkTopDown().filter { it.isFile }.forEach { file ->
                val kind = file.parentFile.name.substringBefore('-')
                if (kind == "values" && file.extension == "xml") {
                    stringFiles.getOrPut("$module/${file.parentFile.name}") { sortedSetOf() }.add(file.name)
                    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                    val strings = document.getElementsByTagName("string")
                    for (i in 0 until strings.length) {
                        val name = strings.item(i).attributes.getNamedItem("name").nodeValue
                        names.getOrPut("string") { sortedSetOf() }.add(name)
                    }
                } else if (kind in listOf("drawable", "mipmap")) {
                    names.getOrPut("drawable") { sortedSetOf() }.add(file.nameWithoutExtension)
                }
            }
            val pkg = "com.stash." + module.replace('/', '.')
            val source = buildString {
                appendLine("package $pkg")
                appendLine("object R {")
                names.forEach { (kind, keys) ->
                    appendLine("  object $kind {")
                    keys.forEach { name ->
                        val id = nextId++
                        appendLine("    const val $name: Int = $id")
                        registry.add("$id to DesktopResource(\"$module\", \"$kind\", \"$name\")")
                    }
                    appendLine("  }")
                }
                appendLine("}")
            }
            output.resolve("kotlin/${pkg.replace('.', '/')}/R.kt").apply {
                parentFile.mkdirs(); writeText(source)
            }
        }
        output.resolve("kotlin/desktop/ResourceIndex.kt").apply {
            parentFile.mkdirs()
            writeText("package com.stash.desktop.resources\n" +
                "data class DesktopResource(val module: String, val kind: String, val name: String)\n" +
                "internal val resourceIndex = mapOf(\n${registry.joinToString(",\n")}\n)\n" +
                "internal val resourceStringFiles = mapOf(\n" + stringFiles.entries.joinToString(",\n") { (folder, files) ->
                    "\"$folder\" to listOf(" + files.joinToString(",") { "\"$it\"" } + ")"
                } + "\n)\n")
        }
    }
}
tasks.matching { it.name.startsWith("compile") || it.name == "desktopProcessResources" }
    .configureEach { dependsOn(generateAndroidResources) }

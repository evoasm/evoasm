package evoasm.gen.x64

import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths

private fun findProjectFile(vararg fileName: String): File {
    return Paths.get(System.getProperty("user.dir"), "src", "evoasm", "x64", *fileName).toFile()
}

open class TemplateClassesWriter(val writer: PrintWriter, val templateFileName: String) {
    companion object {
        val TYPES = listOf("Long", "Float")
        val PACKAGE_REGEX = """^\s*package.*$""".toRegex(RegexOption.MULTILINE)
        val IMPORT_REGEX = """^\s*import.*$""".toRegex(RegexOption.MULTILINE)
    }

    private val template: String
    private val packageName: String
    private val imports: List<String>

    init {
        val originalTemplate = findProjectFile(templateFileName).readText()
        packageName = PACKAGE_REGEX.find(originalTemplate)?.value ?: ""
        imports = IMPORT_REGEX.findAll(originalTemplate).map { it.value }.toList()
        template = originalTemplate.replace(IMPORT_REGEX, "").replace(packageName, "")
    }

    fun writeClasses() {
        writer.println(packageName)
        imports.forEach {
            writer.println(it)
        }
        TYPES.forEach {
            writeClass(it)
        }
    }

    private fun writeClass(type: String) {
        writer.println(template.replace("Double", type))
    }
}

class SampleSetClassesWriter(writer: PrintWriter) : TemplateClassesWriter(writer, "DoubleSampleSet.kt") {}
class PopulationClassesWriter(writer: PrintWriter) : TemplateClassesWriter(writer, "DoublePopulation.kt") {}

fun main() {

    run {
        val file = findProjectFile("SampleSets.kt")
        file.printWriter().use {
            SampleSetClassesWriter(it).writeClasses()
        }
    }

    run {
        val file = findProjectFile("Populations.kt")
        file.printWriter().use {
            PopulationClassesWriter(it).writeClasses()
        }
    }

}
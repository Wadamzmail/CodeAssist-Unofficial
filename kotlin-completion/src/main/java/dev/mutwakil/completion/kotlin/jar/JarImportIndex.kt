package dev.mutwakil.completion.kotlin.jar

import java.io.File
import java.util.zip.ZipFile
/*
* @author Wadamzmail
*/

class JarImportIndex(classpath: List<File>) {

    private val entries: Set<String>

    init {
        val result = HashSet<String>()

        for (jar in classpath) {
            if (!jar.exists() || !jar.name.endsWith(".jar")) continue

            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach { entry ->
                        val fq = entry.name
                            .removeSuffix(".class")
                            .replace('/', '.')
                            .substringBefore('$')

                        // android.widget.Toast
                        result += fq

                        // android.widget
                        fq.substringBeforeLast('.', "")
                            .takeIf { it.isNotEmpty() }
                            ?.let { result += it }
                    }
            }
        }

        entries = result
    }

    fun complete(prefix: String): List<String> =
        entries
            .filter { it.startsWith(prefix) }
            .sorted()
            .take(50)
}
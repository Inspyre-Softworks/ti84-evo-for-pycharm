package com.inspyresoftworks.ti84evo.project

import com.inspyresoftworks.ti84evo.protocol.EvoPythonPayload
import java.nio.file.Path

/**
 * Source-controlled declaration of the Python files that make up an Evo project.
 *
 * Each non-comment line uses `source/path.py=CALCNAME`. Entries are pushed in
 * file order, which keeps the behavior deterministic and leaves room for a
 * future launch-after-upload action.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
object EvoProjectManifest {
    const val FILE_NAME = ".ti84-evo-project"

    data class Entry(
        val sourcePath: String,
        val programName: String,
    )

    class ConfigurationException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val sourcePaths = mutableSetOf<String>()
        val programNames = mutableSetOf<String>()

        text.lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed

            val separator = line.indexOf('=')
            if (separator < 1 || separator == line.lastIndex) {
                throw ConfigurationException(
                    "$FILE_NAME:$lineNumber must use source/path.py=CALCNAME",
                )
            }

            val sourcePath = normalizeSourcePath(line.substring(0, separator).trim(), lineNumber)
            val programName = line.substring(separator + 1).trim().uppercase()
            if (!EvoPythonPayload.isValidProgramName(programName)) {
                throw ConfigurationException(
                    "$FILE_NAME:$lineNumber calculator name must contain 1–8 letters or digits",
                )
            }

            if (!sourcePaths.add(sourcePath.lowercase())) {
                throw ConfigurationException("$FILE_NAME:$lineNumber declares $sourcePath more than once")
            }
            if (!programNames.add(programName)) {
                throw ConfigurationException(
                    "$FILE_NAME:$lineNumber maps more than one source file to $programName",
                )
            }

            entries += Entry(sourcePath, programName)
        }

        if (entries.isEmpty()) {
            throw ConfigurationException("$FILE_NAME does not declare any Python files")
        }
        return entries
    }

    fun render(sourcePaths: Collection<String>): String {
        if (sourcePaths.isEmpty()) {
            throw ConfigurationException("Select at least one Python file")
        }

        val usedNames = mutableSetOf<String>()
        val entries = sourcePaths
            .map { normalizeSourcePath(it, null) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
            .map { sourcePath ->
                val fileStem = sourcePath.substringAfterLast('/').substringBeforeLast('.')
                Entry(sourcePath, uniqueProgramName(fileStem, usedNames))
            }

        return buildString {
            appendLine("# TI-84 Evo Python project")
            appendLine("# source path = calculator program name (1-8 letters or digits)")
            appendLine("# Files are pushed in the order listed below.")
            for (entry in entries) {
                appendLine("${entry.sourcePath}=${entry.programName}")
            }
        }
    }

    fun resolveSource(projectRoot: Path, sourcePath: String): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        val resolved = root.resolve(sourcePath.replace('/', java.io.File.separatorChar)).normalize()
        if (!resolved.startsWith(root)) {
            throw ConfigurationException("Source file escapes the project directory: $sourcePath")
        }
        return resolved
    }

    private fun normalizeSourcePath(sourcePath: String, lineNumber: Int?): String {
        val normalized = sourcePath.replace('\\', '/').trim()
        val location = lineNumber?.let { "$FILE_NAME:$it " } ?: ""
        if (normalized.isEmpty()) {
            throw ConfigurationException("${location}source path cannot be empty")
        }
        if (normalized.startsWith('/') || DRIVE_PREFIX.matches(normalized)) {
            throw ConfigurationException("${location}source path must be relative to the project")
        }
        if ('=' in normalized) {
            throw ConfigurationException("${location}source path cannot contain =")
        }
        if (!normalized.endsWith(".py", ignoreCase = true)) {
            throw ConfigurationException("${location}source file must end in .py")
        }
        return normalized
    }

    private fun uniqueProgramName(fileStem: String, usedNames: MutableSet<String>): String {
        val base = EvoPythonPayload.defaultProgramName(fileStem)
        if (usedNames.add(base)) return base

        var number = 2
        while (true) {
            val suffix = number.toString()
            val candidate = base.take(8 - suffix.length) + suffix
            if (usedNames.add(candidate)) return candidate
            number++
        }
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:.*")
}

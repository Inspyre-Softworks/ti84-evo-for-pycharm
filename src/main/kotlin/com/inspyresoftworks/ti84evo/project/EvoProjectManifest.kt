package com.inspyresoftworks.ti84evo.project

import com.inspyresoftworks.ti84evo.protocol.EvoPythonPayload
import java.nio.file.Path
import java.nio.file.Paths

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
    private const val ALWAYS_PUSH_ALL_OPTION = "@always-push-all="

    data class Entry(
        val sourcePath: String,
        val programName: String,
        val archived: Boolean = false,
    )

    data class Configuration(
        val entries: List<Entry>,
        val alwaysPushAll: Boolean = false,
    )

    class ConfigurationException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): List<Entry> = parseConfiguration(text).entries

    fun parseConfiguration(text: String): Configuration {
        val entries = mutableListOf<Entry>()
        val sourcePaths = mutableSetOf<String>()
        val programNames = mutableSetOf<String>()
        var alwaysPushAll = false

        text.lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            if (line.startsWith(ALWAYS_PUSH_ALL_OPTION, ignoreCase = true)) {
                alwaysPushAll = when (line.substringAfter('=').trim().lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> throw ConfigurationException(
                        "$FILE_NAME:$lineNumber always-push-all must be true or false",
                    )
                }
                return@forEachIndexed
            }

            val separator = line.indexOf('=')
            if (separator < 1 || separator == line.lastIndex) {
                throw ConfigurationException(
                    "$FILE_NAME:$lineNumber must use source/path.py=CALCNAME",
                )
            }

            val sourcePath = normalizeSourcePath(line.substring(0, separator).trim(), lineNumber)
            val targetParts = line.substring(separator + 1).trim().split('|', limit = 2)
            val programName = targetParts[0].trim().uppercase()
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

            val archived = when (val target = targetParts.getOrNull(1)?.trim()?.lowercase()) {
                null, "", "ram" -> false
                "archive" -> true
                else -> throw ConfigurationException(
                    "$FILE_NAME:$lineNumber storage must be RAM or Archive, got $target",
                )
            }

            entries += Entry(sourcePath, programName, archived)
        }

        if (entries.isEmpty()) {
            throw ConfigurationException("$FILE_NAME does not declare any Python files")
        }
        return Configuration(entries, alwaysPushAll)
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


        return renderEntries(entries)
    }

    fun renderEntries(entries: Collection<Entry>, alwaysPushAll: Boolean = false): String {
        if (entries.isEmpty()) {
            throw ConfigurationException("Configure at least one Python file")
        }
        val normalized = entries.map { entry ->
            Entry(
                normalizeSourcePath(entry.sourcePath, null),
                entry.programName.uppercase(),
                entry.archived,
            )
        }
        validateEntries(normalized)

        return buildString {
            appendLine("# TI-84 Evo Python project")
            appendLine("# source path = calculator program name | RAM or Archive")
            appendLine("# Files are pushed in the order listed below.")
            appendLine("$ALWAYS_PUSH_ALL_OPTION$alwaysPushAll")
            for (entry in normalized) {
                appendLine("${entry.sourcePath}=${entry.programName}|${if (entry.archived) "Archive" else "RAM"}")
            }
        }
    }

    private fun validateEntries(entries: Collection<Entry>) {
        val sourcePaths = mutableSetOf<String>()
        val programNames = mutableSetOf<String>()
        entries.forEach { entry ->
            val normalizedPath = normalizeSourcePath(entry.sourcePath, null)
            if (!EvoPythonPayload.isValidProgramName(entry.programName)) {
                throw ConfigurationException("Calculator name must contain 1–8 letters or digits: ${entry.programName}")
            }
            if (!sourcePaths.add(normalizedPath.lowercase())) {
                throw ConfigurationException("${entry.sourcePath} is configured more than once")
            }
            if (!programNames.add(entry.programName.uppercase())) {
                throw ConfigurationException("Calculator name ${entry.programName} is used more than once")
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
        val slashNormalized = sourcePath.replace('\\', '/').trim()
        val location = lineNumber?.let { "$FILE_NAME:$it " } ?: ""
        if (slashNormalized.isEmpty()) {
            throw ConfigurationException("${location}source path cannot be empty")
        }
        if (slashNormalized.startsWith('/') || DRIVE_PREFIX.matches(slashNormalized)) {
            throw ConfigurationException("${location}source path must be relative to the project")
        }
        if ('=' in slashNormalized) {
            throw ConfigurationException("${location}source path cannot contain =")
        }
        if (!slashNormalized.endsWith(".py", ignoreCase = true)) {
            throw ConfigurationException("${location}source file must end in .py")
        }
        val normalized = Paths.get(slashNormalized).normalize().toString().replace('\\', '/')
        if (normalized.startsWith("..")) {
            throw ConfigurationException("${location}source path escapes the project directory: $sourcePath")
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

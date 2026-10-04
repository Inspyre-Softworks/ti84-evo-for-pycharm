package com.inspyresoftworks.ti84evo.project

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class EvoResolvedProjectProgram(
    val entry: EvoProjectManifest.Entry,
    val source: String,
)

data class EvoResolvedProject(
    val root: Path,
    val programs: List<EvoResolvedProjectProgram>,
    val alwaysPushAll: Boolean,
)

object EvoProjectResolver {
    fun resolve(
        manifestPath: Path,
        readText: (Path) -> String? = { path ->
            if (Files.isRegularFile(path)) Files.readString(path, StandardCharsets.UTF_8) else null
        },
    ): EvoResolvedProject {
        val manifest = manifestPath.toAbsolutePath().normalize()
        val root = manifest.parent ?: throw EvoProjectManifest.ConfigurationException("Manifest has no parent: $manifest")
        val manifestText = readText(manifest) ?: throw java.nio.file.NoSuchFileException(manifest.toString())
        val configuration = EvoProjectManifest.parseConfiguration(manifestText)
        val programs = configuration.entries.map { entry ->
            val sourcePath = EvoProjectManifest.resolveSource(root, entry.sourcePath)
            val source = readText(sourcePath) ?: throw EvoProjectManifest.ConfigurationException(
                "Declared source file does not exist: ${entry.sourcePath}",
            )
            EvoResolvedProjectProgram(entry, source)
        }
        return EvoResolvedProject(root, programs, configuration.alwaysPushAll)
    }
}

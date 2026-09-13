package com.inspyresoftworks.ti84evo.project

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

/** Host-side fingerprints used to make project pushes incremental. */
object EvoProjectUploadState {
    private const val DIRECTORY = ".idea"
    private const val FILE_NAME = "ti84-evo-upload-state.properties"

    fun fingerprint(entry: EvoProjectManifest.Entry, source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val identity = buildString {
            append(entry.programName.uppercase())
            append('\u0000')
            append(if (entry.archived) "archive" else "ram")
            append('\u0000')
            append(source)
        }
        return digest.digest(identity.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun pending(
        projectRoot: Path,
        programs: List<Pair<EvoProjectManifest.Entry, String>>,
        calculatorDirectory: Collection<EvoDirectoryEntry>? = null,
        calculatorSources: Map<String, String>? = null,
    ): List<Pair<EvoProjectManifest.Entry, String>> {
        val state = load(projectRoot)
        return programs.filter { (entry, source) ->
            state.getProperty(entry.sourcePath) != fingerprint(entry, source) ||
                calculatorDirectory?.none { calculatorEntry ->
                    calculatorEntry.type == PYTHON_TYPE &&
                        calculatorEntry.name.equals(entry.programName, ignoreCase = true) &&
                        calculatorEntry.archived == entry.archived
                } == true ||
                calculatorSources?.get(entry.programName.uppercase())?.let { it != source } == true
        }
    }

    @Synchronized
    fun markUploaded(projectRoot: Path, entry: EvoProjectManifest.Entry, source: String) {
        markSynchronized(projectRoot, entry, source)
    }

    @Synchronized
    fun markSynchronized(projectRoot: Path, entry: EvoProjectManifest.Entry, source: String) {
        val state = load(projectRoot)
        state.setProperty(entry.sourcePath, fingerprint(entry, source))
        val path = statePath(projectRoot)
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, "ti84-evo-upload-", ".tmp")
        Files.newOutputStream(temporary).use { state.store(it, "TI-84 Evo successful upload fingerprints") }
        try {
            runCatching {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun load(projectRoot: Path): Properties = Properties().apply {
        val path = statePath(projectRoot)
        if (Files.isRegularFile(path)) Files.newInputStream(path).use(::load)
    }

    private fun statePath(projectRoot: Path): Path = projectRoot.resolve(DIRECTORY).resolve(FILE_NAME)

    private const val PYTHON_TYPE = 15
}

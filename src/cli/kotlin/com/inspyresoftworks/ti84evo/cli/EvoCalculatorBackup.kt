package com.inspyresoftworks.ti84evo.cli

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.protocol.EvoLink
import com.inspyresoftworks.ti84evo.protocol.EvoVariableFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/** Lossless, all-variable calculator backup used before hardware acceptance. */
internal object EvoCalculatorBackup {
    data class Result(val directory: Path, val variables: Int, val bytes: Long)

    fun create(outputDirectory: Path): Result {
        require(!Files.exists(outputDirectory)) { "Backup destination already exists: $outputDirectory" }
        Files.createDirectories(outputDirectory.resolve("variables"))
        Files.writeString(
            outputDirectory.resolve("INCOMPLETE.txt"),
            "This backup is incomplete unless COMPLETE.txt is present.\n",
            StandardCharsets.UTF_8,
        )

        val entries: List<EvoDirectoryEntry>
        val attributes: Map<String, Any?>
        val rawVariables = mutableListOf<Pair<EvoDirectoryEntry, ByteArray>>()
        CliTransport.auto().use { transport ->
            transport.open()
            val link = EvoLink(transport)
            attributes = link.getAttributes()
            entries = link.getDirectory().sortedWith(compareBy({ it.name.lowercase() }, { it.type }, { it.archived }))
            entries.forEachIndexed { index, entry ->
                val raw = EvoVariableFile.addChecksum(downloadWithRetry(transport, link, entry))
                rawVariables += entry to raw
                val fileName = "%03d_%s_type-%d_%s.bin".format(
                    index + 1,
                    safeName(entry.name),
                    entry.type,
                    entry.location.lowercase(),
                )
                Files.write(outputDirectory.resolve("variables").resolve(fileName), raw)
            }
        }

        Files.writeString(outputDirectory.resolve("attributes.txt"), renderAttributes(attributes), StandardCharsets.UTF_8)
        Files.writeString(outputDirectory.resolve("directory.tsv"), renderDirectory(entries), StandardCharsets.UTF_8)
        Files.writeString(outputDirectory.resolve("manifest.tsv"), renderManifest(rawVariables), StandardCharsets.UTF_8)
        Files.writeString(outputDirectory.resolve("README.txt"), renderReadme(rawVariables), StandardCharsets.UTF_8)
        Files.writeString(
            outputDirectory.resolve("COMPLETE.txt"),
            "All ${rawVariables.size} directory entries were downloaded and hashed.\n",
            StandardCharsets.UTF_8,
        )
        Files.deleteIfExists(outputDirectory.resolve("INCOMPLETE.txt"))
        return Result(outputDirectory, rawVariables.size, rawVariables.sumOf { it.second.size.toLong() })
    }

    private fun downloadWithRetry(
        transport: com.inspyresoftworks.ti84evo.transport.EvoTransport,
        link: EvoLink,
        entry: EvoDirectoryEntry,
    ): ByteArray {
        var lastFailure: RuntimeException? = null
        repeat(3) { attempt ->
            try {
                transport.close()
                transport.open()
                return link.getVariable(entry)
            } catch (error: RuntimeException) {
                lastFailure = error
                if (attempt < 2) Thread.sleep(300L * (attempt + 1))
            }
        }
        throw IllegalStateException(
            "Could not back up ${entry.name}:${entry.type} from ${entry.location} after 3 attempts: " +
                lastFailure?.message,
            lastFailure,
        )
    }

    private fun renderAttributes(attributes: Map<String, Any?>): String = buildString {
        appendLine("Captured: ${Instant.now()}")
        attributes.toSortedMap().forEach { (key, value) ->
            val rendered = if (SENSITIVE_KEY.containsMatchIn(key)) "<redacted>" else renderValue(value)
            appendLine("$key=$rendered")
        }
    }

    private fun renderDirectory(entries: List<EvoDirectoryEntry>): String = buildString {
        appendLine("name\ttype\ttype_name\tsize\tmemory\ttoken_name_base64")
        entries.forEach { entry ->
            append(entry.name.replace('\t', ' ')).append('\t')
            append(entry.type).append('\t')
            append(entry.typeName).append('\t')
            append(entry.size).append('\t')
            append(entry.location).append('\t')
            appendLine(Base64.getEncoder().encodeToString(entry.tokenName))
        }
    }

    private fun renderManifest(variables: List<Pair<EvoDirectoryEntry, ByteArray>>): String = buildString {
        appendLine("index\tname\ttype\tmemory\tbytes\tsha256")
        variables.forEachIndexed { index, (entry, raw) ->
            append(index + 1).append('\t')
            append(entry.name.replace('\t', ' ')).append('\t')
            append(entry.type).append('\t')
            append(entry.location).append('\t')
            append(raw.size).append('\t')
            appendLine(sha256(raw))
        }
    }

    private fun renderReadme(variables: List<Pair<EvoDirectoryEntry, ByteArray>>): String = buildString {
        appendLine("TI-84 Evo lossless calculator backup")
        appendLine("Created: ${Instant.now()}")
        appendLine("Variables: ${variables.size}")
        appendLine("Payload bytes: ${variables.sumOf { it.second.size.toLong() }}")
        appendLine()
        appendLine("Each variables/*.bin file is a complete checksummed native Evo variable file.")
        appendLine("Use the Calculator Files > Upload variable file action to restore an individual envelope.")
        appendLine("directory.tsv preserves the tokenized identity and RAM/Archive location; manifest.tsv records SHA-256 hashes.")
    }

    private fun renderValue(value: Any?): String = when (value) {
        is ByteArray -> Base64.getEncoder().encodeToString(value)
        else -> value.toString().replace("\r", "\\r").replace("\n", "\\n")
    }

    private fun safeName(name: String): String = name
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
        .joinToString("")
        .ifBlank { "unnamed" }
        .take(32)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val SENSITIVE_KEY = Regex("(?i)(^id$|serial|uuid|certificate|device.?id)")
}

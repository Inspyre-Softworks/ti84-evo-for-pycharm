package com.inspyresoftworks.ti84evo.cli

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.model.EvoScreenCapture
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import com.inspyresoftworks.ti84evo.protocol.EvoImagePayload
import com.inspyresoftworks.ti84evo.protocol.EvoLink
import com.inspyresoftworks.ti84evo.protocol.EvoPythonPayload
import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectVerifier
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.protocol.EvoVariableDecoder
import com.inspyresoftworks.ti84evo.protocol.EvoVariableFile
import com.inspyresoftworks.ti84evo.protocol.EvoVariablePayload
import com.inspyresoftworks.ti84evo.protocol.EvoVariableTransfer
import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import javax.imageio.ImageIO

/** Destructive-but-contained physical-device acceptance for release candidates. */
internal object EvoHardwareAcceptance {
    private const val MAIN_PROGRAM = "HA42MAIN"
    private const val LIB_PROGRAM = "HA42LIB"
    private const val UTIL_PROGRAM = "HA42UTIL"
    private const val LIST_NAME = "HA42L"
    private const val IMAGE_NAME = "HA42IMG"

    private data class Outcome(
        val workflow: String,
        val passed: Boolean,
        val detail: String,
        val elapsedMillis: Long,
    )

    fun run(outputDirectory: Path, backupDirectory: Path) {
        require(!Files.exists(outputDirectory)) { "Acceptance output already exists: $outputDirectory" }
        require(Files.isRegularFile(backupDirectory.resolve("COMPLETE.txt"))) {
            "Refusing hardware changes without a completed backup: $backupDirectory"
        }
        Files.createDirectories(outputDirectory)
        val traceDirectory = outputDirectory.resolve("traces")
        Files.createDirectories(traceDirectory)
        val previousTrace = System.getProperty(CliTransport.TRACE_DIRECTORY_PROPERTY)
        System.setProperty(CliTransport.TRACE_DIRECTORY_PROPERTY, traceDirectory.toString())

        val started = Instant.now()
        val outcomes = mutableListOf<Outcome>()
        var baseline = emptyList<EvoDirectoryEntry>()
        var numberName: String? = null
        var matrixName: String? = null
        var setupFailure: Throwable? = null
        try {
            val (description, attributes, directory) = connected { transport ->
                val link = EvoLink(transport)
                Triple(transport.description, link.getAttributes(), link.getDirectory())
            }
            baseline = directory
            numberName = firstFreeName(('A'..'Z').map(Char::toString), 0, baseline)
            matrixName = firstFreeName(('A'..'J').map(Char::toString), 6, baseline)
            val requiredNumberName = checkNotNull(numberName)
            val requiredMatrixName = checkNotNull(matrixName)
            val reserved = setOf(MAIN_PROGRAM, LIB_PROGRAM, UTIL_PROGRAM, LIST_NAME, IMAGE_NAME)
            require(baseline.none { it.name.uppercase() in reserved }) {
                "Calculator already contains an HA42 acceptance variable; remove it or choose a clean device"
            }
            Files.writeString(
                outputDirectory.resolve("host-configuration.txt"),
                renderHostConfiguration(description, attributes, backupDirectory),
                StandardCharsets.UTF_8,
            )

            step(outcomes, "Multi-file push, calculator mutation, repush, clean pull") {
                testProjectRoundTrip(outputDirectory)
            }
            step(outcomes, "Number edit, Archive move, and deletion") {
                testEditableVariable(
                    EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.NUMBER, requiredNumberName, "42.5"),
                    "-4.25",
                )
            }
            step(outcomes, "Custom-list edit, Archive move, and deletion") {
                testEditableVariable(
                    EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.LIST, LIST_NAME, "{1,2,3}"),
                    "{5,-8,13,21}",
                )
            }
            step(outcomes, "Matrix edit, Archive move, and deletion") {
                testEditableVariable(
                    EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.MATRIX, requiredMatrixName, "[1,2]\n[3,4]"),
                    "[8,6]\n[7,5]\n[3,0]",
                )
            }
            step(outcomes, "Image conversion, transfer, readback, Archive move, and deletion") {
                testImage(outputDirectory)
            }
            step(outcomes, "Two full-resolution screenshot captures") {
                testScreenshots(outputDirectory)
            }
        } catch (error: Throwable) {
            setupFailure = error
        } finally {
            setupFailure?.let { error ->
                outcomes += Outcome(
                    workflow = "Initial setup and acceptance workflows",
                    passed = false,
                    detail = (error.message ?: error.javaClass.simpleName).replace('|', '/').replace('\n', ' '),
                    elapsedMillis = 0,
                )
            }
            step(outcomes, "Acceptance-fixture cleanup") {
                cleanupFixtures(baseline, numberName, matrixName)
            }
            step(outcomes, "Post-run directory matches pre-run identities and locations") {
                val after = readDirectory()
                check(directoryIdentity(after) == directoryIdentity(baseline)) {
                    "Calculator directory identity/location set changed outside acceptance fixtures"
                }
            }
            Files.writeString(
                outputDirectory.resolve("results.md"),
                renderResults(started, Instant.now(), backupDirectory, traceDirectory, outcomes),
                StandardCharsets.UTF_8,
            )
            if (previousTrace == null) System.clearProperty(CliTransport.TRACE_DIRECTORY_PROPERTY)
            else System.setProperty(CliTransport.TRACE_DIRECTORY_PROPERTY, previousTrace)
        }

        val failed = outcomes.filterNot(Outcome::passed)
        check(failed.isEmpty()) {
            "Hardware acceptance failed: ${failed.joinToString { it.workflow }}; see ${outputDirectory.resolve("results.md")}" 
        }
        println("Hardware acceptance passed; results: ${outputDirectory.resolve("results.md")}")
    }

    private fun testProjectRoundTrip(outputDirectory: Path) {
        val sourceRoot = outputDirectory.resolve("project-source")
        Files.createDirectories(sourceRoot.resolve("lib"))
        val sources = linkedMapOf(
            "main.py" to "print(\"HA42 main: original\")\n",
            "lib/helper.py" to "def double(value):\n    return value * 2\n",
            "lib/state.py" to "VALUES = [1, 2, 3, 5, 8]\n",
        )
        sources.forEach { (relative, source) ->
            val target = sourceRoot.resolve(relative)
            Files.createDirectories(target.parent)
            Files.writeString(target, source, StandardCharsets.UTF_8)
        }
        val entries = listOf(
            EvoProjectManifest.Entry("main.py", MAIN_PROGRAM, archived = false),
            EvoProjectManifest.Entry("lib/helper.py", LIB_PROGRAM, archived = true),
            EvoProjectManifest.Entry("lib/state.py", UTIL_PROGRAM, archived = false),
        )
        Files.writeString(
            sourceRoot.resolve(EvoProjectManifest.FILE_NAME),
            EvoProjectManifest.renderEntries(entries),
            StandardCharsets.UTF_8,
        )

        try {
            EvoCli.send(listOf("--project", sourceRoot.toString()))
            assertPrograms(entries, sources)

            connected { transport ->
                EvoPythonTransfer(transport).upload(
                    MAIN_PROGRAM,
                    "print(\"HA42 calculator-side edit\")\n",
                    archive = false,
                    overwrite = true,
                )
            }
            connected { transport ->
                val link = EvoLink(transport)
                val lib = requireEntry(link.getDirectory(), LIB_PROGRAM, 15)
                link.deleteVariables(listOf(lib))
            }
            connected { transport ->
                val link = EvoLink(transport)
                val util = requireEntry(link.getDirectory(), UTIL_PROGRAM, 15)
                EvoVariableTransfer(transport).archiveVariables(listOf(util))
            }

            EvoCli.send(listOf("--project", sourceRoot.toString()))
            assertPrograms(entries, sources)

            val pullRoot = outputDirectory.resolve("project-pulled-clean")
            EvoCli.pull(listOf("--project", pullRoot.toString()))
            val pulledEntries = EvoProjectManifest.parse(
                Files.readString(pullRoot.resolve(EvoProjectManifest.FILE_NAME), StandardCharsets.UTF_8),
            ).associateBy { it.programName }
            entries.forEach { expected ->
                val pulled = checkNotNull(pulledEntries[expected.programName]) {
                    "Clean pull omitted ${expected.programName}"
                }
                check(pulled.archived == expected.archived) { "Clean pull changed ${expected.programName} location" }
                val pulledSource = Files.readString(pullRoot.resolve(pulled.sourcePath), StandardCharsets.UTF_8)
                check(pulledSource == sources.getValue(expected.sourcePath)) {
                    "Clean pull content mismatch for ${expected.programName}"
                }
            }
        } finally {
            deleteByNames(setOf(MAIN_PROGRAM, LIB_PROGRAM, UTIL_PROGRAM))
        }
    }

    private fun assertPrograms(
        entries: List<EvoProjectManifest.Entry>,
        sources: Map<String, String>,
    ) {
        connected { transport ->
            val directory = EvoLink(transport).getDirectory()
            val programs = entries.map { entry ->
                EvoPythonTransfer.Program(entry.programName, sources.getValue(entry.sourcePath), entry.archived)
            }
            val actualSources = EvoPythonProjectVerifier(transport).readSources(directory, programs)
            entries.forEach { expected ->
                val entry = requireEntry(directory, expected.programName, 15)
                check(entry.archived == expected.archived) { "${expected.programName} is in ${entry.location}" }
                check(actualSources[expected.programName] == sources.getValue(expected.sourcePath)) {
                    "${expected.programName} source differs from the fixture"
                }
            }
        }
    }

    private fun testEditableVariable(initial: EvoVariablePayload.EditableValue, editedValue: String) {
        val identityType = initial.kind.typeId
        try {
            connected { EvoVariableTransfer(it).uploadEditable(initial) }
            assertEditable(initial)
            val edited = initial.copy(value = editedValue)
            connected { EvoVariableTransfer(it).uploadEditable(edited) }
            assertEditable(edited)
            val entryToArchive = requireEntry(readDirectory(), initial.name, identityType)
            connected { transport ->
                EvoVariableTransfer(transport).archiveVariables(listOf(entryToArchive))
            }
            assertEditable(edited.copy(archived = true))
            deleteByNames(setOf(initial.name), identityType)
            check(readDirectory().none {
                    it.type == identityType &&
                        canonicalName(it.name, identityType) == canonicalName(initial.name, identityType)
            }) { "${initial.name}:$identityType remained after deletion" }
        } finally {
            deleteByNames(setOf(initial.name), identityType)
        }
    }

    private fun assertEditable(expected: EvoVariablePayload.EditableValue) {
        val entry = requireEntry(readDirectory(), expected.name, expected.kind.typeId)
        check(entry.archived == expected.archived) { "${expected.name} is in ${entry.location}" }
        connected { transport ->
            val link = EvoLink(transport)
            val decoded = EvoVariableDecoder.decode(link.getVariable(entry), entry.type, entry.name, entry.archived)
            val normalized = EvoVariablePayload.normalizeValue(expected.kind, expected.value)
            val decodedNormalized = EvoVariablePayload.normalizeValue(expected.kind, decoded.value)
            check(decodedNormalized == normalized) {
                "${expected.name} decoded as $decodedNormalized, expected $normalized"
            }
        }
    }

    private fun testImage(outputDirectory: Path) {
        val source = BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            source.setRGB(x, y, ((x * 255 / 47) shl 16) or ((y * 255 / 31) shl 8) or ((x + y) * 255 / 78))
        }
        ImageIO.write(source, "png", outputDirectory.resolve("image-source.png").toFile())
        val built = EvoImagePayload.build(source, IMAGE_NAME, true, 48, 32, 32)
        try {
            val uploaded = connected { EvoVariableTransfer(it).uploadImage(built, archived = false) }
            val uploadedEntry = requireEntry(readDirectory(), IMAGE_NAME, 8)
            check(uploadedEntry.archived == uploaded.archived) { "Image upload reported the wrong memory location" }
            connected { transport ->
                val link = EvoLink(transport)
                val downloaded = link.getVariable(uploadedEntry)
                check(
                    EvoVariableFile.inspect(downloaded).data.contentEquals(EvoVariableFile.inspect(built.bytes).data),
                ) { "Downloaded image payload differs from uploaded image payload" }
            }
            if (!uploadedEntry.archived) {
                connected { transport ->
                    EvoVariableTransfer(transport).archiveVariables(listOf(uploadedEntry))
                }
            }
            check(requireEntry(readDirectory(), IMAGE_NAME, 8).archived) {
                "$IMAGE_NAME was not moved to Archive"
            }
            deleteByNames(setOf(IMAGE_NAME), 8)
        } finally {
            deleteByNames(setOf(IMAGE_NAME), 8)
        }
    }

    private fun testScreenshots(outputDirectory: Path) {
        repeat(2) { index ->
            val capture = connected { EvoLink(it).getScreenCapture() }
            check(capture.width == 320 && capture.height == 240 && capture.bitsPerPixel == 16) {
                "Unexpected screenshot format ${capture.width}x${capture.height}x${capture.bitsPerPixel}"
            }
            check(capture.framebuffer.size == capture.width * capture.height * 2) {
                "Screenshot framebuffer length does not match its declared dimensions"
            }
            val target = outputDirectory.resolve("screenshot-${index + 1}.png")
            check(ImageIO.write(toBufferedImage(capture), "png", target.toFile())) { "PNG writer unavailable" }
            check(Files.size(target) > 0) { "Screenshot PNG is empty" }
        }
    }

    private fun cleanupFixtures(
        baseline: List<EvoDirectoryEntry>,
        numberName: String?,
        matrixName: String?,
    ) {
        val baselineIdentities = baseline.mapTo(mutableSetOf()) { canonicalName(it.name, it.type) to it.type }
        val removable = readDirectory().filter { entry ->
                val acceptanceName = entry.name.uppercase() in setOf(
                    MAIN_PROGRAM, LIB_PROGRAM, UTIL_PROGRAM, LIST_NAME, IMAGE_NAME,
                ) || (entry.type == 0 && entry.name.equals(numberName, true)) ||
                    (entry.type == 6 && matrixName != null &&
                        canonicalName(entry.name, entry.type) == canonicalName(matrixName, entry.type))
                acceptanceName && (canonicalName(entry.name, entry.type) to entry.type) !in baselineIdentities
        }
        if (removable.isNotEmpty()) {
            connected { transport -> EvoLink(transport).deleteVariables(removable) }
        }
    }

    private fun deleteByNames(names: Set<String>, type: Int? = null) {
        val matches = readDirectory().filter { entry ->
                names.any { candidate ->
                    canonicalName(entry.name, entry.type) == canonicalName(candidate, entry.type)
                } && (type == null || entry.type == type)
        }
        if (matches.isNotEmpty()) {
            connected { transport -> EvoLink(transport).deleteVariables(matches) }
        }
    }

    private fun requireEntry(directory: Collection<EvoDirectoryEntry>, name: String, type: Int): EvoDirectoryEntry =
        checkNotNull(directory.singleOrNull { it.type == type && canonicalName(it.name, type) == canonicalName(name, type) }) {
            "Calculator does not contain exactly one $name:$type entry"
        }

    private fun canonicalName(name: String, type: Int): String =
        (if (type == 6) name.removeSurrounding("[", "]") else name).uppercase()

    private fun firstFreeName(
        candidates: List<String>,
        type: Int,
        directory: Collection<EvoDirectoryEntry>,
    ): String = candidates.firstOrNull { name ->
        directory.none { it.type == type && canonicalName(it.name, type) == canonicalName(name, type) }
    } ?: error("No free calculator name is available for type $type acceptance data")

    private fun directoryIdentity(entries: Collection<EvoDirectoryEntry>): Set<Triple<String, Int, Boolean>> =
        entries.mapTo(mutableSetOf()) { Triple(canonicalName(it.name, it.type), it.type, it.archived) }

    private fun <T> connected(block: (EvoTransport) -> T): T = CliTransport.auto().use { transport ->
        transport.open()
        block(transport)
    }

    private fun readDirectory(): List<EvoDirectoryEntry> {
        var lastFailure: RuntimeException? = null
        repeat(3) { attempt ->
            try {
                return connected { EvoLink(it).getDirectory() }
            } catch (error: RuntimeException) {
                lastFailure = error
                if (attempt < 2) Thread.sleep(300L * (attempt + 1))
            }
        }
        throw lastFailure ?: IllegalStateException("calculator directory read failed")
    }

    private fun step(outcomes: MutableList<Outcome>, name: String, operation: () -> Unit) {
        val started = System.nanoTime()
        try {
            operation()
            val elapsed = (System.nanoTime() - started) / 1_000_000
            outcomes += Outcome(name, true, "Completed", elapsed)
            println("PASS: $name (${elapsed} ms)")
        } catch (error: Throwable) {
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val detail = error.message ?: error.javaClass.simpleName
            outcomes += Outcome(name, false, detail.replace('|', '/').replace('\n', ' '), elapsed)
            System.err.println("FAIL: $name — $detail")
        }
    }

    private fun renderHostConfiguration(
        transport: String,
        attributes: Map<String, Any?>,
        backupDirectory: Path,
    ): String = buildString {
        appendLine("Captured: ${Instant.now()}")
        appendLine("Plugin/CLI version: ${readVersion()}")
        appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        appendLine("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        appendLine("Transport: $transport")
        appendLine("Backup: $backupDirectory")
        appendLine("Calculator attributes:")
        attributes.toSortedMap().forEach { (key, value) ->
            appendLine("  $key=${if (SENSITIVE_KEY.containsMatchIn(key)) "<redacted>" else value}")
        }
    }

    private fun renderResults(
        started: Instant,
        ended: Instant,
        backupDirectory: Path,
        traceDirectory: Path,
        outcomes: List<Outcome>,
    ): String = buildString {
        appendLine("# TI-84 Evo ${readVersion()} hardware acceptance")
        appendLine()
        appendLine("- Started: $started")
        appendLine("- Ended: $ended")
        appendLine("- Duration: ${Duration.between(started, ended).toSeconds()} seconds")
        appendLine("- Backup: `$backupDirectory`")
        appendLine("- Packet traces: `$traceDirectory`")
        appendLine()
        appendLine("| Workflow | Result | Duration | Detail |")
        appendLine("| --- | --- | ---: | --- |")
        outcomes.forEach { outcome ->
            appendLine(
                "| ${outcome.workflow} | ${if (outcome.passed) "PASS" else "FAIL"} | " +
                    "${outcome.elapsedMillis} ms | ${outcome.detail} |",
            )
        }
        appendLine()
        appendLine("Overall: **${if (outcomes.isNotEmpty() && outcomes.all(Outcome::passed)) "PASS" else "FAIL"}**")
    }

    private fun readVersion(): String = EvoCli::class.java
        .getResourceAsStream("/META-INF/ti84-evo-version.txt")
        ?.bufferedReader()
        ?.use { it.readText().trim() }
        .orEmpty()
        .ifBlank { "development" }

    private fun toBufferedImage(capture: EvoScreenCapture): BufferedImage {
        val image = BufferedImage(capture.width, capture.height, BufferedImage.TYPE_INT_RGB)
        var offset = 0
        for (y in 0 until capture.height) for (x in 0 until capture.width) {
            val value = (capture.framebuffer[offset].toInt() and 0xff) or
                ((capture.framebuffer[offset + 1].toInt() and 0xff) shl 8)
            offset += 2
            val r5 = (value ushr 11) and 0x1f
            val g6 = (value ushr 5) and 0x3f
            val b5 = value and 0x1f
            val red = (r5 shl 3) or (r5 ushr 2)
            val green = (g6 shl 2) or (g6 ushr 4)
            val blue = (b5 shl 3) or (b5 ushr 2)
            image.setRGB(x, y, (red shl 16) or (green shl 8) or blue)
        }
        return image
    }

    private val SENSITIVE_KEY = Regex("(?i)(^id$|serial|uuid|certificate|device.?id)")
}

package com.inspyresoftworks.ti84evo.project

import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectPuller
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Plans and writes a calculator-originated Python project without silent overwrites. */
object EvoProjectPull {
    data class Target(
        val entry: EvoProjectManifest.Entry,
        val path: Path,
        val source: String,
        val conflicts: Boolean,
    )

    data class Plan(
        val projectRoot: Path,
        val targets: List<Target>,
        val manifestText: String,
    ) {
        val conflicts: List<Target> = targets.filter { it.conflicts }
    }

    fun plan(
        projectRoot: Path,
        programs: List<EvoPythonProjectPuller.Program>,
        existing: EvoProjectManifest.Configuration? = null,
        currentSource: ((Path) -> String?)? = null,
    ): Plan {
        require(programs.isNotEmpty()) { "Pulled project must contain at least one Python program" }
        val root = projectRoot.toAbsolutePath().normalize()
        val byName = programs.associateBy { it.programName.uppercase() }
        require(byName.size == programs.size) { "Pulled project contains duplicate calculator program names" }

        val existingByName = existing?.entries.orEmpty().associateBy { it.programName.uppercase() }
        val ordered = buildList {
            existing?.entries.orEmpty().forEach { entry ->
                byName[entry.programName.uppercase()]?.let(::add)
            }
            programs.sortedBy { it.programName }.forEach { program ->
                if (none { it.programName.equals(program.programName, ignoreCase = true) }) add(program)
            }
        }

        val usedPaths = mutableSetOf<String>()
        val targets = ordered.map { program ->
            val existingEntry = existingByName[program.programName.uppercase()]
            var sourcePath = existingEntry?.sourcePath ?: "${program.programName.lowercase()}.py"
            var suffix = 2
            while (!usedPaths.add(sourcePath.lowercase())) {
                sourcePath = "${program.programName.lowercase()}${suffix++}.py"
            }
            val entry = EvoProjectManifest.Entry(sourcePath, program.programName, program.archived)
            val path = EvoProjectManifest.resolveSource(root, sourcePath)
            val localSource = currentSource?.invoke(path) ?: when {
                Files.isRegularFile(path) -> Files.readString(path, StandardCharsets.UTF_8)
                else -> null
            }
            val conflicts = Files.exists(path) &&
                (!Files.isRegularFile(path) || localSource != program.source)
            Target(entry, path, program.source, conflicts)
        }
        val manifest = EvoProjectManifest.renderEntries(targets.map { it.entry }, existing?.alwaysPushAll ?: false)
        return Plan(root, targets, manifest)
    }

    fun write(plan: Plan, overwrite: Boolean = false) {
        if (plan.conflicts.isNotEmpty() && !overwrite) {
            throw EvoProjectManifest.ConfigurationException(
                "Pull would overwrite: ${plan.conflicts.joinToString { it.entry.sourcePath }}; allow overwrite to continue",
            )
        }
        plan.targets.forEach { target ->
            Files.createDirectories(target.path.parent)
            Files.writeString(target.path, target.source, StandardCharsets.UTF_8)
        }
        Files.writeString(
            plan.projectRoot.resolve(EvoProjectManifest.FILE_NAME),
            plan.manifestText,
            StandardCharsets.UTF_8,
        )
        plan.targets.forEach { target ->
            EvoProjectUploadState.markSynchronized(plan.projectRoot, target.entry, target.source)
        }
    }
}

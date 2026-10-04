package com.inspyresoftworks.ti84evo.project

import com.inspyresoftworks.ti84evo.protocol.EvoProtocolException
import com.inspyresoftworks.ti84evo.protocol.EvoPythonLauncher
import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectVerifier
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.protocol.EvoLink
import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.util.concurrent.CancellationException

/** Synchronous push-and-launch workflow shared by IDE execution and host tools. */
class EvoProjectRunner(private val transport: EvoTransport) {
    data class Result(
        val uploadedPrograms: List<String>,
        val skippedPrograms: Int,
        val launch: EvoPythonLauncher.Result,
    )

    fun run(
        project: EvoResolvedProject,
        launchProgram: String,
        onProgress: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result {
        val targetName = launchProgram.trim().uppercase()
        val target = project.programs.singleOrNull {
            it.entry.programName.equals(targetName, ignoreCase = true)
        } ?: throw EvoProtocolException("$targetName is not declared by ${EvoProjectManifest.FILE_NAME}")
        if (target.entry.archived) {
            throw EvoProtocolException("$targetName is configured for Archive; launch targets must be in RAM")
        }
        if (targetName.firstOrNull() !in 'A'..'Z') {
            throw EvoProtocolException("Python launch targets must start with a letter: $targetName")
        }

        checkCancelled(isCancelled)
        onProgress("Comparing the project with calculator RAM…")
        val directory = EvoLink(transport).getDirectory()
        val transferPrograms = project.programs.map {
            EvoPythonTransfer.Program(it.entry.programName, it.source, it.entry.archived)
        }
        val calculatorSources = if (project.alwaysPushAll) emptyMap() else
            EvoPythonProjectVerifier(transport).readSources(directory, transferPrograms)
        val pendingPairs = if (project.alwaysPushAll) {
            project.programs.map { it.entry to it.source }
        } else {
            EvoProjectUploadState.pending(
                project.root,
                project.programs.map { it.entry to it.source },
                directory,
                calculatorSources,
            )
        }
        val pendingPaths = pendingPairs.mapTo(mutableSetOf()) { it.first.sourcePath }
        val pending = project.programs.filter { it.entry.sourcePath in pendingPaths }

        checkCancelled(isCancelled)
        if (pending.isNotEmpty()) {
            transport.close()
            transport.open()
            onProgress("Uploading ${pending.size} changed Python program(s)…")
            EvoPythonTransfer(transport).uploadProject(
                pending.map { EvoPythonTransfer.Program(it.entry.programName, it.source, it.entry.archived) },
                onProgress = { upload, completed, total ->
                    val resolved = pending[completed - 1]
                    EvoProjectUploadState.markUploaded(project.root, resolved.entry, resolved.source)
                    onProgress("Uploaded $completed/$total: ${upload.programName}")
                },
            )
        } else {
            onProgress("Project is current; no upload needed.")
        }

        checkCancelled(isCancelled)
        transport.close()
        transport.open()
        onProgress("Launching $targetName…")
        val launch = EvoPythonLauncher(transport).launch(targetName)
        return Result(
            uploadedPrograms = pending.map { it.entry.programName },
            skippedPrograms = project.programs.size - pending.size,
            launch = launch,
        )
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("TI-84 Evo run cancelled")
    }
}

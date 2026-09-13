package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport

/** Downloads every Python program from a calculator as editable UTF-8 source. */
class EvoPythonProjectPuller(private val transport: EvoTransport) {
    data class Program(
        val programName: String,
        val source: String,
        val archived: Boolean,
        val sourceBytes: Int,
        val payloadBytes: Int,
    ) {
        val location: String get() = if (archived) "ARCHIVE" else "RAM"
    }

    data class Result(val programs: List<Program>) {
        val sourceBytes: Int = programs.sumOf { it.sourceBytes }
        val payloadBytes: Int = programs.sumOf { it.payloadBytes }
    }

    class ProjectPullException(
        val completedPrograms: List<Program>,
        val failedEntry: EvoDirectoryEntry,
        cause: Throwable,
    ) : EvoProtocolException(
        "Project pull stopped at ${failedEntry.name} after ${completedPrograms.size} successful file(s): " +
            (cause.message ?: cause.javaClass.simpleName),
        cause,
    )

    private val link = EvoLink(transport)

    fun pull(onProgress: (Program, Int, Int) -> Unit = { _, _, _ -> }): Result {
        val entries = link.getDirectory()
            .filter { it.type == PYTHON_TYPE }
            .sortedBy { it.name.uppercase() }
        if (entries.isEmpty()) throw EvoProtocolException("calculator contains no Python programs")

        val completed = mutableListOf<Program>()
        for (entry in entries) {
            val program = try {
                val decoded = downloadWithRetry(entry)
                if (!decoded.programName.equals(entry.name, ignoreCase = true)) {
                    throw EvoProtocolException(
                        "directory name ${entry.name} does not match payload name ${decoded.programName}",
                    )
                }
                Program(
                    decoded.programName,
                    decoded.source,
                    entry.archived,
                    decoded.sourceBytes,
                    decoded.payloadBytes,
                )
            } catch (error: Throwable) {
                throw ProjectPullException(completed.toList(), entry, error)
            }
            completed += program
            runCatching { onProgress(program, completed.size, entries.size) }
        }
        return Result(completed)
    }

    private fun downloadWithRetry(entry: EvoDirectoryEntry): EvoPythonPayload.Decoded {
        var lastFailure: RuntimeException? = null
        repeat(READ_ATTEMPTS) { attempt ->
            try {
                transport.close()
                transport.open()
                return EvoPythonPayload.decode(link.getVariable(entry))
            } catch (error: RuntimeException) {
                lastFailure = error
                if (attempt < READ_ATTEMPTS - 1) Thread.sleep(300L * (attempt + 1))
            }
        }
        throw EvoProtocolException(
            "could not download ${entry.name} after $READ_ATTEMPTS attempts: ${lastFailure?.message}",
            lastFailure,
        )
    }

    private companion object {
        const val PYTHON_TYPE = 15
        const val READ_ATTEMPTS = 3
    }
}

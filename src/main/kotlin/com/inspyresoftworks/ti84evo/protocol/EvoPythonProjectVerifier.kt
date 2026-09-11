package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport

/** Reads configured calculator programs so incremental pushes also detect device-side edits. */
class EvoPythonProjectVerifier(private val transport: EvoTransport) {
    private val link = EvoLink(transport)

    fun readSources(
        directory: Collection<EvoDirectoryEntry>,
        programs: Collection<EvoPythonTransfer.Program>,
    ): Map<String, String> = buildMap {
        programs.forEach { program ->
            val entry = directory.singleOrNull {
                it.type == PYTHON_TYPE &&
                    it.name.equals(program.programName, ignoreCase = true) &&
                    it.archived == program.archived
            } ?: return@forEach
            val decoded = downloadWithRetry(entry)
            if (!decoded.programName.equals(entry.name, ignoreCase = true)) {
                throw EvoProtocolException(
                    "directory name ${entry.name} does not match payload name ${decoded.programName}",
                )
            }
            put(program.programName.uppercase(), decoded.source)
        }
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
            "could not verify calculator source for ${entry.name} after $READ_ATTEMPTS attempts: " +
                lastFailure?.message,
            lastFailure,
        )
    }

    private companion object {
        const val PYTHON_TYPE = 15
        const val READ_ATTEMPTS = 3
    }
}

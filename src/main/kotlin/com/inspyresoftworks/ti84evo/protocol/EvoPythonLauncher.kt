package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport

/** Launches a RAM-backed Python source through the Evo Python File Manager. */
class EvoPythonLauncher(
    private val transport: EvoTransport,
    private val waitForPythonMillis: Long = DEFAULT_PYTHON_STARTUP_MILLIS,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    data class Result(
        val programName: String,
        val selectionIndex: Int,
        val availablePrograms: List<String>,
        val packets: Int,
    )

    fun launch(programName: String): Result {
        val requested = programName.trim().uppercase()
        require(EvoPythonPayload.isValidProgramName(requested)) {
            "calculator program name must contain 1–8 letters or digits"
        }
        if (requested.first() !in 'A'..'Z') {
            throw EvoProtocolException("Python launch targets must start with a letter: $requested")
        }

        // gotohome=1 is intentional here: unlike passive browsing, launching
        // needs a deterministic Calculator-app starting point.
        val directory = EvoLink(transport).getDirectory(goHome = true)
        val programs = launchablePrograms(directory)
        val selectionIndex = programs.indexOfFirst { it.equals(requested, ignoreCase = true) }
        if (selectionIndex < 0) {
            val archived = directory.any {
                it.type == PYTHON_TYPE && it.archived && it.name.equals(requested, ignoreCase = true)
            }
            throw EvoProtocolException(
                if (archived) "$requested is archived; Python programs must be in RAM to run"
                else "$requested is not a RAM Python program on the calculator",
            )
        }

        transport.close()
        transport.open()
        val keys = EvoListEditor(transport)
        var packets = keys.sendScancodes(OPEN_PYTHON_FILE_MANAGER)
        sleep(waitForPythonMillis)
        packets += keys.sendScancodes(selectionKeys(programs, requested) + SCAN_RUN)
        return Result(requested, selectionIndex, programs, packets)
    }

    companion object {
        internal const val PYTHON_TYPE = 15
        internal const val SCAN_DOWN = 0x01
        internal const val SCAN_ENTER = 0x09
        internal const val SCAN_PRGM = 0x1F
        internal const val SCAN_RUN = 0x35 // Y=, the File Manager's Run softkey.
        internal val OPEN_PYTHON_FILE_MANAGER = listOf(SCAN_PRGM, SCAN_DOWN, SCAN_ENTER)

        internal fun selectionKeys(programs: List<String>, requested: String): List<Int> {
            val initial = requested.firstOrNull()?.uppercaseChar()
                ?: throw EvoProtocolException("calculator Python program name cannot be empty")
            val initialScan = LETTER_SCANCODES[initial]
                ?: throw EvoProtocolException("Python launch targets must start with a letter: $requested")
            val sameInitial = programs.filter { it.startsWith(initial, ignoreCase = true) }
            val offset = sameInitial.indexOfFirst { it.equals(requested, ignoreCase = true) }
            if (offset < 0) throw EvoProtocolException("$requested is not in the Python File Manager")
            return listOf(initialScan) + List(offset) { SCAN_DOWN }
        }

        internal fun launchablePrograms(directory: Collection<EvoDirectoryEntry>): List<String> = directory
            .asSequence()
            .filter { it.type == PYTHON_TYPE && !it.archived }
            .map(EvoDirectoryEntry::name)
            .distinctBy(String::uppercase)
            .sortedBy(String::uppercase)
            .toList()

        // TI OS GetCSC values for the green A-Z legends on the keypad. The
        // Python File Manager opens in Alpha-lock and uses these for prefix jumps.
        private val LETTER_SCANCODES = mapOf(
            'A' to 0x30, 'B' to 0x27, 'C' to 0x1F, 'D' to 0x2F, 'E' to 0x26,
            'F' to 0x1E, 'G' to 0x16, 'H' to 0x0E, 'I' to 0x2E, 'J' to 0x25,
            'K' to 0x1D, 'L' to 0x15, 'M' to 0x0D, 'N' to 0x2D, 'O' to 0x24,
            'P' to 0x1C, 'Q' to 0x14, 'R' to 0x0C, 'S' to 0x2C, 'T' to 0x23,
            'U' to 0x1B, 'V' to 0x13, 'W' to 0x0B, 'X' to 0x2B, 'Y' to 0x22,
            'Z' to 0x1A,
        )

        private const val DEFAULT_PYTHON_STARTUP_MILLIS = 3_000L
    }
}

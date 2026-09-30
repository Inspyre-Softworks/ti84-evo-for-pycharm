package com.inspyresoftworks.ti84evo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import java.nio.file.Path

class EvoDeveloperToolsDialogTest {
    @Test
    fun javaClasspathWildcardDoesNotBecomeAWindowsPathSegment() {
        val directory = Path.of("C:\\Program Files\\Evo\\lib")
        assertEquals("$directory${java.io.File.separator}*", EvoDeveloperToolsDialog.classpathWildcard(directory))
    }

    @Test
    fun bundledUtilitiesAreAvailableToInstalledPlugin() {
        for (name in listOf("EvoScreenBenchmark.java", "smartpad_macropad.py", "smartpad_usb.py", "requirements-smartpad.txt")) {
            assertNotNull(javaClass.getResourceAsStream("/utils/$name"), name).close()
        }
    }

    @Test
    fun quotedDiagnosticPathsStaySingleArguments() {
        assertEquals(
            listOf("pcap-decode", "C:\\captures\\smartpad trace.pcapng", "--output", "result.log"),
            EvoDeveloperToolsDialog.splitArguments(
                "pcap-decode \"C:\\captures\\smartpad trace.pcapng\" --output result.log",
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            EvoDeveloperToolsDialog.splitArguments("pcap-decode \"unfinished")
        }
    }
}

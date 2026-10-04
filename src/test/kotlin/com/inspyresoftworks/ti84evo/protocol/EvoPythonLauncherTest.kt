package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class EvoPythonLauncherTest {
    @Test
    fun `launchable programs are RAM Python entries in File Manager order`() {
        val directory = listOf(
            entry("ZZPROBE", type = 15),
            entry("TI_DRAW", type = 18, archived = true),
            entry("ARCHIVED", type = 15, archived = true),
            entry("LAUNCHOK", type = 15),
            entry("AAPROBE", type = 15),
            entry("L1", type = 1),
        )

        assertEquals(
            listOf("AAPROBE", "LAUNCHOK", "ZZPROBE"),
            EvoPythonLauncher.launchablePrograms(directory),
        )
    }

    @Test
    fun `Python File Manager is opened from calculator home`() {
        assertEquals(
            listOf(
                EvoPythonLauncher.SCAN_PRGM,
                EvoPythonLauncher.SCAN_DOWN,
                EvoPythonLauncher.SCAN_ENTER,
            ),
            EvoPythonLauncher.OPEN_PYTHON_FILE_MANAGER,
        )
    }

    @Test
    fun `selection jumps by initial then moves within matching programs`() {
        val programs = listOf("AAPROBE", "LAUNCHOK", "LATER", "ZZPROBE")

        assertEquals(listOf(0x30), EvoPythonLauncher.selectionKeys(programs, "AAPROBE"))
        assertEquals(listOf(0x15), EvoPythonLauncher.selectionKeys(programs, "LAUNCHOK"))
        assertEquals(listOf(0x15, EvoPythonLauncher.SCAN_DOWN), EvoPythonLauncher.selectionKeys(programs, "LATER"))
        assertEquals(listOf(0x1A), EvoPythonLauncher.selectionKeys(programs, "ZZPROBE"))
    }

    private fun entry(name: String, type: Int, archived: Boolean = false) = EvoDirectoryEntry(
        name = name,
        type = type,
        size = 1,
        archived = archived,
        tokenName = byteArrayOf(),
    )
}

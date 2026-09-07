package com.inspyresoftworks.ti84evo.project

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class EvoProjectUploadStateTest {
    @Test
    fun `successful upload fingerprint skips only unchanged configuration`() {
        val root = Files.createTempDirectory("evo-upload-state-test")
        val ram = EvoProjectManifest.Entry("main.py", "MAIN")
        val archive = ram.copy(archived = true)

        assertEquals(listOf(ram to "print(1)"), EvoProjectUploadState.pending(root, listOf(ram to "print(1)")))
        EvoProjectUploadState.markUploaded(root, ram, "print(1)")
        assertEquals(emptyList(), EvoProjectUploadState.pending(root, listOf(ram to "print(1)")))
        assertEquals(listOf(ram to "print(2)"), EvoProjectUploadState.pending(root, listOf(ram to "print(2)")))
        assertEquals(listOf(archive to "print(1)"), EvoProjectUploadState.pending(root, listOf(archive to "print(1)")))
    }

    @Test
    fun `unchanged project is pending when its calculator program is missing or in the wrong location`() {
        val root = Files.createTempDirectory("evo-upload-state-calculator-test")
        val entry = EvoProjectManifest.Entry("main.py", "MAIN", archived = true)
        val source = "print(1)"
        EvoProjectUploadState.markUploaded(root, entry, source)

        assertEquals(
            listOf(entry to source),
            EvoProjectUploadState.pending(root, listOf(entry to source), emptyList()),
        )
        assertEquals(
            listOf(entry to source),
            EvoProjectUploadState.pending(
                root,
                listOf(entry to source),
                listOf(calculatorProgram("MAIN", archived = false)),
            ),
        )
        assertEquals(
            emptyList(),
            EvoProjectUploadState.pending(
                root,
                listOf(entry to source),
                listOf(calculatorProgram("main", archived = true)),
            ),
        )
    }

    private fun calculatorProgram(name: String, archived: Boolean) = EvoDirectoryEntry(
        name = name,
        type = 15,
        size = 10,
        archived = archived,
        tokenName = byteArrayOf(1),
    )
}

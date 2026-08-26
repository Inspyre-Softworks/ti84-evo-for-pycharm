package com.inspyresoftworks.ti84evo.project

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
}

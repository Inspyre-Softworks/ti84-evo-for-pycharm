package com.inspyresoftworks.ti84evo.project

import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectPuller
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EvoProjectPullTest {
    @Test
    fun `pull reuses manifest paths preserves archive and synchronizes state`() {
        val root = createTempDirectory("evo-pull-")
        val sourcePath = root.resolve("src/main.py")
        Files.createDirectories(sourcePath.parent)
        Files.writeString(sourcePath, "old source\n")
        val existing = EvoProjectManifest.Configuration(
            listOf(EvoProjectManifest.Entry("src/main.py", "MAIN", archived = false)),
        )
        val program = EvoPythonProjectPuller.Program(
            "MAIN",
            "print('calculator')\n",
            archived = true,
            sourceBytes = 20,
            payloadBytes = 80,
        )

        val plan = EvoProjectPull.plan(root, listOf(program), existing)

        assertEquals(listOf("src/main.py"), plan.conflicts.map { it.entry.sourcePath })
        assertFailsWith<EvoProjectManifest.ConfigurationException> { EvoProjectPull.write(plan) }
        EvoProjectPull.write(plan, overwrite = true)
        assertEquals(program.source, Files.readString(sourcePath))
        assertEquals(
            EvoProjectManifest.Entry("src/main.py", "MAIN", archived = true),
            EvoProjectManifest.parse(Files.readString(root.resolve(EvoProjectManifest.FILE_NAME))).single(),
        )
        assertTrue(
            EvoProjectUploadState.pending(root, listOf(plan.targets.single().entry to program.source)).isEmpty(),
        )
    }

    @Test
    fun `new calculator programs get deterministic Python filenames`() {
        val root = createTempDirectory("evo-pull-")
        val programs = listOf(
            EvoPythonProjectPuller.Program("HELPER", "VALUE = 1\n", false, 10, 70),
            EvoPythonProjectPuller.Program("MAIN", "import helper\n", false, 14, 74),
        )

        val plan = EvoProjectPull.plan(root, programs)

        assertEquals(listOf("helper.py", "main.py"), plan.targets.map { it.entry.sourcePath })
        assertTrue(plan.conflicts.isEmpty())
    }
}

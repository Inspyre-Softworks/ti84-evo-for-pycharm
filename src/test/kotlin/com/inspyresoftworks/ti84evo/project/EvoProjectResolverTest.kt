package com.inspyresoftworks.ti84evo.project

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class EvoProjectResolverTest {
    @Test
    fun `resolves manifest sources relative to the manifest directory`() {
        val manifest = Path.of("project", "configs", EvoProjectManifest.FILE_NAME).toAbsolutePath().normalize()
        val main = manifest.parent.resolve("src/main.py")
        val helper = manifest.parent.resolve("src/helper.py")
        val files = mapOf(
            manifest to "@always-push-all=true\nsrc/main.py=MAIN|RAM\nsrc/helper.py=HELPER|Archive\n",
            main to "print('main')\n",
            helper to "VALUE = 1\n",
        )

        val resolved = EvoProjectResolver.resolve(manifest, files::get)

        assertEquals(manifest.parent, resolved.root)
        assertEquals(true, resolved.alwaysPushAll)
        assertEquals(listOf("MAIN", "HELPER"), resolved.programs.map { it.entry.programName })
        assertEquals(listOf("print('main')\n", "VALUE = 1\n"), resolved.programs.map { it.source })
    }
}

package com.inspyresoftworks.ti84evo.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests the checked-in multi-file Evo project declaration. */
class EvoProjectManifestTest {
    @Test
    fun `parse preserves declared upload order and calculator names`() {
        val entries = EvoProjectManifest.parse(
            """
            # dependencies first
            lib/drawing.py=DRAW
            main.py=MAIN
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                EvoProjectManifest.Entry("lib/drawing.py", "DRAW"),
                EvoProjectManifest.Entry("main.py", "MAIN"),
            ),
            entries,
        )
    }

    @Test
    fun `render gives colliding long filenames unique calculator names`() {
        val rendered = EvoProjectManifest.render(
            listOf("main.py", "utilities_one.py", "utilities_two.py"),
        )

        assertEquals(
            listOf(
                "@always-push-all=false",
                "main.py=MAIN|RAM",
                "utilities_one.py=UTILITIE|RAM",
                "utilities_two.py=UTILITI2|RAM",
            ),
            rendered.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.toList(),
        )
    }

    @Test
    fun `configuration persists archive targets and always rebuild`() {
        val text = EvoProjectManifest.renderEntries(
            listOf(EvoProjectManifest.Entry("main.py", "MAIN", archived = true)),
            alwaysPushAll = true,
        )

        assertEquals(
            EvoProjectManifest.Configuration(
                listOf(EvoProjectManifest.Entry("main.py", "MAIN", archived = true)),
                alwaysPushAll = true,
            ),
            EvoProjectManifest.parseConfiguration(text),
        )
    }

    @Test
    fun `parse rejects duplicate calculator names`() {
        assertFailsWith<EvoProjectManifest.ConfigurationException> {
            EvoProjectManifest.parse("one.py=APP\ntwo.py=app")
        }
    }

    @Test
    fun `resolve rejects a source outside the project`() {
        assertFailsWith<EvoProjectManifest.ConfigurationException> {
            EvoProjectManifest.resolveSource(java.nio.file.Path.of("project"), "../secret.py")
        }
    }
}

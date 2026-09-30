package com.inspyresoftworks.ti84evo.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class EvoChangelogTest {
    @Test
    fun `renders only current version with escaped formatted notes`() {
        val changelog = """
            ## 0.6.0

            - [Bugfix] Add `preview` for A & B.
              Continue with **recording**.
            - [Feature - Production] Add a macro pad.
            - [Bugfix] Fix image orientation.

            ## 0.5.0

            - Earlier <feature>.
        """.trimIndent()

        val html = EvoChangelog.htmlForVersion(changelog, "0.6.0")!!
        assertContains(html, "<code>preview</code>")
        assertContains(html, "A &amp; B")
        assertContains(html, "<b>recording</b>")
        assertContains(html, "<h3>Feature - Production</h3><ul><li>Add a macro pad.</li></ul>")
        assertContains(html, "<h3>Bugfix</h3>")
        assertContains(html, "<li>Fix image orientation.</li>")
        assert(!html.contains("<h3>Docs</h3>"))
        assert(html.indexOf("<h3>Feature - Production</h3>") < html.indexOf("<h3>Bugfix</h3>"))
        assertNull(EvoChangelog.htmlForVersion(changelog, "0.7.0"))
    }
}

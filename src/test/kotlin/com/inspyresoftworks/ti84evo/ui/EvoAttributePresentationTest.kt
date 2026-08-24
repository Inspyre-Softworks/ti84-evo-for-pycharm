package com.inspyresoftworks.ti84evo.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class EvoAttributePresentationTest {
    private val attributes = linkedMapOf<String, Any?>(
        "product" to "23-10-28-2100",
        "id" to 3314348L,
        "total-ram" to 961536L,
        "total-flash" to 3141632L,
        "battery" to 5L,
        "charging" to 1L,
        "bl1-version" to "1.0.0.260",
        "bl2-version" to "7.0.0.3386",
        "bsp-version" to "7.0.0.3992",
        "pkg-version" to "7.0.0.3996",
        "dev-cert" to "no",
        "dbg-cert" to "no",
    )

    @Test
    fun `attributes have friendly grouped values`() {
        val rows = EvoAttributePresentation.rows(attributes)

        assertEquals("961,536 bytes (939.0 KiB)", rows.single { it.key == "total-ram" }.value)
        assertEquals("3,141,632 bytes (3.00 MiB)", rows.single { it.key == "total-flash" }.value)
        assertEquals("Level 5", rows.single { it.key == "battery" }.value)
        assertEquals("Yes", rows.single { it.key == "charging" }.value)
        assertEquals("Not installed", rows.single { it.key == "dev-cert" }.value)
        assertEquals("Firmware", rows.single { it.key == "pkg-version" }.section)
    }

    @Test
    fun `markdown is readable and preserves raw protocol values`() {
        val markdown = EvoAttributePresentation.toMarkdown(attributes)

        assertContains(markdown, "# TI-84 Evo Attributes")
        assertContains(markdown, "| Total RAM | 961,536 bytes (939.0 KiB) | `total-ram` |")
        assertContains(markdown, "| Charging | Yes | `charging` |")
        assertContains(markdown, "## Raw protocol values")
        assertContains(markdown, "charging: 1")
        assertContains(markdown, "pkg-version: 7.0.0.3996")
    }
}

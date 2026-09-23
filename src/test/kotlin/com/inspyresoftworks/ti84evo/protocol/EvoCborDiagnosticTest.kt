package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvoCborDiagnosticTest {
    @Test
    fun `unknown fields and byte strings remain visible`() {
        val rendered = EvoCborDiagnostic.render(
            linkedMapOf(
                "known" to 7L,
                "future-field" to byteArrayOf(0x00, 0x7F, 0xFF.toByte()),
                "nested" to listOf(true, null),
            ),
        )

        assertTrue(rendered.contains("\"future-field\": h'007FFF'"))
        assertTrue(rendered.contains("\"nested\""))
        assertTrue(rendered.contains("true"))
        assertTrue(rendered.contains("null"))
    }

    @Test
    fun `raw CBOR is decoded before rendering`() {
        assertEquals("{\n  \"x\": 1\n}", EvoCborDiagnostic.decodeAndRender(byteArrayOf(0xA1.toByte(), 0x61, 0x78, 0x01)))
    }
}

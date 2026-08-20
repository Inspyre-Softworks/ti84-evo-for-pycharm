package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class EvoResourceCodecTest {
    @Test
    fun `ff run decoder expands printable run marker`() {
        val encoded = byteArrayOf(0x10, 0x7E, 0x23, 0xFF.toByte(), 0x11)
        val expected = byteArrayOf(0x10, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x11)
        assertContentEquals(expected, EvoResourceCodec.decodeEvoScreenFfRuns(encoded, expected.size))
    }
}

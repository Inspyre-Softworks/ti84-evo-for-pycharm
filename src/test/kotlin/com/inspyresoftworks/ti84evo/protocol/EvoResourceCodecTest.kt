package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvoResourceCodecTest {
    @Test
    fun `zero length announcement accepts dynamic resource payload`() {
        val decoded = byteArrayOf(0xBF.toByte(), 0x64, 0x61, 0x74, 0x61, 0xFF.toByte())
        val wire = KermitPacketCodec.encodeData(decoded)

        val resolved = EvoResourceCodec.resolve(0, byteArrayOf(), wire)

        assertContentEquals(decoded, resolved.bytes)
        assertEquals("D-kermit:unannounced", resolved.mode)
    }

    @Test
    fun `nonzero length announcement remains strict`() {
        assertFailsWith<EvoProtocolException> {
            EvoResourceCodec.resolve(7, byteArrayOf(1, 2), byteArrayOf(1, 2, 3))
        }
    }

    @Test
    fun `ff run decoder expands printable run marker`() {
        val encoded = byteArrayOf(0x10, 0x7E, 0x23, 0xFF.toByte(), 0x11)
        val expected = byteArrayOf(0x10, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x11)
        assertContentEquals(expected, EvoResourceCodec.decodeEvoScreenFfRuns(encoded, expected.size))
    }
}

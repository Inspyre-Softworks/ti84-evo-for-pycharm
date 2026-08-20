package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Kermit long-packet and data-quoting fixture tests.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class KermitPacketCodecTest {
    @Test
    fun `long F packet round trips`() {
        val session = KermitPacketCodec.Session()
        val data = EvoPythonPayload.transferUrl("TAYEVO").encodeToByteArray()
        val raw = KermitPacketCodec.makePacket(1, 'F', data, session)
        val parsed = KermitPacketCodec.parsePacket(raw, session)

        assertEquals(1, parsed.sequence)
        assertEquals('F', parsed.type)
        assertContentEquals(data, parsed.data)
        assertEquals(0x20, raw[1].toInt() and 0xFF)
    }

    @Test
    fun `binary data encoding preserves element boundaries`() {
        val payload = byteArrayOf(0x01, 0x01, 0x01, 0x23, 0x7E, 0x0D, 0x41)
        val chunks = KermitPacketCodec.encodeDataChunks(payload, 4)

        assertEquals(
            KermitPacketCodec.encodeData(payload).toList(),
            chunks.flatMap(ByteArray::toList),
        )
        assert(chunks.all { it.size <= 4 })
    }
}

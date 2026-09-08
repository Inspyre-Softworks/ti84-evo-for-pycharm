package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Kermit long-packet and data-quoting fixture tests.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class KermitPacketCodecTest {
    @Test
    fun `send-init packet matches confirmed calculator vector`() {
        val sendInit = byteArrayOf(
            0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
            0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
        )
        val expected = byteArrayOf(
            0x01, 0x30, 0x20, 0x53, 0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23,
            0x59, 0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D, 0x3E, 0x0D,
        )

        assertContentEquals(expected, KermitPacketCodec.makePacket(0, 'S', sendInit))
    }

    @Test
    fun `data codec round trips quoted and repeated bytes`() {
        val payload = byteArrayOf(
            0x23,
            0x23,
            0x23,
            0x7E,
            0x7E,
            0x7E,
            0x00,
            0x7F,
            0xFF.toByte(),
            'A'.code.toByte(),
            'A'.code.toByte(),
            'A'.code.toByte(),
        )

        assertContentEquals(payload, KermitPacketCodec.decodeData(KermitPacketCodec.encodeData(payload)))
    }

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
    fun `resource compatibility accepts an opaque extended AUX byte`() {
        val data = ByteArray(100) { ('A'.code + it % 26).toByte() }
        val raw = KermitPacketCodec.makePacket(3, 'D', data)
        raw[6] = 0x0D
        raw[raw.lastIndex - 1] = checksum1(raw.copyOfRange(1, raw.lastIndex - 1)).toByte()

        assertFailsWith<EvoFrameException> { KermitPacketCodec.parsePacket(raw) }
        val parsed = KermitPacketCodec.parsePacket(raw, validateExtendedHeaderCheck = false)

        assertEquals(3, parsed.sequence)
        assertEquals('D', parsed.type)
        assertContentEquals(data, parsed.data)
    }

    @Test
    fun `resource quoting preserves literal repeat markers`() {
        val payload = byteArrayOf(0x00, 0x01, 0x0D, 0x23, 0x7E, 0x41)

        assertContentEquals(
            payload,
            KermitPacketCodec.decodeResourceData(KermitPacketCodec.encodeResourceData(payload)),
        )
        assertEquals(0x7E, KermitPacketCodec.encodeResourceData(payload)[7].toInt() and 0xFF)
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

    @Test
    fun `file length attributes round trip`() {
        val attributes = KermitPacketCodec.buildFileAttributes(153_635)

        assertEquals(153_635, KermitPacketCodec.parseFileLengthAttributes(attributes))
    }

    private fun checksum1(data: ByteArray): Int {
        val sum = data.sumOf { it.toInt() and 0xFF } and 0xFFFF
        return 0x20 + ((sum + ((sum shr 6) and 3)) and 0x3F)
    }
}

package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EvoListEditorTest {
    @Test
    fun `scancode payload uses the calculators indefinite CBOR array`() {
        assertContentEquals(
            byteArrayOf(0x9F.toByte(), 0x09, 0xFF.toByte()),
            EvoListEditor.scancodePayload(0x09),
        )
        assertContentEquals(
            byteArrayOf(0x9F.toByte(), 0x18, 0x36, 0xFF.toByte()),
            EvoListEditor.scancodePayload(0x36),
        )
    }

    @Test
    fun `default editor restoration sends quit and setup editor keys in one session`() {
        val transport = AckingTransport()

        EvoListEditor(transport).restoreDefaultColumns()

        assertEquals(1, transport.types.count { it == 'S' })
        assertEquals(1, transport.types.count { it == 'B' })
        assertEquals(
            List(EvoListEditor.RESET_TO_DEFAULT_COLUMNS.size) { EvoListEditor.SCANCODE_ENDPOINT },
            transport.fileDescriptors,
        )
        assertEquals(
            EvoListEditor.RESET_TO_DEFAULT_COLUMNS,
            transport.dataPayloads.map { payload ->
                val decoded = KermitPacketCodec.decodeData(payload)
                if (decoded.size == 3) decoded[1].toInt() else decoded[2].toInt()
            },
        )
    }

    private class AckingTransport : EvoTransport {
        override val description = "test"
        val types = mutableListOf<Char>()
        val fileDescriptors = mutableListOf<String>()
        val dataPayloads = mutableListOf<ByteArray>()
        private val responses = ArrayDeque<ByteArray>()
        private val session = KermitPacketCodec.Session()
        private val sendInit = byteArrayOf(
            0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
            0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
        )

        override fun open() = Unit
        override fun close() = Unit

        override fun write(data: ByteArray) {
            val packet = KermitPacketCodec.parsePacket(data, session)
            types += packet.type
            if (packet.type == 'F') fileDescriptors += packet.data.decodeToString()
            if (packet.type == 'D') dataPayloads += packet.data
            val ackData = if (packet.type == 'S') sendInit else byteArrayOf()
            responses += KermitPacketCodec.makePacket(packet.sequence, 'Y', ackData, session)
            if (packet.type == 'S') session.updateFromSendInit(sendInit)
        }

        override fun readPacketBytes(): ByteArray = responses.removeFirst()
    }
}

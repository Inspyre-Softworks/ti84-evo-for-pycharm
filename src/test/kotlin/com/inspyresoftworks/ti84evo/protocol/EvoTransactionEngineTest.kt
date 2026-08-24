package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class EvoTransactionEngineTest {
    @Test
    fun `calculator error payload is preserved`() {
        val responses = ArrayDeque(
            listOf(
                ack(0x20, byteArrayOf(0x7E, 0x25, 0x20, 0x40, 0x2D, 0x23, 0x59, 0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D)),
                ack(0x21, "hh01/get/hh01/sys/attributes".encodeToByteArray()),
                ack(0x22, byteArrayOf('Y'.code.toByte())),
                ack(0x23),
                EvoFrameCodec.encode(EvoFrame(0x24, 'E'.code, "BZ".encodeToByteArray())),
            ),
        )
        val transport = object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) = Unit
            override fun readFrameBytes(): ByteArray = responses.removeFirst()
            override fun readPacketBytes(): ByteArray = error("not used")
        }

        val error = assertFailsWith<EvoUnexpectedFrameException> {
            EvoTransactionEngine(transport).sendSmallTransaction(
                "hh01/get/hh01/sys/attributes".encodeToByteArray(),
                byteArrayOf('h'.code.toByte()),
            )
        }

        assertContains(error.message.orEmpty(), "got E: calculator error BZ")
    }

    private fun ack(sequence: Int, payload: ByteArray = byteArrayOf()): ByteArray =
        EvoFrameCodec.encode(EvoFrame(sequence, EvoFrameCodec.CMD_Y, payload))
}

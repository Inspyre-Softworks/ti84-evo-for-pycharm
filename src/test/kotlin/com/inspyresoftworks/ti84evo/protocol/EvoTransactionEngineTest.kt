package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class EvoTransactionEngineTest {
    private val sendInit = byteArrayOf(
        0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
        0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )
    private val sendInitAck = sendInit.copyOf().also { it[1] = 0x25 }

    @Test
    fun `calculator error payload is preserved`() {
        val request = "hh01/get/hh01/sys/attributes".encodeToByteArray()
        val responses = ArrayDeque(
            listOf(
                packet(0, 'Y', sendInitAck),
                packet(1, 'Y', request),
                packet(2, 'Y', byteArrayOf('Y'.code.toByte())),
                packet(3, 'Y'),
                packet(4, 'E', "BZ".encodeToByteArray()),
            ),
        )
        val transport = QueueTransport(responses)

        val error = assertFailsWith<EvoUnexpectedFrameException> {
            EvoTransactionEngine(transport).sendSmallTransaction(request, byteArrayOf('h'.code.toByte()))
        }

        assertContains(error.message.orEmpty(), "got E: calculator error BZ")
    }

    @Test
    fun `non-error unexpected packet does not duplicate command text`() {
        val responses = ArrayDeque(listOf(packet(0, 'Z')))
        val transport = QueueTransport(responses)

        val error = assertFailsWith<EvoUnexpectedFrameException> {
            EvoTransactionEngine(transport).sendSmallTransaction(
                "hh01/get/hh01/sys/attributes".encodeToByteArray(),
                byteArrayOf('h'.code.toByte()),
            )
        }

        val message = error.message.orEmpty()
        assertContains(message, "got Z")
        assertFalse(message.contains("got Z: Z"))
    }

    @Test
    fun `reverse transaction wraps logical sequence after 63`() {
        val resource = ByteArray(61) { index -> ('A'.code + index % 26).toByte() }
        val responses = ArrayDeque<ByteArray>().apply {
            add(packet(0, 'S', sendInit))
            add(packet(1, 'F', "screen".encodeToByteArray()))
            add(packet(2, 'A', KermitPacketCodec.buildFileAttributes(resource.size)))
            resource.forEachIndexed { index, byte ->
                add(packet((index + 3) % 64, 'D', KermitPacketCodec.encodeData(byteArrayOf(byte))))
            }
            add(packet(0, 'Z'))
            add(packet(1, 'B'))
        }
        val transport = QueueTransport(responses)

        val (_, received) = EvoTransactionEngine(transport).receiveTransaction()

        assertContentEquals(resource, received)
        assertContentEquals(
            byteArrayOf(0x5D, 0x5E, 0x5F, 0x20, 0x21),
            transport.writes.takeLast(5).map { it[2] }.toByteArray(),
        )
        assertEquals(66, transport.writes.size)
    }

    private fun packet(sequence: Int, type: Char, data: ByteArray = byteArrayOf()): ByteArray =
        KermitPacketCodec.makePacket(sequence, type, data)

    private class QueueTransport(private val responses: ArrayDeque<ByteArray>) : EvoTransport {
        override val description = "test"
        val writes = mutableListOf<ByteArray>()

        override fun open() = Unit
        override fun close() = Unit
        override fun write(data: ByteArray) {
            writes += data
        }
        override fun readPacketBytes(): ByteArray = responses.removeFirst()
    }
}

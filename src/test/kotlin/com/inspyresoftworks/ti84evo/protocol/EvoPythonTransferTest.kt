package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Host-to-Evo transfer flow test using an in-memory Kermit peer.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoPythonTransferTest {
    @Test
    fun `upload sends complete Kermit transaction`() {
        val peer = AckingTransport()
        val result = EvoPythonTransfer(peer).upload("HELLO", "print('hello')\n")

        assertEquals("HELLO", result.programName)
        assertTrue(result.payloadBytes > result.sourceBytes)
        assertEquals('S', peer.types.first())
        assertEquals('B', peer.types.last())
        assertTrue('F' in peer.types)
        assertTrue('A' in peer.types)
        assertTrue('D' in peer.types)
        assertTrue('Z' in peer.types)
        assertTrue(peer.fileDescriptor.contains("type=15"))
        assertTrue(peer.fileDescriptor.contains("memtarget=0"))
        assertTrue(peer.fileDescriptor.contains("policy=1"))
    }

    @Test
    fun `project upload sends every program in one transport session`() {
        val peer = AckingTransport()
        val result = EvoPythonTransfer(peer).uploadProject(
            listOf(
                EvoPythonTransfer.Program("HELPER", "VALUE = 42\n"),
                EvoPythonTransfer.Program("MAIN", "import helper\nprint(helper.VALUE)\n"),
            ),
        )

        assertEquals(listOf("HELPER", "MAIN"), result.uploads.map { it.programName })
        assertEquals(listOf('S', 'B', 'S', 'B'), peer.types.filter { it == 'S' || it == 'B' })
        assertEquals(2, peer.fileDescriptors.size)
        assertTrue(peer.fileDescriptors.all { "type=15" in it })
    }

    @Test
    fun `project upload honors each programs archive target and reports progress`() {
        val peer = AckingTransport()
        val progress = mutableListOf<String>()
        EvoPythonTransfer(peer).uploadProject(
            listOf(
                EvoPythonTransfer.Program("RAMAPP", "print(1)", archived = false),
                EvoPythonTransfer.Program("ARCAPP", "print(2)", archived = true),
            ),
            onProgress = { result, completed, total -> progress += "$completed/$total:${result.programName}" },
        )

        assertTrue("memtarget=0" in peer.fileDescriptors[0])
        assertTrue("memtarget=1" in peer.fileDescriptors[1])
        assertEquals(listOf("1/2:RAMAPP", "2/2:ARCAPP"), progress)
    }

    private class AckingTransport : EvoTransport {
        override val description: String = "test"
        val types = mutableListOf<Char>()
        var fileDescriptor = ""
        val fileDescriptors = mutableListOf<String>()
        private val responses = ArrayDeque<ByteArray>()
        private val session = KermitPacketCodec.Session()
        private val sendInit = byteArrayOf(
            0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
            0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
        )

        override fun open() = Unit
        override fun close() = Unit
        override fun readFrameBytes(): ByteArray = error("not used")

        override fun write(data: ByteArray) {
            val packet = KermitPacketCodec.parsePacket(data, session)
            types += packet.type
            if (packet.type == 'F') {
                fileDescriptor = packet.data.decodeToString()
                fileDescriptors += fileDescriptor
            }
            val ackData = if (packet.type == 'S') sendInit else byteArrayOf()
            responses += KermitPacketCodec.makePacket(packet.sequence, 'Y', ackData, session)
            if (packet.type == 'S') {
                session.updateFromSendInit(sendInit)
            }
        }

        override fun readPacketBytes(): ByteArray = responses.removeFirst()
    }
}

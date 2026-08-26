package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EvoLinkTest {
    @Test
    fun `system resource is normalized into the hh01 namespace`() {
        assertContentEquals(
            "hh01/get/hh01/sys/screen".encodeToByteArray(),
            buildGetRequest("sys/screen"),
        )
    }

    @Test
    fun `directory resource retains its nested hh01 path`() {
        assertContentEquals(
            "hh01/get/hh01/inf/res?name=directory&gotohome=1".encodeToByteArray(),
            buildGetRequest("hh01/inf/res?name=directory&gotohome=1"),
        )
    }

    @Test
    fun `delete request percent encodes the calculator token name`() {
        val entry = EvoDirectoryEntry(
            name = "ASTRCALC",
            type = 2,
            size = 42,
            archived = false,
            tokenName = tokenWords(0xE800, 0xE812, 0xE813, 0xE811, 0xE802, 0xE800, 0xE80B, 0xE802),
        )

        assertEquals(
            "hh01/del/var?name=%EE%A0%80%EE%A0%92%EE%A0%93%EE%A0%91" +
                "%EE%A0%82%EE%A0%80%EE%A0%8B%EE%A0%82&type=2",
            buildDeleteRequest(entry).decodeToString(),
        )
    }

    @Test
    fun `delete request refuses an empty token name`() {
        val entry = EvoDirectoryEntry("unknown", 0, 0, false, byteArrayOf())

        assertFailsWith<EvoProtocolException> { buildDeleteRequest(entry) }
    }

    @Test
    fun `delete variables sends one transaction for each selected entry`() {
        val ramName = tokenWords(0xE811, 0xE800, 0xE80C)
        val archiveName = tokenWords(0xE800, 0xE811, 0xE802)
        val entries = listOf(
            EvoDirectoryEntry("RAM", 15, 10, false, ramName),
            EvoDirectoryEntry("ARC", 15, 11, true, archiveName),
        )
        val responses = ArrayDeque<ByteArray>().apply {
            entries.forEach { addAll(sendAcks(buildDeleteRequest(it).decodeToString())) }
        }
        val writes = mutableListOf<EvoFrame>()
        val transport = object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) {
                writes += EvoFrameCodec.decode(data)
            }
            override fun readFrameBytes(): ByteArray = responses.removeFirst()
            override fun readPacketBytes(): ByteArray = error("not used")
        }

        val deleted = EvoLink(transport).deleteVariables(entries)

        assertEquals(listOf("RAM", "ARC"), deleted.map { it.name })
        val deleteRequests = writes
            .filter { it.command == EvoFrameCodec.CMD_F }
            .map { it.payload.decodeToString() }
            .filter { it.startsWith("hh01/del/") }
        assertEquals(2, deleteRequests.size)
        assertTrue(deleteRequests[0].contains("%EE%A0%91%EE%A0%80%EE%A0%8C"))
        assertTrue(deleteRequests[1].contains("%EE%A0%80%EE%A0%91%EE%A0%82"))
        assertTrue(responses.isEmpty())
    }

    private fun tokenWords(vararg words: Int): ByteArray = ByteArray(words.size * 2).also { bytes ->
        words.forEachIndexed { index, word ->
            bytes[index * 2] = word.toByte()
            bytes[index * 2 + 1] = (word ushr 8).toByte()
        }
    }

    private fun sendAcks(request: String): List<ByteArray> = listOf(
        ack(0x20, byteArrayOf(0x7E, 0x25, 0x20, 0x40, 0x2D, 0x23, 0x59, 0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D)),
        ack(0x21, request.encodeToByteArray()),
        ack(0x22, byteArrayOf('Y'.code.toByte())),
        ack(0x23),
        ack(0x24),
        ack(0x25),
    )

    private fun receiveTransaction(data: ByteArray): List<ByteArray> = listOf(
        frame(0x20, EvoFrameCodec.CMD_S, byteArrayOf(0x7E, 0x30)),
        frame(0x21, EvoFrameCodec.CMD_F, "directory".encodeToByteArray()),
        frame(0x22, EvoFrameCodec.CMD_A, EvoFrameCodec.buildLengthAnnouncement(data.size)),
        frame(0x23, EvoFrameCodec.CMD_D, data),
        frame(0x24, EvoFrameCodec.CMD_Z),
        frame(0x25, EvoFrameCodec.CMD_B),
    )

    private fun directoryEntry(tokenName: ByteArray, archived: Boolean): ByteArray = cborMap(
        "tokName" to cborBytes(tokenName),
        "type" to cborUnsigned(15),
        "size" to cborUnsigned(10),
        "mem" to cborBoolean(archived),
    )

    private fun ack(sequence: Int, payload: ByteArray = byteArrayOf()): ByteArray =
        frame(sequence, EvoFrameCodec.CMD_Y, payload)

    private fun frame(sequence: Int, command: Int, payload: ByteArray = byteArrayOf()): ByteArray =
        EvoFrameCodec.encode(EvoFrame(sequence, command, payload))

    private fun cborMap(vararg entries: Pair<String, ByteArray>): ByteArray = concat(
        cborLength(5, entries.size),
        *entries.flatMap { (key, value) -> listOf(cborText(key), value) }.toTypedArray(),
    )

    private fun cborArray(vararg values: ByteArray): ByteArray = concat(cborLength(4, values.size), *values)

    private fun cborText(value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        return concat(cborLength(3, bytes.size), bytes)
    }

    private fun cborBytes(value: ByteArray): ByteArray = concat(cborLength(2, value.size), value)

    private fun cborUnsigned(value: Int): ByteArray = cborLength(0, value)

    private fun cborBoolean(value: Boolean): ByteArray =
        byteArrayOf(if (value) 0xF5.toByte() else 0xF4.toByte())

    private fun cborLength(major: Int, value: Int): ByteArray = when {
        value < 24 -> byteArrayOf(((major shl 5) or value).toByte())
        value < 256 -> byteArrayOf(((major shl 5) or 24).toByte(), value.toByte())
        else -> byteArrayOf(((major shl 5) or 25).toByte(), (value ushr 8).toByte(), value.toByte())
    }

    private fun concat(vararg values: ByteArray): ByteArray {
        val result = ByteArray(values.sumOf { it.size })
        var offset = 0
        values.forEach { value ->
            value.copyInto(result, offset)
            offset += value.size
        }
        return result
    }
}

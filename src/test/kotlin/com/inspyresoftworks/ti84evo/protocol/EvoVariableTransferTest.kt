package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvoVariableTransferTest {
    @Test
    fun `temporary list name avoids calculator variables already in use`() {
        val transfer = EvoVariableTransfer(object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) = Unit
            override fun readPacketBytes(): ByteArray = error("not used")
        })

        assertEquals("Z0002", transfer.temporaryListName(listOf("z0000", "Z0001", "L1")))
    }

    @Test
    fun `archive transfer targets calculator Archive with overwrite policy`() {
        val transfer = EvoVariableTransfer(object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) = Unit
            override fun readPacketBytes(): ByteArray = error("not used")
        })

        assertEquals("hh01/xfr/var?memtarget=1&policy=1", transfer.archiveTransferUrl())
    }

    @Test
    fun `archive keeps entry when upload final ack is lost but directory shows archived`() {
        val entry = EvoDirectoryEntry("A", type = 0, size = 8, archived = false, tokenName = tokenWords(0xE800))
        val rawVariable = byteArrayOf(0xA1.toByte(), 0x61, 0x41, 0x01)
        val responses = ArrayDeque<ByteArray>().apply {
            addAll(variableRead(entry, rawVariable))
            add(ack(0, sendInitAck))
            add(ack(1))
            add(ack(2))
            add(ack(3))
            add(ack(4))
            repeat(3) { add(KermitPacketCodec.makePacket(5, 'N')) }
            addAll(directoryRead(directoryEntry(entry.tokenName, archived = true, type = entry.type, size = 10)))
        }
        val transport = object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) = Unit
            override fun readPacketBytes(): ByteArray = responses.removeFirst()
        }

        val archived = EvoVariableTransfer(transport).archiveVariables(listOf(entry))

        assertEquals(1, archived.size)
        assertTrue(archived.single().entry.archived)
        assertEquals("A", archived.single().entry.name)
        assertEquals(0, archived.single().packets)
        assertTrue(responses.isEmpty())
    }

    private val sendInit = byteArrayOf(
        0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
        0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )

    private val sendInitAck = byteArrayOf(
        0x7E, 0x25, 0x20, 0x40, 0x2D, 0x23, 0x59,
        0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )

    private fun tokenWords(vararg words: Int): ByteArray = ByteArray(words.size * 2).also { bytes ->
        words.forEachIndexed { index, word ->
            bytes[index * 2] = word.toByte()
            bytes[index * 2 + 1] = (word ushr 8).toByte()
        }
    }

    private fun variableRead(entry: EvoDirectoryEntry, payload: ByteArray): List<ByteArray> {
        val request = buildGetRequest("hh01/xfr/${buildVariableResourceName(entry)}").decodeToString()
        return resourceRead(request, payload)
    }

    private fun directoryRead(vararg entries: ByteArray): List<ByteArray> {
        val request = buildGetRequest("hh01/inf/res?name=directory&gotohome=1").decodeToString()
        val payload = cborMap("data" to cborArray(*entries))
        return resourceRead(request, payload)
    }

    private fun resourceRead(request: String, payload: ByteArray): List<ByteArray> =
        sendAcks(request) + listOf(
            KermitPacketCodec.makePacket(0, 'S', sendInit),
            KermitPacketCodec.makePacket(1, 'F', "directory".encodeToByteArray()),
            KermitPacketCodec.makePacket(2, 'A', KermitPacketCodec.buildFileAttributes(payload.size)),
            KermitPacketCodec.makePacket(3, 'D', KermitPacketCodec.encodeData(payload)),
            KermitPacketCodec.makePacket(4, 'Z'),
            KermitPacketCodec.makePacket(5, 'B'),
        )

    private fun sendAcks(request: String): List<ByteArray> = listOf(
        ack(0, sendInitAck),
        ack(1, request.encodeToByteArray()),
        ack(2, byteArrayOf('Y'.code.toByte())),
        ack(3),
        ack(4),
        ack(5),
    )

    private fun ack(sequence: Int, payload: ByteArray = byteArrayOf()): ByteArray =
        KermitPacketCodec.makePacket(sequence, 'Y', payload)

    private fun directoryEntry(
        tokenName: ByteArray,
        archived: Boolean,
        type: Int = 15,
        size: Int = 10,
    ): ByteArray = cborMap(
        "tokName" to cborBytes(tokenName),
        "type" to cborUnsigned(type),
        "size" to cborUnsigned(size),
        "mem" to cborBoolean(archived),
    )

    private fun cborMap(vararg entries: Pair<String, ByteArray>): ByteArray = concat(
        cborLength(5, entries.size),
        *entries.flatMap { (key, value) -> listOf(cborText(key), value) }.toTypedArray(),
    )

    private fun cborArray(vararg values: ByteArray): ByteArray = concat(cborLength(4, values.size), *values)

    private fun cborText(value: String): ByteArray = value.encodeToByteArray().let { bytes ->
        concat(cborLength(3, bytes.size), bytes)
    }

    private fun cborBytes(value: ByteArray): ByteArray = concat(cborLength(2, value.size), value)

    private fun cborBoolean(value: Boolean): ByteArray = byteArrayOf(if (value) 0xF5.toByte() else 0xF4.toByte())

    private fun cborUnsigned(value: Int): ByteArray = when {
        value < 24 -> byteArrayOf(value.toByte())
        value <= 0xFF -> byteArrayOf(0x18, value.toByte())
        else -> byteArrayOf(0x19, (value shr 8).toByte(), value.toByte())
    }

    private fun cborLength(major: Int, size: Int): ByteArray = when {
        size < 24 -> byteArrayOf(((major shl 5) or size).toByte())
        size <= 0xFF -> byteArrayOf(((major shl 5) or 24).toByte(), size.toByte())
        else -> byteArrayOf(((major shl 5) or 25).toByte(), (size shr 8).toByte(), size.toByte())
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }
}

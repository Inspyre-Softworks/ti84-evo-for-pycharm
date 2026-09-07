package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `variable download resource preserves its type and tokenized name`() {
        val entry = EvoDirectoryEntry("L1", 1, 24, false, tokenWords(0xE401))

        assertEquals(
            "var?name=%EE%90%81&type=1",
            buildVariableResourceName(entry),
        )
    }

    @Test
    fun `delete variables use negotiated data encoding and verify each selected entry`() {
        val ramName = tokenWords(0xE811, 0xE800, 0xE80C)
        val archiveName = tokenWords(0xE800, 0xE811, 0xE802)
        val entries = listOf(
            EvoDirectoryEntry("RAM", 15, 10, false, ramName),
            EvoDirectoryEntry("ARC", 15, 11, true, archiveName),
        )
        val responses = ArrayDeque<ByteArray>().apply {
            addAll(sendAcks(buildDeleteRequest(entries[0]).decodeToString()))
            addAll(directoryRead(directoryEntry(archiveName, archived = true)))
            addAll(sendAcks(buildDeleteRequest(entries[1]).decodeToString()))
            addAll(directoryRead())
        }
        val writes = mutableListOf<KermitPacketCodec.Packet>()
        val session = KermitPacketCodec.Session()
        val transport = object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) {
                val packet = KermitPacketCodec.parsePacket(data, session)
                writes += packet
                if (packet.type == 'S') session.updateFromSendInit(sendInitAck)
            }
            override fun readPacketBytes(): ByteArray = responses.removeFirst()
        }

        val progress = mutableListOf<String>()
        val deleted = EvoLink(transport).deleteVariables(entries) { entry, completed, total ->
            progress += "$completed/$total:${entry.name}"
        }

        assertEquals(listOf("RAM", "ARC"), deleted.map { it.name })
        assertEquals(listOf("1/2:RAM", "2/2:ARC"), progress)
        val deleteRequests = writes
            .filter { it.type == 'F' }
            .map { it.data.decodeToString() }
            .filter { it.startsWith("hh01/del/") }
        assertEquals(2, deleteRequests.size)
        assertTrue(deleteRequests[0].contains("%EE%A0%91%EE%A0%80%EE%A0%8C"))
        assertTrue(deleteRequests[1].contains("%EE%A0%80%EE%A0%91%EE%A0%82"))
        assertContentEquals(
            KermitPacketCodec.encodeData(byteArrayOf(0)),
            writes.first { it.type == 'D' }.data,
        )
        assertTrue(responses.isEmpty())
    }

    @Test
    fun `delete variables reports entries completed before a later failure`() {
        val first = EvoDirectoryEntry("FIRST", 15, 10, false, tokenWords(0xE801))
        val second = EvoDirectoryEntry("SECOND", 15, 11, false, tokenWords(0xE802))
        val responses = ArrayDeque<ByteArray>().apply {
            addAll(sendAcks(buildDeleteRequest(first).decodeToString()))
            addAll(directoryRead(directoryEntry(second.tokenName, archived = false)))
            repeat(2) {
                addAll(deleteFailure("denied"))
                addAll(directoryRead(directoryEntry(second.tokenName, archived = false)))
            }
        }
        val session = KermitPacketCodec.Session()
        val transport = object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) {
                val packet = KermitPacketCodec.parsePacket(data, session)
                if (packet.type == 'S') session.updateFromSendInit(sendInitAck)
            }
            override fun readPacketBytes(): ByteArray = responses.removeFirst()
        }

        val error = assertFailsWith<EvoVariableDeleteException> {
            EvoLink(transport).deleteVariables(listOf(first, second))
        }

        assertEquals("SECOND", error.failedEntry.name)
        assertEquals(listOf("FIRST"), error.deletedEntries.map { it.name })
        assertTrue(responses.isEmpty())
    }

    @Test
    fun `acknowledged delete is rejected when the calculator directory still contains the variable`() {
        val entry = EvoDirectoryEntry("STUBBORN", 15, 10, false, tokenWords(0xE812))
        val responses = ArrayDeque<ByteArray>().apply {
            repeat(2) {
                addAll(sendAcks(buildDeleteRequest(entry).decodeToString()))
                addAll(directoryRead(directoryEntry(entry.tokenName, archived = false)))
            }
        }
        val progress = mutableListOf<String>()
        val writes = mutableListOf<KermitPacketCodec.Packet>()
        val session = KermitPacketCodec.Session()
        val transport = object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) {
                val packet = KermitPacketCodec.parsePacket(data, session)
                writes += packet
                if (packet.type == 'S') session.updateFromSendInit(sendInitAck)
            }
            override fun readPacketBytes(): ByteArray = responses.removeFirst()
        }

        val error = assertFailsWith<EvoVariableDeleteException> {
            EvoLink(transport).deleteVariables(listOf(entry)) { deleted, _, _ ->
                progress += deleted.name
            }
        }

        assertEquals("STUBBORN", error.failedEntry.name)
        assertTrue(error.deletedEntries.isEmpty())
        assertTrue(progress.isEmpty())
        assertEquals(
            2,
            writes.count { it.type == 'F' && it.data.decodeToString().startsWith("hh01/del/") },
        )
        assertTrue(responses.isEmpty())
    }

    @Test
    fun `built-in list deletion clears and retains the list slot`() {
        val nativeTokenName = tokenWords(0xE830)
        val entry = EvoDirectoryEntry("L1", 1, 24, false, nativeTokenName + byteArrayOf(0, 0))
        val populatedData = tokenWords(0x00E5, 0x0001, 0x0001, 0x001F, 0x00D9) + byteArrayOf(0, 0, 0)
        val populated = nativeList(entry.tokenName, length = 1, data = populatedData)
        val empty = nativeList(entry.tokenName, length = 0, data = byteArrayOf())
        val responses = ArrayDeque<ByteArray>().apply {
            addAll(variableRead(entry, populated))
            addAll(directoryRead())
            addAll(sendAcks(EvoVariablePayload.transferUrl(archived = false)))
            addAll(directoryRead(directoryEntry(entry.tokenName, archived = false, type = 1, size = 4)))
            addAll(variableRead(entry, empty))
            addAll(scancodeAcks())
        }
        val writes = mutableListOf<KermitPacketCodec.Packet>()
        val session = KermitPacketCodec.Session()
        val transport = object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) {
                val packet = KermitPacketCodec.parsePacket(data, session)
                writes += packet
                if (packet.type == 'S') session.updateFromSendInit(sendInitAck)
            }
            override fun readPacketBytes(): ByteArray = responses.removeFirst()
        }

        val completed = EvoLink(transport).deleteVariables(listOf(entry))

        assertEquals(listOf("L1"), completed.map { it.name })
        assertEquals(4L, completed.single().size)
        assertTrue(isPersistentBuiltInList(completed.single()))
        assertEquals(0, writes.count { it.type == 'F' && it.data.decodeToString().startsWith("hh01/del/") })
        val uploadStart = writes.indexOfFirst {
            it.type == 'F' && it.data.decodeToString() == EvoVariablePayload.transferUrl(false)
        }
        assertTrue(uploadStart >= 0)
        val uploaded = writes.drop(uploadStart + 1)
            .takeWhile { it.type != 'Z' }
            .filter { it.type == 'D' }
            .flatMap { KermitPacketCodec.decodeData(it.data).asIterable() }
            .toByteArray()
        val uploadedInspection = EvoVariableFile.inspect(uploaded.dropLast(2).toByteArray())
        assertContentEquals(nativeTokenName, uploadedInspection.metadata["name"] as ByteArray)
        assertEquals(0L, uploadedInspection.fields["len"])
        assertFalse("data" in uploadedInspection.fields)
        assertEquals(
            EvoListEditor.RESET_TO_DEFAULT_COLUMNS.size,
            writes.count { it.type == 'F' && it.data.decodeToString() == EvoListEditor.SCANCODE_ENDPOINT },
        )
        assertTrue(responses.isEmpty())
    }

    private fun tokenWords(vararg words: Int): ByteArray = ByteArray(words.size * 2).also { bytes ->
        words.forEachIndexed { index, word ->
            bytes[index * 2] = word.toByte()
            bytes[index * 2 + 1] = (word ushr 8).toByte()
        }
    }

    private val sendInit = byteArrayOf(
        0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
        0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )

    private val sendInitAck = byteArrayOf(
        0x7E, 0x25, 0x20, 0x40, 0x2D, 0x23, 0x59,
        0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )

    private fun sendAcks(request: String): List<ByteArray> = listOf(
        ack(0, sendInitAck),
        ack(1, request.encodeToByteArray()),
        ack(2, byteArrayOf('Y'.code.toByte())),
        ack(3),
        ack(4),
        ack(5),
    )

    private fun deleteFailure(message: String): List<ByteArray> = listOf(
        ack(0, sendInitAck),
        KermitPacketCodec.makePacket(1, 'E', message.encodeToByteArray()),
    )

    private fun directoryRead(vararg entries: ByteArray): List<ByteArray> {
        val request = buildGetRequest("hh01/inf/res?name=directory&gotohome=1").decodeToString()
        val payload = cborMap("data" to cborArray(*entries))
        return resourceRead(request, payload)
    }

    private fun variableRead(entry: EvoDirectoryEntry, payload: ByteArray): List<ByteArray> {
        val request = buildGetRequest("hh01/xfr/${buildVariableResourceName(entry)}").decodeToString()
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

    private fun nativeList(tokenName: ByteArray, length: Int, data: ByteArray): ByteArray {
        val fields = mutableListOf(
            "metaData" to cborMap(
                "type" to cborUnsigned(1),
                "version" to cborUnsigned(1),
                "name" to cborBytes(tokenName),
            ),
            "version" to cborUnsigned(1),
            "type" to cborUnsigned(0),
            "len" to cborUnsigned(length),
        )
        if (length > 0) {
            fields += "arraylen" to cborUnsigned((data.size - 3) / 2)
            fields += "size" to cborUnsigned(data.size)
        }
        fields += "data" to cborBytes(data)
        return cborMap(*fields.toTypedArray())
    }

    private fun ack(sequence: Int, payload: ByteArray = byteArrayOf()): ByteArray =
        KermitPacketCodec.makePacket(sequence, 'Y', payload)

    private fun scancodeAcks(): List<ByteArray> = buildList {
        add(ack(0, sendInitAck))
        repeat(EvoListEditor.RESET_TO_DEFAULT_COLUMNS.size * 4 + 1) { offset ->
            add(ack(offset + 1))
        }
    }

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

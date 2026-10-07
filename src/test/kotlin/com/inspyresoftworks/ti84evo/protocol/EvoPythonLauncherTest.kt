package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EvoPythonLauncherTest {
    @Test
    fun `launchable programs are RAM Python entries in File Manager order`() {
        val directory = listOf(
            entry("ZZPROBE", type = 15),
            entry("TI_DRAW", type = 18, archived = true),
            entry("ARCHIVED", type = 15, archived = true),
            entry("LAUNCHOK", type = 15),
            entry("AAPROBE", type = 15),
            entry("L1", type = 1),
        )

        assertEquals(
            listOf("AAPROBE", "LAUNCHOK", "ZZPROBE"),
            EvoPythonLauncher.launchablePrograms(directory),
        )
    }

    @Test
    fun `Python File Manager is opened from calculator home`() {
        assertEquals(
            listOf(
                EvoPythonLauncher.SCAN_PRGM,
                EvoPythonLauncher.SCAN_DOWN,
                EvoPythonLauncher.SCAN_ENTER,
            ),
            EvoPythonLauncher.OPEN_PYTHON_FILE_MANAGER,
        )
    }

    @Test
    fun `selection jumps by initial then moves within matching programs`() {
        val programs = listOf("AAPROBE", "LAUNCHOK", "LATER", "ZZPROBE")

        assertEquals(listOf(0x30), EvoPythonLauncher.selectionKeys(programs, "AAPROBE"))
        assertEquals(listOf(0x15), EvoPythonLauncher.selectionKeys(programs, "LAUNCHOK"))
        assertEquals(listOf(0x15, EvoPythonLauncher.SCAN_DOWN), EvoPythonLauncher.selectionKeys(programs, "LATER"))
        assertEquals(listOf(0x1A), EvoPythonLauncher.selectionKeys(programs, "ZZPROBE"))
    }

    @Test
    fun `launch reads home directory reconnects and sends selection keys in order`() {
        val transport = LauncherTransport(directoryRead())
        transport.open()

        val result = EvoPythonLauncher(transport, waitForPythonMillis = 0).launch("ABPROBE")

        assertEquals("ABPROBE", result.programName)
        assertEquals(1, result.selectionIndex)
        assertEquals(
            "hh01/get/hh01/inf/res?name=directory&gotohome=1",
            transport.packets.first { it.type == 'F' }.data.decodeToString(),
        )
        val keyboardRequestIndex = transport.packets.indexOfFirst {
            it.type == 'F' && it.data.decodeToString() == EvoListEditor.SCANCODE_ENDPOINT
        }
        val directoryEnd = transport.events.indexOfLast { it == "read:B" }
        val keyboardEvent = transport.events.indexOfFirst {
            it == "write:F:${EvoListEditor.SCANCODE_ENDPOINT}"
        }
        assertTrue(directoryEnd >= 0 && keyboardEvent > directoryEnd)
        assertEquals(
            listOf("close", "open"),
            transport.events.subList(directoryEnd + 1, keyboardEvent).filter { it == "close" || it == "open" },
        )
        assertEquals(
            listOf(0x1F, 0x01, 0x09, 0x30, 0x01, 0x35)
                .map(EvoListEditor::scancodePayload)
                .map { it.toList() },
            scancodes(transport.packets.drop(keyboardRequestIndex)).map { it.toList() },
        )
        assertTrue(transport.responses.isEmpty())
    }

    @Test
    fun `launch rejects digit-prefixed names before calculator io`() {
        val transport = LauncherTransport(ArrayDeque())

        assertFailsWith<EvoProtocolException> {
            EvoPythonLauncher(transport).launch("2MAIN")
        }

        assertTrue(transport.events.isEmpty())
        assertTrue(transport.packets.isEmpty())
    }

    private fun scancodes(packets: List<KermitPacketCodec.Packet>): List<ByteArray> =
        packets.withIndex()
            .filter { (_, packet) -> packet.type == 'F' && packet.data.decodeToString() == EvoListEditor.SCANCODE_ENDPOINT }
            .map { (index, _) ->
                val chunks = packets.drop(index + 1).takeWhile { it.type != 'Z' }
                    .filter { it.type == 'D' }.map { it.data }
                KermitPacketCodec.decodeData(chunks.fold(ByteArrayOutputStream()) { output, chunk ->
                    output.write(chunk)
                    output
                }.toByteArray())
            }

    private fun directoryRead(): ArrayDeque<ByteArray> {
        val payload = cborMap(
            "data" to cborArray(
                directoryEntry("AAPROBE"),
                directoryEntry("ABPROBE"),
            ),
        )
        return ArrayDeque(
            listOf(
                packet(0, 'S', sendInit),
                packet(1, 'F', "directory".encodeToByteArray()),
                packet(2, 'A', KermitPacketCodec.buildFileAttributes(payload.size)),
                packet(3, 'D', KermitPacketCodec.encodeData(payload)),
                packet(4, 'Z'),
                packet(5, 'B'),
            ),
        )
    }

    private fun directoryEntry(name: String): ByteArray = cborMap(
        "dispName" to cborText(name),
        "type" to cborUnsigned(15),
        "size" to cborUnsigned(10),
        "mem" to byteArrayOf(0xF4.toByte()),
    )

    private fun cborMap(vararg entries: Pair<String, ByteArray>): ByteArray = concat(
        cborLength(5, entries.size),
        *entries.flatMap { (key, value) -> listOf(cborText(key), value) }.toTypedArray(),
    )

    private fun cborArray(vararg values: ByteArray): ByteArray = concat(cborLength(4, values.size), *values)

    private fun cborText(value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        return concat(cborLength(3, bytes.size), bytes)
    }

    private fun cborUnsigned(value: Int): ByteArray = cborLength(0, value)

    private fun cborLength(major: Int, value: Int): ByteArray = when {
        value < 24 -> byteArrayOf(((major shl 5) or value).toByte())
        value < 256 -> byteArrayOf(((major shl 5) or 24).toByte(), value.toByte())
        else -> byteArrayOf(((major shl 5) or 25).toByte(), (value ushr 8).toByte(), value.toByte())
    }

    private fun concat(vararg values: ByteArray): ByteArray = ByteArray(values.sumOf { it.size }).also { result ->
        var offset = 0
        values.forEach { value ->
            value.copyInto(result, offset)
            offset += value.size
        }
    }

    private fun packet(sequence: Int, type: Char, data: ByteArray = byteArrayOf()): ByteArray =
        KermitPacketCodec.makePacket(sequence, type, data)

    private val sendInit = byteArrayOf(
        0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
        0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )
    private val sendInitAck = sendInit.copyOf().also { it[1] = 0x25 }

    private inner class LauncherTransport(val responses: ArrayDeque<ByteArray>) : EvoTransport {
        override val description = "test"
        val events = mutableListOf<String>()
        val packets = mutableListOf<KermitPacketCodec.Packet>()
        private val session = KermitPacketCodec.Session()
        private var pendingAck: ByteArray? = null

        override fun open() {
            events += "open"
        }

        override fun close() {
            events += "close"
        }

        override fun write(data: ByteArray) {
            val sent = KermitPacketCodec.parsePacket(data, session)
            packets += sent
            events += "write:${sent.type}" +
                if (sent.type == 'F') ":${sent.data.decodeToString()}" else ""
            if (sent.type == 'Y') return

            val ackData = when (sent.type) {
                'S' -> sendInitAck
                'F' -> sent.data
                'A' -> byteArrayOf('Y'.code.toByte())
                else -> byteArrayOf()
            }
            pendingAck = packet(sent.sequence, 'Y', ackData)
            if (sent.type == 'S') session.updateFromSendInit(sendInitAck)
        }

        override fun readPacketBytes(): ByteArray {
            pendingAck?.let {
                pendingAck = null
                return it
            }
            val response = responses.removeFirst()
            val incoming = KermitPacketCodec.parsePacket(
                response,
                session,
                validateExtendedHeaderCheck = false,
            )
            events += "read:${incoming.type}"
            return response
        }
    }

    private fun entry(name: String, type: Int, archived: Boolean = false) = EvoDirectoryEntry(
        name = name,
        type = type,
        size = 1,
        archived = archived,
        tokenName = byteArrayOf(),
    )
}

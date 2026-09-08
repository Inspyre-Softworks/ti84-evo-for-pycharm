package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Confirmed S/F/A/D/Z/B Evo transaction ladder using standard Kermit packets.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoTransactionEngine(private val transport: EvoTransport) {
    private val sessionPayload = byteArrayOf(
        0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59, 0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )

    private fun sessionAckPayload(payload: ByteArray): ByteArray {
        if (payload.size < 2) throw EvoProtocolException("S payload is too short to acknowledge")
        return payload.copyOf().also { it[1] = 0x25 }
    }

    private fun expectedAckPayload(type: Char, data: ByteArray): ByteArray = when (type) {
        'S' -> sessionAckPayload(data)
        'F' -> data
        'A' -> byteArrayOf('Y'.code.toByte())
        'D', 'Z', 'B' -> byteArrayOf()
        else -> throw EvoUnsupportedException("ACK behavior for $type is unresolved")
    }

    private fun sendAndAck(
        sequence: Int,
        type: Char,
        data: ByteArray,
        session: KermitPacketCodec.Session,
    ): KermitPacketCodec.Packet {
        transport.write(KermitPacketCodec.makePacket(sequence, type, data, session))
        val ack = KermitPacketCodec.parsePacket(transport.readPacketBytes(), session)
        if (ack.type != 'Y') {
            val message = if (ack.type == 'E') {
                "expected Y ack for $type, got E: ${formatErrorPayload(ack.data)}"
            } else {
                "expected Y ack for $type, got ${ack.type}"
            }
            throw EvoUnexpectedFrameException(message)
        }
        if (ack.sequence != sequence) {
            throw EvoUnexpectedFrameException(
                "ack sequence mismatch for $type: expected $sequence, got ${ack.sequence}",
            )
        }
        if (!ack.data.contentEquals(expectedAckPayload(type, data))) {
            throw EvoUnexpectedFrameException("unexpected Y payload for $type")
        }
        return ack
    }

    private fun formatErrorPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return "calculator returned an empty error payload"
        val text = payload.toString(StandardCharsets.UTF_8)
        return if (text.all { !it.isISOControl() }) {
            "calculator error ${text.trim()}"
        } else {
            "calculator error bytes ${payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}"
        }
    }

    fun sendSmallTransaction(request: ByteArray, data: ByteArray) {
        val session = KermitPacketCodec.Session()
        var sequence = 0

        fun send(type: Char, payload: ByteArray = byteArrayOf()): KermitPacketCodec.Packet {
            val ack = sendAndAck(sequence, type, payload, session)
            sequence = (sequence + 1) % 64
            return ack
        }

        val sendInitAck = send('S', sessionPayload)
        session.updateFromSendInit(sendInitAck.data)
        send('F', request)
        send('A', KermitPacketCodec.buildFileAttributes(data.size))
        send('D', KermitPacketCodec.encodeResourceData(data))
        send('Z')
        send('B')
    }

    private fun sendY(
        received: KermitPacketCodec.Packet,
        data: ByteArray,
        session: KermitPacketCodec.Session,
    ) {
        transport.write(KermitPacketCodec.makePacket(received.sequence, 'Y', data, session))
    }

    private fun validateSequence(packet: KermitPacketCodec.Packet, expected: Int): Int {
        if (packet.sequence != expected) {
            throw EvoUnexpectedFrameException(
                "reverse transaction sequence mismatch: expected $expected, got ${packet.sequence} for ${packet.type}",
            )
        }
        return (expected + 1) % 64
    }

    fun receiveTransaction(): Pair<ByteArray, ByteArray> {
        val session = KermitPacketCodec.Session()
        var expectedSequence = 0

        fun readPacket(): KermitPacketCodec.Packet =
            KermitPacketCodec.parsePacket(
                transport.readPacketBytes(),
                session,
                validateExtendedHeaderCheck = false,
            )

        var packet = readPacket()
        expectedSequence = validateSequence(packet, expectedSequence)
        if (packet.type != 'S') unexpected("S", packet)
        sendY(packet, sessionAckPayload(packet.data), session)
        session.updateFromSendInit(packet.data)

        packet = readPacket()
        expectedSequence = validateSequence(packet, expectedSequence)
        if (packet.type != 'F') unexpected("F", packet)
        val resourceDescriptor = packet.data.copyOf()
        sendY(packet, packet.data, session)

        packet = readPacket()
        expectedSequence = validateSequence(packet, expectedSequence)
        if (packet.type != 'A') unexpected("A", packet)
        val expectedLength = KermitPacketCodec.parseFileLengthAttributes(packet.data)
        sendY(packet, byteArrayOf('Y'.code.toByte()), session)

        val wireData = ByteArrayOutputStream()
        while (true) {
            packet = readPacket()
            expectedSequence = validateSequence(packet, expectedSequence)
            when (packet.type) {
                'D' -> {
                    wireData.write(packet.data)
                    sendY(packet, byteArrayOf(), session)
                }
                'Z' -> {
                    sendY(packet, byteArrayOf(), session)
                    break
                }
                else -> unexpected("D or Z", packet)
            }
        }

        packet = readPacket()
        validateSequence(packet, expectedSequence)
        if (packet.type != 'B') unexpected("B", packet)
        sendY(packet, byteArrayOf(), session)

        val encodedData = wireData.toByteArray()
        val decodedData = runCatching { KermitPacketCodec.decodeResourceData(encodedData) }
            .getOrDefault(encodedData)
        val resolved = EvoResourceCodec.resolve(expectedLength, decodedData, encodedData)
        return Pair(resourceDescriptor, resolved.bytes)
    }

    private fun unexpected(expected: String, packet: KermitPacketCodec.Packet): Nothing {
        throw EvoUnexpectedFrameException("expected $expected, got ${packet.type}")
    }
}

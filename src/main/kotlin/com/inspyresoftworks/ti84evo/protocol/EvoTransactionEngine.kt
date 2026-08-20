package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport

/**
 * Confirmed S/F/A/D/Z/B Evo transaction ladder.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoTransactionEngine(private val transport: EvoTransport) {
    private val sessionPayload = byteArrayOf(
        0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59, 0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )

    private fun writeFrame(frame: EvoFrame, forceExtended: Boolean = false) {
        transport.write(EvoFrameCodec.encode(frame, forceExtended))
    }

    private fun readFrame(): EvoFrame = EvoFrameCodec.decode(transport.readFrameBytes())

    private fun sessionAckPayload(payload: ByteArray): ByteArray {
        if (payload.size < 2) throw EvoProtocolException("S payload is too short to acknowledge")
        return payload.copyOf().also { it[1] = 0x25 }
    }

    private fun expectedAckPayload(frame: EvoFrame): ByteArray = when (frame.command) {
        EvoFrameCodec.CMD_S -> sessionAckPayload(frame.payload)
        EvoFrameCodec.CMD_F -> frame.payload
        EvoFrameCodec.CMD_A -> byteArrayOf('Y'.code.toByte())
        EvoFrameCodec.CMD_D, EvoFrameCodec.CMD_Z, EvoFrameCodec.CMD_B -> byteArrayOf()
        else -> throw EvoUnsupportedException("ACK behavior for ${frame.commandText} is unresolved")
    }

    private fun sendAndAck(frame: EvoFrame) {
        writeFrame(frame)
        val ack = readFrame()
        if (ack.command != EvoFrameCodec.CMD_Y) {
            throw EvoUnexpectedFrameException("expected Y ack for ${frame.commandText}, got ${ack.commandText}")
        }
        if (ack.sequence != frame.sequence) {
            throw EvoUnexpectedFrameException("ack sequence mismatch for ${frame.commandText}")
        }
        if (!ack.payload.contentEquals(expectedAckPayload(frame))) {
            throw EvoUnexpectedFrameException("unexpected Y payload for ${frame.commandText}")
        }
    }

    fun sendSmallTransaction(request: ByteArray, data: ByteArray) {
        if (6 + EvoFrameCodec.escapeDPayload(data).size > EvoFrameCodec.MAX_SHORT_TOTAL) {
            throw EvoUnsupportedException("large outgoing D transfer requires a confirmed initial extended-D AUX value")
        }

        val sequence = EvoSequence()
        sendAndAck(EvoFrame(sequence.take(), EvoFrameCodec.CMD_S, sessionPayload))
        sendAndAck(EvoFrame(sequence.take(), EvoFrameCodec.CMD_F, request))
        sendAndAck(EvoFrame(sequence.take(), EvoFrameCodec.CMD_A, EvoFrameCodec.buildLengthAnnouncement(data.size)))
        sendAndAck(EvoFrame(sequence.take(), EvoFrameCodec.CMD_D, data))
        sendAndAck(EvoFrame(sequence.take(), EvoFrameCodec.CMD_Z))
        sendAndAck(EvoFrame(sequence.take(), EvoFrameCodec.CMD_B))
    }

    private fun sendY(received: EvoFrame, payload: ByteArray) {
        writeFrame(EvoFrame(received.sequence, EvoFrameCodec.CMD_Y, payload))
    }

    private fun validateSequence(frame: EvoFrame, expected: Int): Int {
        if (frame.sequence != expected) {
            throw EvoUnexpectedFrameException(
                "reverse transaction sequence mismatch: expected 0x%02X, got 0x%02X for %s"
                    .format(expected, frame.sequence, frame.commandText),
            )
        }
        return if (expected == EvoFrameCodec.PRINTABLE_MAX) EvoFrameCodec.PRINTABLE_BASE else expected + 1
    }

    fun receiveTransaction(): Pair<ByteArray, ByteArray> {
        var expectedSequence = EvoFrameCodec.PRINTABLE_BASE

        var frame = readFrame()
        expectedSequence = validateSequence(frame, expectedSequence)
        if (frame.command != EvoFrameCodec.CMD_S) unexpected("S", frame)
        sendY(frame, sessionAckPayload(frame.payload))

        frame = readFrame()
        expectedSequence = validateSequence(frame, expectedSequence)
        if (frame.command != EvoFrameCodec.CMD_F) unexpected("F", frame)
        val resourceDescriptor = frame.payload.copyOf()
        sendY(frame, frame.payload)

        frame = readFrame()
        expectedSequence = validateSequence(frame, expectedSequence)
        if (frame.command != EvoFrameCodec.CMD_A) unexpected("A", frame)
        val expectedLength = EvoFrameCodec.parseLengthAnnouncement(frame.payload)
        sendY(frame, byteArrayOf('Y'.code.toByte()))

        val decodedData = mutableListOf<Byte>()
        val wireData = mutableListOf<Byte>()

        while (true) {
            frame = readFrame()
            expectedSequence = validateSequence(frame, expectedSequence)
            when (frame.command) {
                EvoFrameCodec.CMD_D -> {
                    frame.payload.forEach(decodedData::add)
                    (frame.wirePayload ?: frame.payload).forEach(wireData::add)
                    sendY(frame, byteArrayOf())
                }
                EvoFrameCodec.CMD_Z -> {
                    sendY(frame, byteArrayOf())
                    break
                }
                else -> unexpected("D or Z", frame)
            }
        }

        frame = readFrame()
        validateSequence(frame, expectedSequence)
        if (frame.command != EvoFrameCodec.CMD_B) unexpected("B", frame)
        sendY(frame, byteArrayOf())

        val resolved = EvoResourceCodec.resolve(
            expectedLength,
            decodedData.toByteArray(),
            wireData.toByteArray(),
        )
        return Pair(resourceDescriptor, resolved.bytes)
    }

    private fun unexpected(expected: String, frame: EvoFrame): Nothing {
        throw EvoUnexpectedFrameException("expected $expected, got ${frame.commandText}")
    }
}

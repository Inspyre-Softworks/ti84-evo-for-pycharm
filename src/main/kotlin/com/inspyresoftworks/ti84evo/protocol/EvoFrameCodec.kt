package com.inspyresoftworks.ti84evo.protocol

/**
 * TI-84 Evo frame codec.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
object EvoFrameCodec {
    const val SOH = 0x01
    const val CR = 0x0D
    const val ESC = 0x23

    const val PRINTABLE_BASE = 0x20
    const val PRINTABLE_MAX = 0x7E
    const val BASE95 = 95

    const val CMD_A = 'A'.code
    const val CMD_B = 'B'.code
    const val CMD_D = 'D'.code
    const val CMD_F = 'F'.code
    const val CMD_S = 'S'.code
    const val CMD_Y = 'Y'.code
    const val CMD_Z = 'Z'.code

    const val MAX_SHORT_TOTAL = 97
    const val OBSERVED_MAX_D_WIRE_PAYLOAD = 2036

    fun checksum(body: ByteArray): Int {
        val value = body.sumOf { it.toInt() and 0xFF } and 0xFF
        return PRINTABLE_BASE + (((value and 0x3F) + (value ushr 6)) and 0x3F)
    }

    fun escapeDPayload(payload: ByteArray): ByteArray {
        val output = ArrayList<Byte>(payload.size)
        payload.forEach { raw ->
            val value = raw.toInt() and 0xFF
            if (value == SOH || value == CR || value == ESC) {
                output += ESC.toByte()
                output += (value xor 0x40).toByte()
            } else {
                output += raw
            }
        }
        return output.toByteArray()
    }

    fun unescapeDPayload(payload: ByteArray): ByteArray {
        val output = ArrayList<Byte>(payload.size)
        var index = 0
        while (index < payload.size) {
            val value = payload[index].toInt() and 0xFF
            if (value != ESC) {
                output += payload[index]
                index += 1
                continue
            }

            if (index + 1 >= payload.size) {
                throw EvoFrameException("truncated D-frame escape sequence")
            }

            output += ((payload[index + 1].toInt() and 0xFF) xor 0x40).toByte()
            index += 2
        }
        return output.toByteArray()
    }

    fun encodeBase95Pair(value: Int): Pair<Int, Int> {
        require(value in 0 until (BASE95 * BASE95)) {
            "value out of two-digit base-95 range: $value"
        }
        val high = value / BASE95
        val low = value % BASE95
        return Pair(PRINTABLE_BASE + high, PRINTABLE_BASE + low)
    }

    fun decodeBase95Pair(high: Int, low: Int): Int {
        if (high !in PRINTABLE_BASE..PRINTABLE_MAX || low !in PRINTABLE_BASE..PRINTABLE_MAX) {
            throw EvoFrameException("extended length contains a non-printable base-95 digit")
        }
        return (high - PRINTABLE_BASE) * BASE95 + (low - PRINTABLE_BASE)
    }

    fun buildLengthAnnouncement(length: Int): ByteArray {
        require(length >= 0) { "length cannot be negative" }
        val digits = length.toString().encodeToByteArray()
        require(digits.size <= PRINTABLE_MAX - PRINTABLE_BASE) {
            "decimal length has too many digits"
        }

        return concat(
            "\"\"B81".encodeToByteArray(),
            byteArrayOf((PRINTABLE_BASE + digits.size).toByte()),
            digits,
            "@ ".encodeToByteArray(),
        )
    }

    fun parseLengthAnnouncement(payload: ByteArray): Int {
        val prefix = "\"\"B81".encodeToByteArray()
        val suffix = "@ ".encodeToByteArray()

        if (payload.size < prefix.size + 1 + suffix.size ||
            !payload.copyOfRange(0, prefix.size).contentEquals(prefix) ||
            !payload.copyOfRange(payload.size - suffix.size, payload.size).contentEquals(suffix)
        ) {
            throw EvoProtocolException("unrecognized A payload")
        }

        val count = (payload[prefix.size].toInt() and 0xFF) - PRINTABLE_BASE
        val digitStart = prefix.size + 1
        val digitEnd = payload.size - suffix.size
        val digits = payload.copyOfRange(digitStart, digitEnd).decodeToString()

        if (count != digits.length || digits.any { !it.isDigit() }) {
            throw EvoProtocolException("A payload digit count does not match decimal length")
        }

        return digits.toInt()
    }

    fun encode(frame: EvoFrame, forceExtended: Boolean = false): ByteArray {
        require(frame.sequence in PRINTABLE_BASE..PRINTABLE_MAX) {
            "sequence must be printable 0x20..0x7E"
        }

        val wirePayload = if (frame.command == CMD_D) escapeDPayload(frame.payload) else frame.payload
        val shortTotal = 6 + wirePayload.size
        val useExtended = forceExtended || shortTotal > MAX_SHORT_TOTAL

        if (!useExtended) {
            val bodyLength = 3 + wirePayload.size
            val encodedLength = PRINTABLE_BASE + bodyLength
            if (encodedLength > PRINTABLE_MAX) {
                throw IllegalStateException("short-frame length overflow")
            }

            val body = concat(
                byteArrayOf(encodedLength.toByte(), frame.sequence.toByte(), frame.command.toByte()),
                wirePayload,
            )
            return concat(byteArrayOf(SOH.toByte()), body, byteArrayOf(checksum(body).toByte(), CR.toByte()))
        }

        val auxiliary = frame.auxiliary
            ?: throw EvoUnsupportedException("extended frame requires a confirmed AUX value")
        val extendedSpan = 1 + wirePayload.size
        val (lengthHigh, lengthLow) = encodeBase95Pair(extendedSpan)
        val body = concat(
            byteArrayOf(
                PRINTABLE_BASE.toByte(),
                frame.sequence.toByte(),
                frame.command.toByte(),
                lengthHigh.toByte(),
                lengthLow.toByte(),
                auxiliary.toByte(),
            ),
            wirePayload,
        )
        return concat(byteArrayOf(SOH.toByte()), body, byteArrayOf(checksum(body).toByte(), CR.toByte()))
    }

    fun decode(raw: ByteArray): EvoFrame {
        if (raw.size < 6) throw EvoFrameException("frame too short: ${raw.size} bytes")
        if ((raw.first().toInt() and 0xFF) != SOH) throw EvoFrameException("missing SOH")
        if ((raw.last().toInt() and 0xFF) != CR) throw EvoFrameException("missing CR terminator")

        val lengthMarker = raw[1].toInt() and 0xFF
        val sequence: Int
        val command: Int
        val auxiliary: Int?
        val extended: Boolean
        val wirePayload: ByteArray

        if (lengthMarker != PRINTABLE_BASE) {
            if (lengthMarker !in PRINTABLE_BASE..PRINTABLE_MAX) {
                throw EvoFrameException("invalid short length byte: 0x%02X".format(lengthMarker))
            }
            val expectedTotal = lengthMarker - PRINTABLE_BASE + 3
            if (raw.size != expectedTotal) {
                throw EvoFrameException("short length mismatch: expected $expectedTotal, got ${raw.size}")
            }
            sequence = raw[2].toInt() and 0xFF
            command = raw[3].toInt() and 0xFF
            wirePayload = raw.copyOfRange(4, raw.size - 2)
            auxiliary = null
            extended = false
        } else {
            if (raw.size < 9) throw EvoFrameException("extended frame too short: ${raw.size} bytes")
            sequence = raw[2].toInt() and 0xFF
            command = raw[3].toInt() and 0xFF
            val extendedSpan = decodeBase95Pair(raw[4].toInt() and 0xFF, raw[5].toInt() and 0xFF)
            val expectedTotal = extendedSpan + 8
            if (raw.size != expectedTotal) {
                throw EvoFrameException("extended length mismatch: expected $expectedTotal, got ${raw.size}")
            }
            auxiliary = raw[6].toInt() and 0xFF
            wirePayload = raw.copyOfRange(7, raw.size - 2)
            if (extendedSpan != 1 + wirePayload.size) {
                throw EvoFrameException("extended span is not AUX + wire payload")
            }
            extended = true
        }

        val body = raw.copyOfRange(1, raw.size - 2)
        val actualChecksum = raw[raw.size - 2].toInt() and 0xFF
        val expectedChecksum = checksum(body)
        if (actualChecksum != expectedChecksum) {
            throw EvoFrameException(
                "checksum mismatch: got 0x%02X, expected 0x%02X".format(actualChecksum, expectedChecksum),
            )
        }

        val payload = if (command == CMD_D) unescapeDPayload(wirePayload) else wirePayload.copyOf()
        return EvoFrame(
            sequence = sequence,
            command = command,
            payload = payload,
            auxiliary = auxiliary,
            extended = extended,
            wirePayload = if (command == CMD_D) wirePayload.copyOf() else null,
        )
    }

    fun concat(vararg arrays: ByteArray): ByteArray {
        val output = ByteArray(arrays.sumOf { it.size })
        var offset = 0
        arrays.forEach { bytes ->
            bytes.copyInto(output, offset)
            offset += bytes.size
        }
        return output
    }
}

package com.inspyresoftworks.ti84evo.protocol

import java.io.ByteArrayOutputStream

/**
 * Kermit packet support used by the Evo variable-transfer endpoint.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
object KermitPacketCodec {
    const val SOH = 0x01
    const val CR = 0x0D
    const val CONTROL_QUOTE = 0x23
    const val REPEAT_QUOTE = 0x7E

    data class Packet(
        val sequence: Int,
        val type: Char,
        val data: ByteArray,
    )

    class Session {
        var checkType: Int = 1
            private set
        var controlQuote: Int = CONTROL_QUOTE
            private set
        var binaryQuote: Int? = null
            private set
        var repeatQuote: Int = REPEAT_QUOTE
            private set
        var maxPacketLength: Int = 2_040
            private set

        val dataChunkSize: Int
            get() = maxOf(1, minOf(2_000, maxPacketLength - checkType - 8))

        fun updateFromSendInit(data: ByteArray) {
            if (data.size > 5 && u(data[5]) != 0x20) {
                controlQuote = u(data[5])
            }
            if (data.size > 6) {
                val value = u(data[6])
                if (value != 'Y'.code && value != 'N'.code && value != 0x20) {
                    binaryQuote = value
                }
            }
            if (data.size > 7) {
                when (u(data[7]).toChar()) {
                    '1' -> checkType = 1
                    '2' -> checkType = 2
                    '3' -> checkType = 3
                }
            }
            if (data.size > 8 && u(data[8]) != 0x20) {
                repeatQuote = u(data[8])
            }
            if (data.size > 12) {
                val length = unchar(u(data[11])) * 95 + unchar(u(data[12]))
                if (length >= 60) {
                    maxPacketLength = minOf(length, 2_040)
                }
            }
        }
    }

    fun makePacket(
        sequence: Int,
        type: Char,
        data: ByteArray = byteArrayOf(),
        session: Session? = null,
        checkType: Int = session?.checkType ?: 1,
    ): ByteArray {
        require(checkType in 1..3) { "Kermit check type must be 1, 2, or 3" }

        val sequenceByte = tochar(Math.floorMod(sequence, 64))
        val normalLength = data.size + 2 + checkType

        val body = if (normalLength <= 94) {
            concat(
                byteArrayOf(
                    tochar(normalLength).toByte(),
                    sequenceByte.toByte(),
                    type.code.toByte(),
                ),
                data,
            )
        } else {
            val dataAndCheckLength = data.size + checkType
            val header = byteArrayOf(
                tochar(0).toByte(),
                sequenceByte.toByte(),
                type.code.toByte(),
                tochar(dataAndCheckLength / 95).toByte(),
                tochar(dataAndCheckLength % 95).toByte(),
            )
            concat(header, blockCheck(header, 1), data)
        }

        return concat(
            byteArrayOf(SOH.toByte()),
            body,
            blockCheck(body, checkType),
            byteArrayOf(CR.toByte()),
        )
    }

    fun parsePacket(
        raw: ByteArray,
        session: Session? = null,
        checkType: Int = session?.checkType ?: 1,
        validateExtendedHeaderCheck: Boolean = true,
    ): Packet {
        if (raw.size < 6 || u(raw.first()) != SOH || u(raw.last()) != CR) {
            throw EvoFrameException("invalid Kermit packet envelope")
        }
        require(checkType in 1..3) { "Kermit check type must be 1, 2, or 3" }

        val body = raw.copyOfRange(1, raw.size - 1)
        val extended = u(body[0]) == tochar(0)
        if (body.size < 3 + checkType) {
            throw EvoFrameException("Kermit packet is too short")
        }

        val data: ByteArray
        val checked: ByteArray
        val check: ByteArray

        if (extended) {
            if (body.size < 6 + checkType) {
                throw EvoFrameException("extended Kermit packet is too short")
            }
            val header = body.copyOfRange(0, 5)
            if (validateExtendedHeaderCheck && u(body[5]) != u(blockCheck(header, 1).single())) {
                throw EvoFrameException("extended Kermit header checksum mismatch")
            }
            checked = body.copyOfRange(0, body.size - checkType)
            check = body.copyOfRange(body.size - checkType, body.size)
            data = body.copyOfRange(6, body.size - checkType)
        } else {
            val declared = unchar(u(body[0]))
            if (declared != body.size - 1) {
                throw EvoFrameException(
                    "Kermit packet length mismatch: declared=$declared actual=${body.size - 1}",
                )
            }
            checked = body.copyOfRange(0, body.size - checkType)
            check = body.copyOfRange(body.size - checkType, body.size)
            data = body.copyOfRange(3, body.size - checkType)
        }

        val expected = blockCheck(checked, checkType)
        if (!check.contentEquals(expected)) {
            throw EvoFrameException("Kermit packet block check mismatch")
        }

        return Packet(
            sequence = unchar(u(body[1])),
            type = u(body[2]).toChar(),
            data = data,
        )
    }

    /** Build the Kermit A-packet attributes used by Evo resource and variable transfers. */
    fun buildFileAttributes(payloadLength: Int): ByteArray {
        require(payloadLength >= 0) { "payload length cannot be negative" }
        return concat(
            fileAttribute('"', "B8"),
            fileAttribute('1', payloadLength.toString()),
            fileAttribute('@', ""),
        )
    }

    /** Read the decimal payload length from the Evo's Kermit A-packet attributes. */
    fun parseFileLengthAttributes(data: ByteArray): Int {
        val prefix = "\"\"B81".encodeToByteArray()
        val suffix = "@ ".encodeToByteArray()

        if (
            data.size < prefix.size + 1 + suffix.size ||
            !data.copyOfRange(0, prefix.size).contentEquals(prefix) ||
            !data.copyOfRange(data.size - suffix.size, data.size).contentEquals(suffix)
        ) {
            throw EvoProtocolException("unrecognized Kermit file attributes")
        }

        val count = unchar(u(data[prefix.size]))
        val digitStart = prefix.size + 1
        val digitEnd = data.size - suffix.size
        val digits = data.copyOfRange(digitStart, digitEnd).decodeToString()
        if (count != digits.length || digits.isEmpty() || digits.any { !it.isDigit() }) {
            throw EvoProtocolException("Kermit file length digit count does not match decimal length")
        }
        return digits.toIntOrNull()
            ?: throw EvoProtocolException("Kermit file length is too large")
    }

    /** Encode a binary payload into Kermit's quoted/repeated data stream. */
    fun encodeData(payload: ByteArray): ByteArray = concat(*encodeElements(payload).toTypedArray())

    /**
     * Encode the calculator's observed resource D-packet form. Unlike negotiated
     * uploads, this legacy-compatible form quotes only packet framing bytes.
     */
    fun encodeResourceData(payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(payload.size)
        payload.forEach { byte ->
            val value = u(byte)
            if (value == SOH || value == CR || value == CONTROL_QUOTE) {
                output.write(CONTROL_QUOTE)
                output.write(value xor 0x40)
            } else {
                output.write(value)
            }
        }
        return output.toByteArray()
    }

    /** Decode the limited quoting used by calculator resource responses. */
    fun decodeResourceData(encoded: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(encoded.size)
        var index = 0
        while (index < encoded.size) {
            val value = u(encoded[index])
            if (value == CONTROL_QUOTE) {
                if (index + 1 >= encoded.size) {
                    throw EvoProtocolException("truncated resource D-packet escape sequence")
                }
                output.write(u(encoded[index + 1]) xor 0x40)
                index += 2
            } else {
                output.write(value)
                index += 1
            }
        }
        return output.toByteArray()
    }

    /** Decode the two printable base-95 length characters in a long packet. */
    fun decodeLongPacketLength(high: Int, low: Int): Int {
        if (high !in 0x20..0x7E || low !in 0x20..0x7E) {
            throw EvoFrameException("extended length contains a non-printable base-95 digit")
        }
        return unchar(high) * 95 + unchar(low)
    }

    /** Decode Kermit's default control quoting and repeat encoding. */
    fun decodeData(encoded: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(encoded.size)
        var index = 0

        while (index < encoded.size) {
            var count = 1
            if (u(encoded[index]) == REPEAT_QUOTE) {
                if (index + 2 >= encoded.size) {
                    throw EvoProtocolException("truncated Kermit repeat sequence")
                }
                count = unchar(u(encoded[index + 1]))
                if (count !in 1..94) {
                    throw EvoProtocolException("invalid Kermit repeat count $count")
                }
                index += 2
            }

            var value = u(encoded[index])
            if (value == CONTROL_QUOTE) {
                if (index + 1 >= encoded.size) {
                    throw EvoProtocolException("truncated Kermit control quote")
                }
                value = uncontrol(u(encoded[index + 1]))
                index += 2
            } else {
                index += 1
            }
            repeat(count) { output.write(value) }
        }

        return output.toByteArray()
    }

    /**
     * Split encoded payload without cutting a quote or repeat element between D packets.
     */
    fun encodeDataChunks(payload: ByteArray, maximumChunkSize: Int): List<ByteArray> {
        require(maximumChunkSize >= 4) { "Kermit chunk size is too small" }
        val chunks = mutableListOf<ByteArray>()
        var current = ByteArrayOutputStream()

        for (element in encodeElements(payload)) {
            if (element.size > maximumChunkSize) {
                throw EvoProtocolException("encoded Kermit element exceeds negotiated packet size")
            }
            if (current.size() > 0 && current.size() + element.size > maximumChunkSize) {
                chunks += current.toByteArray()
                current = ByteArrayOutputStream()
            }
            current.write(element)
        }

        if (current.size() > 0) {
            chunks += current.toByteArray()
        }
        if (chunks.isEmpty()) {
            chunks += byteArrayOf()
        }
        return chunks
    }

    private fun encodeElements(payload: ByteArray): List<ByteArray> {
        val elements = mutableListOf<ByteArray>()
        var index = 0

        while (index < payload.size) {
            val value = u(payload[index])
            var run = 1
            while (
                index + run < payload.size &&
                u(payload[index + run]) == value &&
                run < 94
            ) {
                run++
            }

            val encodedValue = encodeByte(value)
            if (run >= 3) {
                elements += concat(
                    byteArrayOf(REPEAT_QUOTE.toByte(), tochar(run).toByte()),
                    encodedValue,
                )
            } else {
                repeat(run) { elements += encodedValue }
            }
            index += run
        }

        return elements
    }

    private fun encodeByte(value: Int): ByteArray {
        val sevenBit = value and 0x7F
        return when {
            sevenBit < 0x20 || sevenBit == 0x7F -> byteArrayOf(
                CONTROL_QUOTE.toByte(),
                (value xor 0x40).toByte(),
            )
            value == CONTROL_QUOTE -> byteArrayOf(CONTROL_QUOTE.toByte(), CONTROL_QUOTE.toByte())
            value == REPEAT_QUOTE -> byteArrayOf(CONTROL_QUOTE.toByte(), REPEAT_QUOTE.toByte())
            else -> byteArrayOf(value.toByte())
        }
    }

    private fun fileAttribute(tag: Char, value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        require(bytes.size <= 94) { "Kermit file attribute is too long" }
        return concat(
            byteArrayOf(tag.code.toByte(), tochar(bytes.size).toByte()),
            bytes,
        )
    }

    private fun blockCheck(data: ByteArray, checkType: Int): ByteArray = when (checkType) {
        1 -> byteArrayOf(checksum1(data).toByte())
        2 -> checksum2(data)
        3 -> checksum3(data)
        else -> throw IllegalArgumentException("unsupported Kermit check type $checkType")
    }

    private fun checksum1(data: ByteArray): Int {
        val sum = data.sumOf { u(it) } and 0xFFFF
        return tochar((sum + ((sum shr 6) and 3)) and 0x3F)
    }

    private fun checksum2(data: ByteArray): ByteArray {
        val sum = data.sumOf { u(it) } and 0xFFFF
        return byteArrayOf(
            tochar((sum shr 6) and 0x3F).toByte(),
            tochar(sum and 0x3F).toByte(),
        )
    }

    private fun checksum3(data: ByteArray): ByteArray {
        val crc = crc16Kermit(data)
        return byteArrayOf(
            tochar((crc shr 12) and 0x0F).toByte(),
            tochar((crc shr 6) and 0x3F).toByte(),
            tochar(crc and 0x3F).toByte(),
        )
    }

    private fun crc16Kermit(data: ByteArray): Int {
        var crc = 0
        for (byte in data) {
            val value = u(byte)
            var q = (crc xor value) and 0x0F
            crc = (crc ushr 4) xor (q * 0x1081)
            q = (crc xor (value shr 4)) and 0x0F
            crc = (crc ushr 4) xor (q * 0x1081)
        }
        return crc and 0xFFFF
    }

    private fun tochar(value: Int): Int = value + 0x20
    private fun unchar(value: Int): Int = value - 0x20
    private fun uncontrol(value: Int): Int =
        if ((value and 0x7F) == 0x3F || (value and 0x60) == 0x40) value xor 0x40 else value
    private fun u(value: Byte): Int = value.toInt() and 0xFF

    internal fun concat(vararg arrays: ByteArray): ByteArray {
        val size = arrays.sumOf(ByteArray::size)
        val output = ByteArray(size)
        var offset = 0
        for (array in arrays) {
            array.copyInto(output, offset)
            offset += array.size
        }
        return output
    }
}

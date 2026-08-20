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
            if (u(body[5]) != u(blockCheck(header, 1).single())) {
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

    /** Encode a binary payload into Kermit's quoted/repeated data stream. */
    fun encodeData(payload: ByteArray): ByteArray = concat(*encodeElements(payload).toTypedArray())

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

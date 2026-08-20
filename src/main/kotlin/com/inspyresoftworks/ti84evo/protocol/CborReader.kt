package com.inspyresoftworks.ti84evo.protocol

/**
 * Small CBOR reader covering the value types observed in Evo resources.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class CborReader(private val data: ByteArray) {
    private var index = 0

    fun readComplete(): Any? {
        val value = readValue()
        if (index != data.size) {
            throw EvoProtocolException("CBOR contains ${data.size - index} trailing bytes")
        }
        return value
    }

    private fun readValue(): Any? {
        val initial = readByte()
        val major = initial ushr 5
        val additional = initial and 0x1F

        return when (major) {
            0 -> readUnsigned(additional)
            1 -> -1L - readUnsigned(additional)
            2 -> readByteString(additional)
            3 -> readTextString(additional)
            4 -> readArray(additional)
            5 -> readMap(additional)
            6 -> {
                readUnsigned(additional)
                readValue()
            }
            7 -> readSimple(additional)
            else -> throw EvoProtocolException("unsupported CBOR major type $major")
        }
    }

    private fun readUnsigned(additional: Int): Long = when {
        additional < 24 -> additional.toLong()
        additional == 24 -> readByte().toLong()
        additional == 25 -> readUInt(2)
        additional == 26 -> readUInt(4)
        additional == 27 -> readUInt(8)
        else -> throw EvoProtocolException("unsupported CBOR integer additional info $additional")
    }

    private fun readByteString(additional: Int): ByteArray {
        if (additional == 31) {
            val parts = mutableListOf<ByteArray>()
            while (!nextIsBreak()) {
                val initial = readByte()
                if ((initial ushr 5) != 2) throw EvoProtocolException("indefinite byte string contains non-bytes chunk")
                parts += readByteString(initial and 0x1F)
            }
            readByte()
            return EvoFrameCodec.concat(*parts.toTypedArray())
        }

        val length = readUnsigned(additional).toIntChecked("byte string")
        return readBytes(length)
    }

    private fun readTextString(additional: Int): String {
        if (additional == 31) {
            val builder = StringBuilder()
            while (!nextIsBreak()) {
                val initial = readByte()
                if ((initial ushr 5) != 3) throw EvoProtocolException("indefinite text string contains non-text chunk")
                builder.append(readTextString(initial and 0x1F))
            }
            readByte()
            return builder.toString()
        }

        val length = readUnsigned(additional).toIntChecked("text string")
        return readBytes(length).decodeToString()
    }

    private fun readArray(additional: Int): List<Any?> {
        if (additional == 31) {
            val values = mutableListOf<Any?>()
            while (!nextIsBreak()) values += readValue()
            readByte()
            return values
        }

        val length = readUnsigned(additional).toIntChecked("array")
        return List(length) { readValue() }
    }

    private fun readMap(additional: Int): Map<Any?, Any?> {
        val values = linkedMapOf<Any?, Any?>()
        if (additional == 31) {
            while (!nextIsBreak()) {
                val key = readValue()
                values[key] = readValue()
            }
            readByte()
            return values
        }

        val length = readUnsigned(additional).toIntChecked("map")
        repeat(length) {
            val key = readValue()
            values[key] = readValue()
        }
        return values
    }

    private fun readSimple(additional: Int): Any? = when (additional) {
        20 -> false
        21 -> true
        22, 23 -> null
        24 -> readByte()
        25 -> halfToDouble(readUInt(2).toInt())
        26 -> Float.fromBits(readUInt(4).toInt()).toDouble()
        27 -> Double.fromBits(readUInt(8))
        31 -> throw EvoProtocolException("unexpected CBOR break")
        else -> additional
    }

    private fun halfToDouble(bits: Int): Double {
        val sign = if ((bits and 0x8000) != 0) -1.0 else 1.0
        val exponent = (bits ushr 10) and 0x1F
        val fraction = bits and 0x03FF
        return when (exponent) {
            0 -> if (fraction == 0) sign * 0.0 else sign * Math.scalb(fraction.toDouble(), -24)
            31 -> if (fraction == 0) sign * Double.POSITIVE_INFINITY else Double.NaN
            else -> sign * Math.scalb((1024 + fraction).toDouble(), exponent - 25)
        }
    }

    private fun readUInt(size: Int): Long {
        var value = 0L
        repeat(size) {
            value = (value shl 8) or readByte().toLong()
        }
        return value
    }

    private fun readByte(): Int {
        if (index >= data.size) throw EvoProtocolException("unexpected end of CBOR")
        return data[index++].toInt() and 0xFF
    }

    private fun readBytes(length: Int): ByteArray {
        if (length < 0 || index + length > data.size) throw EvoProtocolException("truncated CBOR payload")
        val bytes = data.copyOfRange(index, index + length)
        index += length
        return bytes
    }

    private fun nextIsBreak(): Boolean = index < data.size && (data[index].toInt() and 0xFF) == 0xFF

    private fun Long.toIntChecked(label: String): Int {
        if (this !in 0..Int.MAX_VALUE.toLong()) throw EvoProtocolException("CBOR $label is too large")
        return toInt()
    }
}

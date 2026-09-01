package com.inspyresoftworks.ti84evo.protocol

import java.math.BigDecimal
import java.math.BigInteger

/** Decodes native Evo number, list, and matrix payloads into ASCII-importer text. */
object EvoVariableDecoder {
    fun decode(
        raw: ByteArray,
        type: Int,
        name: String,
        archived: Boolean,
    ): EvoVariablePayload.EditableValue {
        val kind = when (type) {
            0 -> EvoVariablePayload.Kind.NUMBER
            1 -> EvoVariablePayload.Kind.LIST
            6 -> EvoVariablePayload.Kind.MATRIX
            else -> throw EvoProtocolException("${name.ifBlank { "variable" }} is not an editable data type")
        }
        val inspection = EvoVariableFile.inspect(raw)
        val payloadType = (inspection.metadata["type"] as? Number)?.toInt()
        if (payloadType != type) {
            throw EvoProtocolException("variable payload type $payloadType does not match directory type $type")
        }
        val value = when (kind) {
            EvoVariablePayload.Kind.NUMBER -> decodeNumber(inspection.data)
            EvoVariablePayload.Kind.LIST -> decodeList(inspection)
            EvoVariablePayload.Kind.MATRIX -> decodeMatrix(inspection)
        }
        return EvoVariablePayload.EditableValue(kind, name, value, archived)
    }

    private fun decodeNumber(data: ByteArray): String {
        val words = expressionWords(data, data.size / 2)
        val scalar = decodeScalar(words, 0)
        require(scalar.next == words.size) { "number payload contains unsupported expression data" }
        return scalar.text
    }

    private fun decodeList(inspection: EvoVariableFile.Inspection): String {
        val count = inspection.numberField("len").toInt()
        if (count == 0) return ""
        val words = expressionWords(inspection.data, inspection.numberField("arraylen").toInt())
        require(words.firstOrNull() == LIST_START) { "list payload is missing its start marker" }
        var offset = 1
        val payloadOrder = buildList {
            while (offset < words.size && words[offset] != LIST_END) {
                val scalar = decodeScalar(words, offset)
                add(scalar.text)
                offset = scalar.next
            }
        }
        require(offset == words.lastIndex && words[offset] == LIST_END) { "list payload is missing its end marker" }
        require(payloadOrder.size == count) { "list payload contains ${payloadOrder.size} values; expected $count" }
        return payloadOrder.asReversed().joinToString(", ")
    }

    private fun decodeMatrix(inspection: EvoVariableFile.Inspection): String {
        val rows = inspection.numberField("rows").toInt()
        val columns = inspection.numberField("cols").toInt()
        require(rows > 0 && columns > 0) { "matrix dimensions must be positive" }
        val words = expressionWords(inspection.data, inspection.numberField("arraylen").toInt())
        require(words.firstOrNull() == LIST_START) { "matrix payload is missing its start marker" }
        val display = Array(rows) { Array(columns) { "" } }
        var offset = 1
        repeat(rows) { payloadRow ->
            require(offset < words.size && words[offset] == LIST_START) { "matrix row is missing its start marker" }
            offset++
            repeat(columns) { payloadColumn ->
                val scalar = decodeScalar(words, offset)
                display[rows - payloadRow - 1][columns - payloadColumn - 1] = scalar.text
                offset = scalar.next
            }
            require(offset < words.size && words[offset] == LIST_END) { "matrix row is missing its end marker" }
            offset++
        }
        require(offset == words.lastIndex && words[offset] == LIST_END) { "matrix payload is missing its end marker" }
        return display.joinToString("\n") { row -> row.joinToString(", ") }
    }

    private fun decodeScalar(words: List<Int>, start: Int): Scalar {
        val real = decodeAtom(words, start)
        if (words.matches(real.next, IMAGINARY_MARKER, IMAGINARY_UNIT)) {
            val negative = words.getOrNull(real.next + 2) == NEGATE
            return Scalar((if (negative) "-" else "") + real.text.removePrefix("-") + "i", real.next + if (negative) 3 else 2)
        }

        val imaginary = runCatching { decodeAtom(words, real.next) }.getOrNull()
        if (imaginary != null &&
            words.matches(imaginary.next, IMAGINARY_MARKER, IMAGINARY_UNIT) &&
            words.getOrNull(imaginary.next + 2) in setOf(ADD, SUBTRACT)
        ) {
            val operator = if (words[imaginary.next + 2] == ADD) "+" else "-"
            return Scalar(
                real.text + operator + imaginary.text.removePrefix("-") + "i",
                imaginary.next + 3,
            )
        }
        return real
    }

    private fun decodeAtom(words: List<Int>, start: Int): Scalar {
        require(start < words.size) { "scalar payload ended unexpectedly" }
        val tagIndex = (start until words.size).firstOrNull { words[it] in ATOM_TAGS }
            ?: throw IllegalArgumentException("scalar payload has no supported value tag")
        val tag = words[tagIndex]
        val text = when (tag) {
            POSITIVE_INTEGER, NEGATIVE_INTEGER -> decodeInteger(words, start, tagIndex, tag == NEGATIVE_INTEGER)
            POSITIVE_FRACTION, NEGATIVE_FRACTION -> decodeFraction(words, start, tagIndex, tag == NEGATIVE_FRACTION)
            DECIMAL -> decodeDecimal(words, start, tagIndex)
            else -> error("unreachable")
        }
        return Scalar(text, tagIndex + 1)
    }

    private fun decodeInteger(words: List<Int>, start: Int, tagIndex: Int, negative: Boolean): String {
        if (tagIndex == start + 1 && words[start] == 0) return "0"
        require(tagIndex > start) { "integer payload is missing its limb count" }
        val limbCount = words[tagIndex - 1]
        require(limbCount > 0 && tagIndex - start == limbCount + 1) { "integer payload has an invalid limb count" }
        val magnitude = limbs(words.subList(start, tagIndex - 1))
        return (if (negative) "-" else "") + magnitude
    }

    private fun decodeFraction(words: List<Int>, start: Int, tagIndex: Int, negative: Boolean): String {
        require(tagIndex > start + 2) { "fraction payload is too short" }
        val numeratorCount = words[tagIndex - 1]
        val numeratorStart = tagIndex - 1 - numeratorCount
        require(numeratorCount > 0 && numeratorStart > start) { "fraction numerator has an invalid limb count" }
        val denominatorCountIndex = numeratorStart - 1
        val denominatorCount = words[denominatorCountIndex]
        require(denominatorCount > 0 && denominatorCountIndex - start == denominatorCount) {
            "fraction denominator has an invalid limb count"
        }
        val denominator = limbs(words.subList(start, denominatorCountIndex))
        require(denominator != BigInteger.ZERO) { "fraction denominator cannot be zero" }
        val numerator = limbs(words.subList(numeratorStart, tagIndex - 1))
        return (if (negative) "-" else "") + numerator + "/" + denominator
    }

    private fun decodeDecimal(words: List<Int>, start: Int, tagIndex: Int): String {
        require(tagIndex - start == 5) { "decimal payload must contain six words" }
        val mantissaBytes = buildList {
            repeat(4) { index ->
                val word = words[start + index]
                add(word and 0xFF)
                add((word ushr 8) and 0xFF)
            }
        }
        val digits = mantissaBytes.asReversed().joinToString("") { byte ->
            val high = byte ushr 4
            val low = byte and 0x0F
            require(high <= 9 && low <= 9) { "decimal payload contains invalid BCD digits" }
            "$high$low"
        }.trimEnd('0')
        if (digits.isEmpty()) return "0"
        val signAndExponent = words[start + 4]
        val negative = (signAndExponent and 0xFF) == 0xFF
        val exponent = ((signAndExponent ushr 8) and 0xFF).toByte().toInt()
        val signedDigits = BigInteger(digits).let { if (negative) it.negate() else it }
        val decimal = BigDecimal(signedDigits, digits.length - 1 - exponent).stripTrailingZeros()
        return if (exponent in -6..15) decimal.toPlainString() else {
            val significand = buildString {
                if (negative) append('-')
                append(digits.first())
                if (digits.length > 1) append('.').append(digits.drop(1))
            }
            "${significand}E$exponent"
        }
    }

    private fun expressionWords(data: ByteArray, wordCount: Int): List<Int> {
        require(wordCount >= 0 && wordCount * 2 <= data.size) { "expression length exceeds variable data" }
        return List(wordCount) { index ->
            (data[index * 2].toInt() and 0xFF) or ((data[index * 2 + 1].toInt() and 0xFF) shl 8)
        }
    }

    private fun limbs(words: List<Int>): BigInteger = words.asReversed().fold(BigInteger.ZERO) { value, limb ->
        value.shiftLeft(16).add(BigInteger.valueOf(limb.toLong()))
    }

    private fun EvoVariableFile.Inspection.numberField(name: String): Long =
        (fields[name] as? Number)?.toLong() ?: throw EvoProtocolException("variable payload is missing numeric $name")

    private fun List<Int>.matches(offset: Int, vararg expected: Int): Boolean =
        expected.indices.all { index -> getOrNull(offset + index) == expected[index] }

    private data class Scalar(val text: String, val next: Int)

    private const val LIST_START = 0x00E5
    private const val LIST_END = 0x00D9
    private const val POSITIVE_INTEGER = 0x001F
    private const val NEGATIVE_INTEGER = 0x0020
    private const val POSITIVE_FRACTION = 0x0021
    private const val NEGATIVE_FRACTION = 0x0022
    private const val DECIMAL = 0x0023
    private const val IMAGINARY_MARKER = 0x0026
    private const val IMAGINARY_UNIT = 0x008F
    private const val NEGATE = 0x007A
    private const val ADD = 0x008B
    private const val SUBTRACT = 0x008D
    private val ATOM_TAGS = POSITIVE_INTEGER..DECIMAL
}

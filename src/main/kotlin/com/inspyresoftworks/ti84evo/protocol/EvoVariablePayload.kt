package com.inspyresoftworks.ti84evo.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Builds the Evo type-60 ASCII import envelope used for editable data variables. */
object EvoVariablePayload {
    enum class Kind(val wireName: String, val typeId: Int) {
        NUMBER("Number", 0),
        LIST("List", 1),
        MATRIX("Matrix", 6),
    }

    data class EditableValue(
        val kind: Kind,
        val name: String,
        val value: String,
        val archived: Boolean = false,
    )

    fun build(value: EditableValue): ByteArray {
        val normalizedName = normalizeName(value.kind, value.name)
        val normalizedValue = normalizeValue(value.kind, value.value)
        return ByteArrayOutputStream().apply {
            write(0xBF)
            write(cborText("metaData"))
            write(0xBF)
            write(cborText("type"))
            write(cborUnsigned(60))
            write(cborText("version"))
            write(cborUnsigned(1))
            write(0xFF)
            write(cborText("type"))
            write(cborText(value.kind.wireName))
            write(cborText("version"))
            write(cborUnsigned(1))
            write(cborText("name"))
            write(cborText(wireName(value.kind, normalizedName)))
            write(cborText("data"))
            write(cborBytes(normalizedValue.toByteArray(StandardCharsets.US_ASCII)))
            write(0xFF)
        }.toByteArray()
    }

    fun transferUrl(archived: Boolean, overwrite: Boolean = true): String =
        "hh01/xfr/var?memtarget=${if (archived) 1 else 0}&policy=${if (overwrite) 1 else 0}"

    fun normalizeName(kind: Kind, input: String): String {
        val name = input.trim().uppercase()
        return when (kind) {
            Kind.NUMBER -> {
                require(name.length == 1 && name.single() in 'A'..'Z') { "Number name must be A–Z" }
                name
            }
            Kind.LIST -> {
                if (name in BUILT_IN_LIST_NAMES) return name
                require(name.length in 1..5 && name.first() in 'A'..'Z' && name.all { it.isLetterOrDigit() }) {
                    "List name must be L1–L6 or 1–5 letters/digits beginning with a letter"
                }
                name
            }
            Kind.MATRIX -> {
                val letter = name.removeSurrounding("[", "]")
                require(letter.length == 1 && letter.single() in 'A'..'J') { "Matrix name must be A–J" }
                letter
            }
        }
    }

    private fun wireName(kind: Kind, normalizedName: String): String =
        if (kind == Kind.LIST && normalizedName in BUILT_IN_LIST_NAMES) normalizedName.drop(1) else normalizedName

    fun normalizeValue(kind: Kind, input: String): String = when (kind) {
        Kind.NUMBER -> normalizeNumber(input)
        Kind.LIST -> {
            val values = splitValues(input.trim().removeSurrounding("{", "}"))
                .map(::normalizeNumber)
            require(values.isNotEmpty()) { "List must contain at least one number" }
            require(values.size <= 999) { "List cannot contain more than 999 values" }
            values.joinToString(",", "{", "}")
        }
        Kind.MATRIX -> {
            val rows = input.lineSequence()
                .map { it.trim().removePrefix("[").removeSuffix("]") }
                .filter(String::isNotBlank)
                .map { row -> splitValues(row).map(::normalizeNumber) }
                .toList()
            require(rows.isNotEmpty() && rows.first().isNotEmpty()) { "Matrix must contain at least one row" }
            val width = rows.first().size
            require(rows.all { it.size == width }) { "Every matrix row must contain the same number of values" }
            require(rows.size <= 99 && width <= 99) { "Matrix dimensions cannot exceed 99×99" }
            rows.joinToString(separator = "", prefix = "[", postfix = "]") { it.joinToString(",", "[", "]") }
        }
    }

    private fun normalizeNumber(input: String): String {
        val value = input.filterNot(Char::isWhitespace).replace('I', 'i')
        require(NUMERIC_EXPRESSION.matches(value)) { "Invalid real, fraction, or complex value: $input" }
        FRACTION.findAll(value).forEach { fraction ->
            require(fraction.groupValues[1].trimStart('0').isNotEmpty()) { "Fraction denominator cannot be zero" }
        }
        return value.replace('e', 'E').replace("E", "@E")
    }

    private fun splitValues(input: String): List<String> =
        (if (',' in input) input.split(',') else input.split(Regex("\\s+")))
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun cborText(value: String): ByteArray {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return KermitPacketCodec.concat(cborLength(3, bytes.size), bytes)
    }

    private fun cborBytes(value: ByteArray): ByteArray = KermitPacketCodec.concat(cborLength(2, value.size), value)

    private fun cborUnsigned(value: Int): ByteArray = when {
        value < 24 -> byteArrayOf(value.toByte())
        value <= 0xFF -> byteArrayOf(0x18, value.toByte())
        else -> byteArrayOf(0x19, (value shr 8).toByte(), value.toByte())
    }

    private fun cborLength(majorType: Int, size: Int): ByteArray = when {
        size < 24 -> byteArrayOf(((majorType shl 5) or size).toByte())
        size <= 0xFF -> byteArrayOf(((majorType shl 5) or 24).toByte(), size.toByte())
        size <= 0xFFFF -> byteArrayOf(((majorType shl 5) or 25).toByte(), (size shr 8).toByte(), size.toByte())
        else -> byteArrayOf(
            ((majorType shl 5) or 26).toByte(),
            (size shr 24).toByte(),
            (size shr 16).toByte(),
            (size shr 8).toByte(),
            size.toByte(),
        )
    }

    private const val UNSIGNED_DECIMAL = "(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"
    private const val UNSIGNED_ATOM = "(?:$UNSIGNED_DECIMAL|\\d+/\\d+)"
    private const val SIGNED_ATOM = "[+-]?$UNSIGNED_ATOM"
    private val NUMERIC_EXPRESSION = Regex("(?:$SIGNED_ATOM|[+-]?$UNSIGNED_ATOM\\s*i|$SIGNED_ATOM[+-]$UNSIGNED_ATOM\\s*i)")
    private val FRACTION = Regex("/([0-9]+)")
    private val BUILT_IN_LIST_NAMES = (1..6).mapTo(mutableSetOf()) { "L$it" }
}

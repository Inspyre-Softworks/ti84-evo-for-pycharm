package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class EvoVariableDecoderTest {
    @Test
    fun `decodes native exact decimal fraction and complex numbers`() {
        val values = listOf(
            words(0x002A, 0x0001, 0x0020) to "-42",
            words(0x0000, 0x0000, 0x9000, 0x6758, 0x0201, 0x0023) to "675.89",
            words(0x0003, 0x0001, 0x0002, 0x0001, 0x0021) to "2/3",
            words(0x0004, 0x0001, 0x0020, 0x0002, 0x0001, 0x001F, 0x0026, 0x008F, 0x008B) to "-4+2i",
        )

        values.forEach { (data, expected) ->
            val decoded = EvoVariableDecoder.decode(nativeVariable(0, data), 0, "A", archived = false)
            assertEquals(expected, decoded.value)
        }
    }

    @Test
    fun `decodes reversed native list payload into display order`() {
        val expression = words(
            0x00E5,
            0x0000, 0x0000, 0x0000, 0x4500, 0x0001, 0x0023,
            0x0003, 0x0001, 0x0002, 0x0001, 0x0021,
            0x0001, 0x0001, 0x001F,
            0x00D9,
        )
        val data = expression + byteArrayOf(0, 6, 0)
        val decoded = EvoVariableDecoder.decode(
            nativeVariable(1, data, "type" to 0, "len" to 3, "arraylen" to expression.size / 2, "size" to data.size),
            1,
            "L1",
            archived = true,
        )

        assertEquals("1, 2/3, 4.5", decoded.value)
        assertEquals(true, decoded.archived)
    }

    @Test
    fun `decodes native matrix rows and columns into display order`() {
        val expression = words(
            0x00E5,
            0x00E5, 0x0004, 0x0001, 0x001F, 0x0003, 0x0001, 0x001F, 0x00D9,
            0x00E5, 0x0002, 0x0001, 0x001F, 0x0001, 0x0001, 0x001F, 0x00D9,
            0x00D9,
        )
        val data = expression + byteArrayOf(0, 0, 0, 0)
        val decoded = EvoVariableDecoder.decode(
            nativeVariable(
                6,
                data,
                "rows" to 2,
                "cols" to 2,
                "arraylen" to expression.size / 2,
                "size" to data.size,
            ),
            6,
            "[A]",
            archived = false,
        )

        assertEquals("1, 2\n3, 4", decoded.value)
    }

    private fun nativeVariable(type: Int, data: ByteArray, vararg fields: Pair<String, Int>): ByteArray = cborMap(
        "metaData" to cborMap("type" to cborUnsigned(type), "version" to cborUnsigned(1)),
        "version" to cborUnsigned(1),
        *fields.map { (key, value) -> key to cborUnsigned(value) }.toTypedArray(),
        "data" to cborBytes(data),
    )

    private fun words(vararg values: Int): ByteArray = ByteArray(values.size * 2).also { bytes ->
        values.forEachIndexed { index, word ->
            bytes[index * 2] = word.toByte()
            bytes[index * 2 + 1] = (word ushr 8).toByte()
        }
    }

    private fun cborMap(vararg entries: Pair<String, ByteArray>): ByteArray = concat(
        cborLength(5, entries.size),
        *entries.flatMap { (key, value) -> listOf(cborText(key), value) }.toTypedArray(),
    )

    private fun cborText(value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        return concat(cborLength(3, bytes.size), bytes)
    }

    private fun cborBytes(value: ByteArray): ByteArray = concat(cborLength(2, value.size), value)

    private fun cborUnsigned(value: Int): ByteArray = cborLength(0, value)

    private fun cborLength(major: Int, value: Int): ByteArray = when {
        value < 24 -> byteArrayOf(((major shl 5) or value).toByte())
        value < 256 -> byteArrayOf(((major shl 5) or 24).toByte(), value.toByte())
        else -> byteArrayOf(((major shl 5) or 25).toByte(), (value ushr 8).toByte(), value.toByte())
    }

    private fun concat(vararg values: ByteArray): ByteArray {
        val result = ByteArray(values.sumOf(ByteArray::size))
        var offset = 0
        values.forEach { value ->
            value.copyInto(result, offset)
            offset += value.size
        }
        return result
    }
}

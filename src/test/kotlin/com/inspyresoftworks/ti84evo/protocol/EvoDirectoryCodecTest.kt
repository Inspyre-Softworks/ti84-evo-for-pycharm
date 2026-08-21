package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvoDirectoryCodecTest {
    @Test
    fun decodesNamesTypesSizesAndMemoryLocations() {
        val raw = cborMap(
            "data" to cborArray(
                cborMap(
                    "tokName" to cborBytes(tokenName("HELLO")),
                    "dispName" to cborText("ignored"),
                    "type" to cborUnsigned(15),
                    "size" to cborUnsigned(321),
                    "mem" to cborBoolean(true),
                ),
                cborMap(
                    "tokName" to cborBytes(tokenWords(0xE830)),
                    "type" to cborUnsigned(1),
                    "size" to cborUnsigned(24),
                    "mem" to cborBoolean(false),
                ),
                cborMap(
                    "tokName" to cborBytes(byteArrayOf(0x34, 0x12)),
                    "dispName" to cborText("fallback"),
                    "type" to cborUnsigned(8),
                ),
            ),
        )

        val entries = EvoDirectoryCodec.decode(raw)

        assertEquals(3, entries.size)
        assertEquals("HELLO", entries[0].name)
        assertEquals("Python Program", entries[0].typeName)
        assertEquals(321, entries[0].size)
        assertEquals("Archive", entries[0].location)
        assertContentEquals(tokenName("HELLO"), entries[0].tokenName)

        assertEquals("L1", entries[1].name)
        assertEquals("List", entries[1].typeName)
        assertEquals("RAM", entries[1].location)

        assertEquals("fallback", entries[2].name)
        assertEquals("AppVar", entries[2].typeName)
        assertEquals(0, entries[2].size)
    }

    @Test
    fun decodesCustomListAndFunctionNames() {
        val raw = cborMap(
            "data" to cborArray(
                cborMap(
                    "tokName" to cborBytes(tokenWords(0xE836, 0xE800, 0xE401)),
                    "type" to cborUnsigned(1),
                ),
                cborMap(
                    "tokName" to cborBytes(tokenWords(0xE850)),
                    "type" to cborUnsigned(7),
                ),
            ),
        )

        val entries = EvoDirectoryCodec.decode(raw)

        assertEquals("A0", entries[0].name)
        assertEquals("X1T", entries[1].name)
    }

    @Test
    fun rejectsMalformedDirectoryData() {
        val error = assertFailsWith<EvoProtocolException> {
            EvoDirectoryCodec.decode(cborMap("data" to cborText("not an array")))
        }

        assertEquals("directory resource is missing its data array", error.message)
    }

    private fun tokenName(name: String): ByteArray = tokenWords(
        *name.map { character -> 0xE800 + character.uppercaseChar().code - 'A'.code }.toIntArray(),
    )

    private fun tokenWords(vararg words: Int): ByteArray = ByteArray(words.size * 2).also { bytes ->
        words.forEachIndexed { index, word ->
            bytes[index * 2] = word.toByte()
            bytes[index * 2 + 1] = (word ushr 8).toByte()
        }
    }

    private fun cborMap(vararg entries: Pair<String, ByteArray>): ByteArray = concat(
        cborLength(5, entries.size),
        *entries.flatMap { (key, value) -> listOf(cborText(key), value) }.toTypedArray(),
    )

    private fun cborArray(vararg values: ByteArray): ByteArray = concat(cborLength(4, values.size), *values)

    private fun cborText(value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        return concat(cborLength(3, bytes.size), bytes)
    }

    private fun cborBytes(value: ByteArray): ByteArray = concat(cborLength(2, value.size), value)

    private fun cborUnsigned(value: Int): ByteArray = cborLength(0, value)

    private fun cborBoolean(value: Boolean): ByteArray = byteArrayOf(if (value) 0xF5.toByte() else 0xF4.toByte())

    private fun cborLength(major: Int, value: Int): ByteArray = when {
        value < 24 -> byteArrayOf(((major shl 5) or value).toByte())
        value < 256 -> byteArrayOf(((major shl 5) or 24).toByte(), value.toByte())
        else -> byteArrayOf(((major shl 5) or 25).toByte(), (value ushr 8).toByte(), value.toByte())
    }

    private fun concat(vararg values: ByteArray): ByteArray {
        val result = ByteArray(values.sumOf { it.size })
        var offset = 0
        values.forEach { value ->
            value.copyInto(result, offset)
            offset += value.size
        }
        return result
    }
}

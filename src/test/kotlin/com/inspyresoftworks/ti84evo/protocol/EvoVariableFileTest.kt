package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EvoVariableFileTest {
    @Test
    fun `inspection exposes metadata and variable data`() {
        val raw = EvoVariablePayload.build(
            EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.NUMBER, "A", "42"),
        )
        val inspection = EvoVariableFile.inspect(raw)

        assertEquals(60L, inspection.metadata["type"])
        assertContentEquals("42".encodeToByteArray(), inspection.data)
        assertEquals(raw.size, inspection.rawBytes)
    }

    @Test
    fun `export appends the two-byte Evo file checksum`() {
        val raw = EvoVariablePayload.build(
            EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.NUMBER, "A", "1"),
        )
        val exported = EvoVariableFile.addChecksum(raw)

        assertEquals(raw.size + 2, exported.size)
        assertContentEquals(raw, exported.copyOf(raw.size))
    }

    @Test
    fun `retargeting preserves native fields and replaces tokenized metadata name`() {
        val raw = EvoVariablePayload.build(
            EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.LIST, "SCORE", "1, 2"),
        )
        val tokenName = byteArrayOf(0x30, 0xE8.toByte(), 0, 0)

        val retargeted = EvoVariableFile.retargetName(raw, tokenName)
        val original = EvoVariableFile.inspect(raw)
        val renamed = EvoVariableFile.inspect(retargeted)

        assertContentEquals(tokenName, renamed.metadata["name"] as ByteArray)
        assertEquals(original.metadata["type"], renamed.metadata["type"])
        assertEquals(original.fields - "data", renamed.fields - "data")
        assertContentEquals(original.data, renamed.data)
    }

    @Test
    fun `clearing a native list preserves metadata and removes its element fields`() {
        val tokenName = byteArrayOf(0x30, 0xE8.toByte())
        val raw = cborMap(
            "metaData" to cborMap(
                "type" to cborUnsigned(1),
                "version" to cborUnsigned(1),
                "name" to cborBytes(tokenName),
            ),
            "version" to cborUnsigned(1),
            "type" to cborUnsigned(0),
            "len" to cborUnsigned(2),
            "arraylen" to cborUnsigned(8),
            "size" to cborUnsigned(19),
            "data" to cborBytes(byteArrayOf(1, 2, 3)),
        )

        val cleared = EvoVariableFile.inspect(EvoVariableFile.clearList(raw))

        assertContentEquals(tokenName, cleared.metadata["name"] as ByteArray)
        assertEquals(0L, cleared.fields["len"])
        assertFalse("arraylen" in cleared.fields)
        assertFalse("size" in cleared.fields)
        assertFalse("data" in cleared.fields)
        assertContentEquals(byteArrayOf(), cleared.data)
    }

    @Test
    fun `one-item native list can be converted to a named native number`() {
        val scalar = byteArrayOf(0, 0, 0, 0, 0, 0, 0x50, 0x42, 1, 1, 0x63, 0)
        val expression = byteArrayOf(0xE5.toByte(), 0) + scalar + byteArrayOf(0xD9.toByte(), 0, 0)
        val list = cborMap(
            "metaData" to cborMap("type" to cborUnsigned(1), "version" to cborUnsigned(1)),
            "version" to cborUnsigned(1),
            "len" to cborUnsigned(1),
            "arraylen" to cborUnsigned(8),
            "size" to cborUnsigned(expression.size),
            "data" to cborBytes(expression),
        )

        val number = EvoVariableFile.inspect(EvoVariableFile.numberFromSingleItemList(list, "D"))

        assertEquals(0L, number.metadata["type"])
        assertContentEquals(byteArrayOf(0x03, 0xE8.toByte(), 0, 0), number.metadata["name"] as ByteArray)
        assertEquals(6L, number.fields["arraylen"])
        assertEquals(12L, number.fields["size"])
        assertContentEquals(scalar, number.data)
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

    private fun cborLength(major: Int, value: Int): ByteArray =
        byteArrayOf(((major shl 5) or value).toByte())

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

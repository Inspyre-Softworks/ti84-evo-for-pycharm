package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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
}

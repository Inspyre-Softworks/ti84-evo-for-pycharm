package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvoVariablePayloadTest {
    @Test
    fun `list payload uses type-60 ASCII importer envelope`() {
        val raw = EvoVariablePayload.build(
            EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.LIST, "L1", "1, 2.5, -3e4"),
        )
        val decoded = CborReader(raw).readComplete() as Map<*, *>
        val metadata = decoded["metaData"] as Map<*, *>

        assertEquals(60L, metadata["type"])
        assertEquals("List", decoded["type"])
        assertEquals("1", decoded["name"])
        assertContentEquals("{1,2.5,-3@E4}".encodeToByteArray(), decoded["data"] as ByteArray)
    }

    @Test
    fun `matrix input normalizes rows and validates their width`() {
        assertEquals(
            "[[1,2][3,4]]",
            EvoVariablePayload.normalizeValue(EvoVariablePayload.Kind.MATRIX, "1, 2\n3 4"),
        )
        assertFailsWith<IllegalArgumentException> {
            EvoVariablePayload.normalizeValue(EvoVariablePayload.Kind.MATRIX, "1,2\n3")
        }
    }

    @Test
    fun `numeric input accepts exact fractions and complex values`() {
        assertEquals("-4+2i", EvoVariablePayload.normalizeValue(EvoVariablePayload.Kind.NUMBER, "-4 + 2i"))
        assertEquals("{1/3,1.5@E6,-2i}", EvoVariablePayload.normalizeValue(EvoVariablePayload.Kind.LIST, "1/3, 1.5e6, -2i"))
        assertFailsWith<IllegalArgumentException> {
            EvoVariablePayload.normalizeValue(EvoVariablePayload.Kind.NUMBER, "1/0")
        }
    }

    @Test
    fun `editable names enforce calculator type rules`() {
        assertEquals("L1", EvoVariablePayload.normalizeName(EvoVariablePayload.Kind.LIST, "L1"))
        assertEquals("SCORE", EvoVariablePayload.normalizeName(EvoVariablePayload.Kind.LIST, "score"))
        assertFailsWith<IllegalArgumentException> {
            EvoVariablePayload.normalizeName(EvoVariablePayload.Kind.NUMBER, "AA")
        }
    }

    @Test
    fun `normalized built-in list names can be built again`() {
        val normalized = EvoVariablePayload.normalizeName(EvoVariablePayload.Kind.LIST, "l1")
        val raw = EvoVariablePayload.build(
            EvoVariablePayload.EditableValue(EvoVariablePayload.Kind.LIST, normalized, "1, 2"),
        )
        val decoded = CborReader(raw).readComplete() as Map<*, *>

        assertEquals("L1", normalized)
        assertEquals("1", decoded["name"])
    }
}

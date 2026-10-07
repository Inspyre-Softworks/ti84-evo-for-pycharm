package com.inspyresoftworks.ti84evo.smartpad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvoKeyTest {
    @Test
    fun `physical key identity is independent of transport numbers`() {
        assertEquals(50, EvoKey.entries.size)
        assertEquals("Y=", EvoKey.Y_EQUALS.displayLabel)
        assertEquals(0x09, EvoScancodeMap.mappingFor(EvoKey.ENTER)?.scancode)
        assertEquals(EvoScancodeEvidence.CONFIRMED, EvoScancodeMap.mappingFor(EvoKey.FIVE)?.evidence)
        assertNull(EvoScancodeMap.mappingFor(EvoKey.GRAPH))
    }
}

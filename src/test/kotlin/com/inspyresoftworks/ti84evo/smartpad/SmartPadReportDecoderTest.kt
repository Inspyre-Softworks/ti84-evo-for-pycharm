package com.inspyresoftworks.ti84evo.smartpad

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartPadReportDecoderTest {
    @Test
    fun `key down and key up are derived from successive reports`() {
        val decoder = SmartPadReportDecoder(
            calculatorKeyMap = mapOf(SmartPadHidChord(0, 0x3A) to "Y="),
        )

        val down = decoder.accept(report(0x00, 0x3A), timestampNanos = 10)
        val held = decoder.accept(report(0x00, 0x3A), timestampNanos = 20)
        val up = decoder.accept(report(), timestampNanos = 30)

        assertEquals(1, down.size)
        assertEquals(SmartPadKeyTransition.DOWN, down.single().transition)
        assertEquals("F1", down.single().hostKey)
        assertEquals("Y=", down.single().calculatorKey)
        assertTrue(held.isEmpty())
        assertEquals(SmartPadKeyTransition.UP, up.single().transition)
        assertContentEquals(report(), up.single().rawReport)
    }

    @Test
    fun `modifier changes and multiple keys are decoded independently`() {
        val decoder = SmartPadReportDecoder()

        val events = decoder.accept(report(modifiers = 0x03, 0x04, 0x05), timestampNanos = 1)

        assertEquals(
            listOf(0x04, 0x05, 0xE0, 0xE1),
            events.map { it.usage },
        )
        assertEquals(
            listOf("A", "B", "LeftControl", "LeftShift"),
            events.map { it.hostKey },
        )
    }

    @Test
    fun `unknown usages are preserved and visibly named`() {
        val state = assertIs<SmartPadDecodeResult.Valid>(
            SmartPadReportDecoder().decode(report(0, 0xFE)),
        ).state

        assertEquals(listOf(0xFE, 0, 0, 0, 0, 0), state.keySlots)
        assertEquals("Unknown(0xFE)", SmartPadKeyMap.hostKeyName(0xFE))
    }

    @Test
    fun `reserved byte and duplicate slots remain available`() {
        val raw = byteArrayOf(0, 0x7F, 4, 4, 0, 0, 0, 0)
        val state = assertIs<SmartPadDecodeResult.Valid>(SmartPadReportDecoder().decode(raw)).state

        assertEquals(0x7F, state.reservedByte)
        assertEquals(listOf(4, 4, 0, 0, 0, 0), state.keySlots)
        assertEquals(setOf(4), state.usages)
        assertContentEquals(raw, state.raw)
    }

    @Test
    fun `malformed report retains bytes and does not mutate key state`() {
        val decoder = SmartPadReportDecoder()
        decoder.accept(report(0, 0x04))
        val raw = byteArrayOf(0, 0, 4)

        val malformed = assertIs<SmartPadDecodeResult.Malformed>(decoder.decode(raw))
        assertContentEquals(raw, malformed.raw)
        assertTrue(malformed.reason.contains("expected 8"))
        assertTrue(decoder.accept(raw).isEmpty())
        assertTrue(decoder.accept(report(0, 0x04)).isEmpty())
    }

    @Test
    fun `report ID prefix is supported`() {
        val decoder = SmartPadReportDecoder(mapOf(2 to 9))
        val raw = byteArrayOf(2) + report(modifiers = 0x02, 0x04)

        val state = assertIs<SmartPadDecodeResult.Valid>(decoder.decode(raw)).state
        assertEquals(2, state.reportId)
        assertEquals(setOf(0xE1), state.modifiers)
        assertEquals(0x04, state.keySlots.first())
        assertIs<SmartPadDecodeResult.Malformed>(decoder.decode(byteArrayOf(3) + report()))
    }

    @Test
    fun `unknown calculator chord has no calculator mapping`() {
        val event = SmartPadReportDecoder().accept(report(0, 0x3A)).single()
        assertNull(event.calculatorKey)
    }

    @Test
    fun `captured physical map contains all 50 unique calculator keys`() {
        assertEquals(50, SmartPadKeyMap.calculatorKeys.size)
        assertEquals(50, SmartPadKeyMap.calculatorKeys.values.toSet().size)
        assertEquals("Y=", SmartPadKeyMap.calculatorKeyName(0x00, 0x6F))
        assertEquals("WINDOW", SmartPadKeyMap.calculatorKeyName(0x05, 0x6C))
        assertEquals("LOG", SmartPadKeyMap.calculatorKeyName(0x02, 0x69))
        assertEquals("ON", SmartPadKeyMap.calculatorKeyName(0x00, 0x29))
        assertEquals("ENTER", SmartPadKeyMap.calculatorKeyName(0x00, 0x28))
    }

    @Test
    fun `modifier usage chord identifies calculator key without labeling modifiers`() {
        val events = SmartPadReportDecoder().accept(report(0x05, 0x6C))

        assertEquals("WINDOW", events.single { it.usage == 0x6C }.calculatorKey)
        assertTrue(events.filter { it.usage in 0xE0..0xE7 }.all { it.calculatorKey == null })
        assertEquals("F17", SmartPadKeyMap.hostKeyName(0x6C))
    }

    @Test
    fun `modifier-only change releases and presses reused HID usage as distinct calculator keys`() {
        val decoder = SmartPadReportDecoder()
        decoder.accept(report(0x03, 0x69))

        val events = decoder.accept(report(0x02, 0x69))

        assertEquals(
            listOf(
                SmartPadKeyTransition.UP to "9",
                SmartPadKeyTransition.UP to null,
                SmartPadKeyTransition.DOWN to "LOG",
            ),
            events.map { it.transition to it.calculatorKey },
        )
    }

    @Test
    fun `release retains the calculator mapping from the preceding chord`() {
        val decoder = SmartPadReportDecoder()
        decoder.accept(report(0x05, 0x6C))

        val released = decoder.accept(report())

        assertEquals("WINDOW", released.single { it.usage == 0x6C }.calculatorKey)
    }

    private fun report(modifiers: Int = 0, vararg usages: Int): ByteArray =
        byteArrayOf(modifiers.toByte(), 0) +
            usages.take(6).map(Int::toByte).toByteArray() +
            ByteArray(6 - usages.take(6).size)
}

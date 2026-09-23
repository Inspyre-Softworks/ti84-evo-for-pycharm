package com.inspyresoftworks.ti84evo.smartpad

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartPadHidDescriptorTest {
    @Test
    fun `OS 7_1 package descriptor decodes boot keyboard input and LED output`() {
        val descriptor = SmartPadHidDescriptor.parse(OS_7_1_PACKAGE_REPORT_DESCRIPTOR)

        assertEquals(setOf(0), descriptor.reportIds)
        assertFalse(descriptor.hasReportIds)
        assertEquals(emptySet(), descriptor.vendorDefinedUsagePages)
        assertEquals(64, descriptor.reportBits(HidReportType.INPUT))
        assertEquals(8, descriptor.reportBytes(HidReportType.INPUT))
        assertEquals(8, descriptor.reportBits(HidReportType.OUTPUT))
        assertEquals(1, descriptor.reportBytes(HidReportType.OUTPUT))
        assertEquals(0, descriptor.reportBits(HidReportType.FEATURE))

        val inputs = descriptor.fields.filter { it.type == HidReportType.INPUT }
        assertEquals(3, inputs.size)
        assertEquals(0, inputs[0].bitOffset)
        assertEquals(1, inputs[0].reportSize)
        assertEquals(8, inputs[0].reportCount)
        assertEquals(0x07, inputs[0].usagePage)
        assertEquals(HidUsage(0x07, 0xE0), inputs[0].usageMinimum)
        assertEquals(HidUsage(0x07, 0xE7), inputs[0].usageMaximum)
        assertTrue(inputs[0].isVariable)

        assertEquals(8, inputs[1].bitOffset)
        assertTrue(inputs[1].isConstant)

        assertEquals(16, inputs[2].bitOffset)
        assertEquals(8, inputs[2].reportSize)
        assertEquals(6, inputs[2].reportCount)
        assertFalse(inputs[2].isVariable)

        val outputs = descriptor.fields.filter { it.type == HidReportType.OUTPUT }
        assertEquals(2, outputs.size)
        assertEquals(0x08, outputs[0].usagePage)
        assertEquals(5, outputs[0].reportCount)
        assertTrue(outputs[0].isVariable)
        assertEquals(5, outputs[1].bitOffset)
        assertEquals(3, outputs[1].reportSize)
        assertTrue(outputs[1].isConstant)

        assertTrue(descriptor.humanReadableDump().contains("Keyboard/Keypad"))
        assertTrue(descriptor.humanReadableDump().contains("OUTPUT id=0 8 bits"))
    }

    @Test
    fun `report IDs contribute a wire prefix byte`() {
        val raw = bytes("85 02 75 08 95 02 81 02")
        val descriptor = SmartPadHidDescriptor.parse(raw)

        assertEquals(setOf(2), descriptor.reportIds)
        assertTrue(descriptor.hasReportIds)
        assertEquals(16, descriptor.reportBits(HidReportType.INPUT, 2))
        assertEquals(3, descriptor.reportBytes(HidReportType.INPUT, 2))
    }

    @Test
    fun `unknown and long items remain lossless`() {
        val raw = bytes("FC FE 03 A5 10 20 30")
        val descriptor = SmartPadHidDescriptor.parse(raw)

        assertEquals(2, descriptor.items.size)
        assertEquals(HidItemType.RESERVED, descriptor.items[0].type)
        assertEquals(HidItemType.LONG, descriptor.items[1].type)
        assertEquals(0xA5, descriptor.items[1].tag)
        assertContentEquals(bytes("10 20 30"), descriptor.items[1].data)
    }

    @Test
    fun `truncated item is rejected with its offset`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SmartPadHidDescriptor.parse(bytes("06 01"))
        }
        assertTrue(error.message.orEmpty().contains("offset 0"))
    }

    companion object {
        // Exact 63-byte descriptor embedded in TI84Evo_Package.84pk2 from the
        // 7.1.0.4421 bundle. The live configuration advertises the same length.
        val OS_7_1_PACKAGE_REPORT_DESCRIPTOR = bytes(
            "05 01 09 06 A1 01 05 07 19 E0 29 E7 15 00 25 01 " +
                "75 01 95 08 81 02 95 01 75 08 81 01 95 05 75 01 " +
                "05 08 19 01 29 05 91 02 95 01 75 03 91 01 95 06 " +
                "75 08 15 00 25 FF 05 07 19 00 29 FF 81 00 C0",
        )

        private fun bytes(hex: String): ByteArray = hex.split(' ')
            .filter(String::isNotBlank)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}

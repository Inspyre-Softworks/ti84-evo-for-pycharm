package com.inspyresoftworks.ti84evo.smartpad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartPadDeviceDetectorTest {
    private val os71Interfaces = listOf(
        SmartPadUsbInterface(0, 0, 0x02, 0x02, 0x01, emptyList()),
        SmartPadUsbInterface(1, 0, 0x0A, 0, 0, emptyList()),
        SmartPadUsbInterface(2, 0, 0xFF, 0, 0, emptyList()),
        SmartPadUsbInterface(
            3,
            0,
            0x03,
            0x01,
            0x01,
            listOf(SmartPadUsbEndpoint(0x84, 0x03, 64, 16)),
        ),
    )

    @Test
    fun `enumerated HID does not by itself imply SmartPad is running`() {
        val status = SmartPadDeviceDetector.detect(true, os71Interfaces, smartPadInputObserved = false)

        assertEquals(SmartPadDeviceMode.CDC_CONNECTED, status.mode)
        assertTrue(status.cdcPresent)
        assertTrue(status.hidInterfacePresent)
        assertFalse(status.smartPadInputObserved)
    }

    @Test
    fun `HID traffic plus CDC is reported as coexistence`() {
        val status = SmartPadDeviceDetector.detect(true, os71Interfaces, smartPadInputObserved = true)
        assertEquals(SmartPadDeviceMode.CDC_AND_SMARTPAD_HID, status.mode)
    }

    @Test
    fun `disconnected overrides stale interface state`() {
        val status = SmartPadDeviceDetector.detect(false, os71Interfaces, smartPadInputObserved = true)
        assertEquals(SmartPadDeviceMode.DISCONNECTED, status.mode)
        assertFalse(status.cdcPresent)
        assertFalse(status.hidInterfacePresent)
    }
}

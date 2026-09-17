package com.inspyresoftworks.ti84evo.smartpad

data class SmartPadUsbEndpoint(
    val address: Int,
    val attributes: Int,
    val maxPacketSize: Int,
    val interval: Int,
) {
    val direction: String get() = if (address and 0x80 != 0) "IN" else "OUT"
    val transferType: String
        get() = when (attributes and 0x03) {
            0 -> "control"
            1 -> "isochronous"
            2 -> "bulk"
            else -> "interrupt"
        }
}

data class SmartPadUsbInterface(
    val number: Int,
    val alternateSetting: Int,
    val classCode: Int,
    val subclassCode: Int,
    val protocolCode: Int,
    val endpoints: List<SmartPadUsbEndpoint>,
    val extraDescriptors: ByteArray = byteArrayOf(),
)

enum class SmartPadDeviceMode {
    DISCONNECTED,
    NORMAL,
    CDC_CONNECTED,
    SMARTPAD_HID,
    CDC_AND_SMARTPAD_HID,
}

data class SmartPadDeviceStatus(
    val mode: SmartPadDeviceMode,
    val cdcPresent: Boolean,
    val hidInterfacePresent: Boolean,
    val smartPadInputObserved: Boolean,
)

/** Pure USB-layout detector; platform enumeration remains in diagnostics. */
object SmartPadDeviceDetector {
    fun detect(
        connected: Boolean,
        interfaces: List<SmartPadUsbInterface>,
        smartPadInputObserved: Boolean,
    ): SmartPadDeviceStatus {
        if (!connected) {
            return SmartPadDeviceStatus(SmartPadDeviceMode.DISCONNECTED, false, false, false)
        }
        val hasControl = interfaces.any { it.classCode == 0x02 }
        val hasData = interfaces.any { it.classCode == 0x0A }
        val cdc = hasControl && hasData
        val hid = interfaces.any {
            it.classCode == 0x03 && it.subclassCode == 0x01 && it.protocolCode == 0x01
        }
        val activeHid = hid && smartPadInputObserved
        val mode = when {
            cdc && activeHid -> SmartPadDeviceMode.CDC_AND_SMARTPAD_HID
            activeHid -> SmartPadDeviceMode.SMARTPAD_HID
            cdc -> SmartPadDeviceMode.CDC_CONNECTED
            else -> SmartPadDeviceMode.NORMAL
        }
        return SmartPadDeviceStatus(mode, cdc, hid, activeHid)
    }
}

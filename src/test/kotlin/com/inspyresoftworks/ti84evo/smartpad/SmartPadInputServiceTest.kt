package com.inspyresoftworks.ti84evo.smartpad

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SmartPadInputServiceTest {
    @Test
    fun `device path parsing identifies only the Evo HID interface`() {
        val hid = SmartPadInputDevice.fromWindowsPath(
            "\\\\?\\HID#VID_0451&PID_E018&MI_03#7&123&0&0000",
        )
        val vendorInterface = SmartPadInputDevice.fromWindowsPath(
            "\\\\?\\USB#VID_0451&PID_E018&MI_02#Evo",
        )

        assertTrue(hid.isTi84EvoSmartPad)
        assertEquals(0x0451, hid.vendorId)
        assertEquals(3, hid.interfaceNumber)
        assertTrue(!vendorInterface.isTi84EvoSmartPad)
    }

    @Test
    fun `service is opt in decodes only identified devices and labels physical origin`() {
        val backend = FakeBackend()
        val service = SmartPadInputService { backend }
        val states = mutableListOf<SmartPadInputState>()
        val events = mutableListOf<SmartPadInputEvent>()
        service.addStateListener { state, _ -> states += state }
        service.addListener(events::add)

        assertEquals(SmartPadInputState.DISABLED, service.state)
        assertEquals(SmartPadInputState.LISTENING, service.start())
        backend.emit(device(interfaceNumber = 2), report(0x05, 0x6C))
        backend.emit(device(), report(0x05))
        backend.emit(device(), report(0x05, 0x6C))
        backend.emit(device(), report())

        assertEquals(listOf(SmartPadInputState.STARTING, SmartPadInputState.LISTENING), states)
        assertEquals(listOf(SmartPadKeyTransition.DOWN, SmartPadKeyTransition.UP), events.map { it.keyEvent.transition })
        assertTrue(events.all { it.keyEvent.evoKey == EvoKey.WINDOW })
        assertTrue(events.all { it.origin == SmartPadInputOrigin.PHYSICAL_SMARTPAD })
        assertEquals(SmartPadHidChord(0x05, 0x6C), events.last().rawHidChord)

        service.stop()
        assertEquals(SmartPadInputState.DISABLED, service.state)
        assertTrue(backend.stopped)
    }

    @Test
    fun `unsupported backend and backend failures are explicit states`() {
        val unsupported = SmartPadInputService { FakeBackend(SmartPadBackendSupport.UNSUPPORTED_PLATFORM) }
        assertEquals(SmartPadInputState.UNSUPPORTED, unsupported.start())

        val backend = FakeBackend()
        val service = SmartPadInputService { backend }
        service.start()
        backend.fail(IllegalStateException("raw input stopped"))
        assertEquals(SmartPadInputState.FAILED, service.state)
        assertNotNull(service.lastError)
    }

    @Test
    fun `windows assembler reconstructs a complete atomic TI chord`() {
        val assembler = WindowsSmartPadReportAssembler()

        assertEquals(null, assembler.accept(0x1D, 0, 0x11))
        assertEquals(null, assembler.accept(0x38, 0, 0x12))
        assertContentEquals(report(0x05, 0x6C), assembler.accept(0x68, 0, 0x80))
        assertContentEquals(report(), assembler.accept(0x68, 1, 0x80))
        assertEquals(null, assembler.accept(0x38, 1, 0x12))
        assertEquals(null, assembler.accept(0x1D, 1, 0x11))
    }

    @Test
    fun `windows backend registers and stops its hidden window`() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val backend = WindowsRawInputSmartPadBackend()
        val errors = mutableListOf<Throwable>()
        try {
            backend.start(object : SmartPadInputBackend.Sink {
                override fun onReport(report: SmartPadRawInputReport) = Unit
                override fun onError(error: Throwable) {
                    errors += error
                }
            })
        } finally {
            backend.stop()
        }
        assertTrue(errors.isEmpty(), errors.joinToString { it.message ?: it.javaClass.simpleName })
    }

    private fun device(interfaceNumber: Int = 3) = SmartPadInputDevice(
        id = "device-$interfaceNumber",
        path = "test",
        vendorId = 0x0451,
        productId = 0xE018,
        interfaceNumber = interfaceNumber,
    )

    private fun report(modifiers: Int = 0, usage: Int = 0): ByteArray =
        byteArrayOf(modifiers.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0)

    private class FakeBackend(
        override val support: SmartPadBackendSupport = SmartPadBackendSupport.SUPPORTED,
    ) : SmartPadInputBackend {
        private var sink: SmartPadInputBackend.Sink? = null
        var stopped = false

        override fun start(sink: SmartPadInputBackend.Sink) {
            this.sink = sink
        }

        override fun stop() {
            stopped = true
        }

        fun emit(device: SmartPadInputDevice, report: ByteArray) {
            sink?.onReport(SmartPadRawInputReport(device, report, 1))
        }

        fun fail(error: Throwable) {
            sink?.onError(error)
        }
    }
}

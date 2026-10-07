package com.inspyresoftworks.ti84evo.smartpad

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HINSTANCE
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Windows device-specific backend using Raw Input keyboard events.
 *
 * Windows exposes the TI interface as a keyboard-class collection, so Raw
 * Input supplies device identity plus make/break events rather than the USB
 * packet verbatim. [WindowsSmartPadReportAssembler] reconstructs the standard
 * eight-byte state report and the normal decoder remains the only interpreter.
 * RIDEV_NOLEGACY is intentionally absent, so Windows continues normal handling.
 */
internal class WindowsRawInputSmartPadBackend : SmartPadInputBackend {
    override val support = SmartPadBackendSupport.SUPPORTED

    @Volatile
    private var running = false
    private var messageThread: Thread? = null
    private var messageThreadId: Int = 0
    private var sink: SmartPadInputBackend.Sink? = null
    private val deviceNames = mutableMapOf<Long, SmartPadInputDevice?>()
    private val assemblers = mutableMapOf<String, WindowsSmartPadReportAssembler>()

    override fun start(sink: SmartPadInputBackend.Sink) {
        check(!running) { "Windows Raw Input backend is already running" }
        this.sink = sink
        running = true
        val ready = CountDownLatch(1)
        val startupError = AtomicReference<Throwable?>()
        messageThread = Thread({ runMessageLoop(ready, startupError) }, "TI-84 Evo SmartPad Raw Input").apply {
            isDaemon = true
            start()
        }
        if (!ready.await(5, TimeUnit.SECONDS)) {
            stop()
            error("Timed out while starting Windows Raw Input")
        }
        startupError.get()?.let {
            stop()
            throw it
        }
    }

    override fun stop() {
        running = false
        val thread = messageThread
        if (messageThreadId != 0) {
            User32.INSTANCE.PostThreadMessage(messageThreadId, WinUser.WM_QUIT, WPARAM(0), LPARAM(0))
        }
        if (thread != null && thread !== Thread.currentThread()) thread.join(2_000)
        messageThread = null
        messageThreadId = 0
        sink = null
        synchronized(deviceNames) {
            deviceNames.clear()
            assemblers.clear()
        }
    }

    private fun runMessageLoop(ready: CountDownLatch, startupError: AtomicReference<Throwable?>) {
        val user32 = User32.INSTANCE
        val rawUser32 = RawInputUser32.INSTANCE
        val className = "Ti84EvoSmartPadRawInput-${System.identityHashCode(this)}"
        val module = Kernel32.INSTANCE.GetModuleHandle(null)
        val instance = HINSTANCE().apply { pointer = module.pointer }
        var window: HWND? = null
        var registeredClass = false
        val windowProc = WinUser.WindowProc { hwnd, message, wParam, lParam ->
            if (message == WM_INPUT) {
                runCatching { handleRawInput(lParam) }.onFailure { sink?.onError(it) }
                LRESULT(0)
            } else {
                user32.DefWindowProc(hwnd, message, wParam, lParam)
            }
        }

        try {
            messageThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val windowClass = WinUser.WNDCLASSEX().apply {
                cbSize = size()
                lpfnWndProc = windowProc
                hInstance = instance
                lpszClassName = className
            }
            check(user32.RegisterClassEx(windowClass).toInt() != 0) {
                "RegisterClassEx failed with Windows error ${Kernel32.INSTANCE.GetLastError()}"
            }
            registeredClass = true
            window = user32.CreateWindowEx(
                0, className, "TI-84 Evo SmartPad Raw Input", 0,
                0, 0, 0, 0, null, null, instance, null,
            )
            check(window != null) {
                "CreateWindowEx failed with Windows error ${Kernel32.INSTANCE.GetLastError()}"
            }
            checkNoExistingKeyboardRegistration(rawUser32)
            val registration = RawInputDevice().apply {
                usagePage = 0x01
                usage = 0x06
                flags = RIDEV_INPUTSINK or RIDEV_DEVNOTIFY
                target = window
                write()
            }
            check(rawUser32.RegisterRawInputDevices(registration.pointer, 1, registration.size())) {
                "RegisterRawInputDevices failed with Windows error ${Kernel32.INSTANCE.GetLastError()}"
            }
            ready.countDown()

            val message = WinUser.MSG()
            while (running) {
                val result = user32.GetMessage(message, null, 0, 0)
                if (result <= 0) break
                user32.TranslateMessage(message)
                user32.DispatchMessage(message)
            }
        } catch (error: Throwable) {
            if (ready.count > 0) startupError.set(error) else sink?.onError(error)
        } finally {
            ready.countDown()
            unregisterRawInput(rawUser32)
            window?.let(user32::DestroyWindow)
            if (registeredClass) user32.UnregisterClass(className, instance)
            running = false
            // Keep the callback strongly reachable until the native window is gone.
            windowProc.hashCode()
        }
    }

    private fun unregisterRawInput(user32: RawInputUser32) {
        runCatching {
            val removal = RawInputDevice().apply {
                usagePage = 0x01
                usage = 0x06
                flags = RIDEV_REMOVE
                target = null
                write()
            }
            user32.RegisterRawInputDevices(removal.pointer, 1, removal.size())
        }
    }

    private fun checkNoExistingKeyboardRegistration(user32: RawInputUser32) {
        val count = IntByReference()
        check(user32.GetRegisteredRawInputDevices(null, count, RawInputDevice().size()) != UINT_ERROR) {
            "GetRegisteredRawInputDevices(size) failed with Windows error ${Kernel32.INSTANCE.GetLastError()}"
        }
        if (count.value == 0) return
        val structureSize = RawInputDevice().size()
        val registrations = Memory(count.value.toLong() * structureSize)
        check(user32.GetRegisteredRawInputDevices(registrations, count, structureSize) != UINT_ERROR) {
            "GetRegisteredRawInputDevices failed with Windows error ${Kernel32.INSTANCE.GetLastError()}"
        }
        repeat(count.value) { index ->
            val offset = index.toLong() * structureSize
            val page = registrations.getShort(offset).toInt() and 0xFFFF
            val usage = registrations.getShort(offset + 2).toInt() and 0xFFFF
            val flags = registrations.getInt(offset + 4)
            val ownsKeyboard = page == 0x01 && (
                usage == 0x06 || (usage == 0 && flags and RIDEV_PAGEONLY != 0)
            )
            check(!ownsKeyboard) {
                "Another IDE component already owns the Windows Raw Input keyboard registration"
            }
        }
    }

    private fun handleRawInput(lParam: LPARAM) {
        val handle = Pointer.createConstant(lParam.toLong())
        val size = IntByReference()
        val rawUser32 = RawInputUser32.INSTANCE
        val headerSize = RAW_INPUT_HEADER_BYTES
        check(rawUser32.GetRawInputData(handle, RID_INPUT, null, size, headerSize) != UINT_ERROR) {
            "GetRawInputData(size) failed with Windows error ${Kernel32.INSTANCE.GetLastError()}"
        }
        if (size.value < headerSize + RAW_KEYBOARD_BYTES) return
        val raw = Memory(size.value.toLong())
        check(rawUser32.GetRawInputData(handle, RID_INPUT, raw, size, headerSize) != UINT_ERROR) {
            "GetRawInputData failed with Windows error ${Kernel32.INSTANCE.GetLastError()}"
        }
        if (raw.getInt(0) != RIM_TYPEKEYBOARD) return

        val devicePointer = raw.getPointer(8)
        val deviceKey = Pointer.nativeValue(devicePointer)
        val device = synchronized(deviceNames) {
            deviceNames.getOrPut(deviceKey) { readDevice(devicePointer) }
        } ?: return
        if (!device.isTi84EvoSmartPad) return

        val offset = headerSize.toLong()
        val makeCode = raw.getShort(offset).toInt() and 0xFFFF
        val flags = raw.getShort(offset + 2).toInt() and 0xFFFF
        val virtualKey = raw.getShort(offset + 6).toInt() and 0xFFFF
        val assembler = synchronized(deviceNames) {
            assemblers.getOrPut(device.id) { WindowsSmartPadReportAssembler() }
        }
        assembler.accept(makeCode, flags, virtualKey)?.let { report ->
            sink?.onReport(SmartPadRawInputReport(device, report))
        }
    }

    private fun readDevice(handle: Pointer): SmartPadInputDevice? {
        val size = IntByReference()
        val user32 = RawInputUser32.INSTANCE
        if (user32.GetRawInputDeviceInfo(handle, RIDI_DEVICENAME, null, size) == UINT_ERROR || size.value <= 0) {
            return null
        }
        val buffer = Memory((size.value + 1L) * Native.WCHAR_SIZE)
        if (user32.GetRawInputDeviceInfo(handle, RIDI_DEVICENAME, buffer, size) == UINT_ERROR) return null
        return SmartPadInputDevice.fromWindowsPath(buffer.getWideString(0))
    }

    @Structure.FieldOrder("usagePage", "usage", "flags", "target")
    internal class RawInputDevice : Structure() {
        @JvmField var usagePage: Short = 0
        @JvmField var usage: Short = 0
        @JvmField var flags: Int = 0
        @JvmField var target: HWND? = null
    }

    private interface RawInputUser32 : StdCallLibrary {
        fun RegisterRawInputDevices(devices: Pointer, count: Int, structureSize: Int): Boolean
        fun GetRegisteredRawInputDevices(devices: Pointer?, count: IntByReference, structureSize: Int): Int
        fun GetRawInputData(input: Pointer, command: Int, data: Pointer?, size: IntByReference, headerSize: Int): Int
        fun GetRawInputDeviceInfo(device: Pointer, command: Int, data: Pointer?, size: IntByReference): Int

        companion object {
            val INSTANCE: RawInputUser32 = Native.load("user32", RawInputUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    companion object {
        private const val WM_INPUT = 0x00FF
        private const val RID_INPUT = 0x10000003
        private const val RIDI_DEVICENAME = 0x20000007
        private const val RIM_TYPEKEYBOARD = 1
        private const val RIDEV_REMOVE = 0x00000001
        private const val RIDEV_PAGEONLY = 0x00000020
        private const val RIDEV_INPUTSINK = 0x00000100
        private const val RIDEV_DEVNOTIFY = 0x00002000
        private const val RAW_KEYBOARD_BYTES = 16
        private const val UINT_ERROR = -1
        private val RAW_INPUT_HEADER_BYTES = 8 + Native.POINTER_SIZE * 2
    }
}

/** Stateful conversion from one Windows keyboard device to boot-report state. */
internal class WindowsSmartPadReportAssembler {
    private var modifierByte = 0
    private var usage = 0

    fun accept(makeCode: Int, flags: Int, virtualKey: Int): ByteArray? {
        val released = flags and RI_KEY_BREAK != 0
        modifierBit(makeCode, flags, virtualKey)?.let { bit ->
            modifierByte = if (released) modifierByte and (1 shl bit).inv() else modifierByte or (1 shl bit)
            // Windows decomposes one TI report into modifier and key records.
            // Do not expose transient modifier-only states: emit one atomic
            // chord when the non-modifier arrives and one empty release.
            return null
        }

        val mapped = hidUsage(virtualKey) ?: return null
        val before = usage
        usage = when {
            released && usage == mapped -> 0
            released -> usage
            else -> mapped
        }
        return when {
            before == usage -> null
            usage == 0 -> ByteArray(SmartPadReportDecoder.BOOT_REPORT_BYTES)
            else -> report()
        }
    }

    private fun report(): ByteArray = byteArrayOf(
        modifierByte.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0,
    )

    private fun modifierBit(makeCode: Int, flags: Int, virtualKey: Int): Int? = when (virtualKey) {
        VK_LCONTROL -> 0
        VK_LSHIFT -> 1
        VK_LMENU -> 2
        VK_LWIN -> 3
        VK_RCONTROL -> 4
        VK_RSHIFT -> 5
        VK_RMENU -> 6
        VK_RWIN -> 7
        VK_CONTROL -> if (flags and RI_KEY_E0 != 0) 4 else 0
        VK_SHIFT -> if (makeCode == 0x36) 5 else 1
        VK_MENU -> if (flags and RI_KEY_E0 != 0) 6 else 2
        else -> null
    }

    private fun hidUsage(virtualKey: Int): Int? = when (virtualKey) {
        VK_RETURN -> 0x28
        VK_ESCAPE -> 0x29
        VK_DELETE -> 0x4C
        VK_RIGHT -> 0x4F
        VK_LEFT -> 0x50
        VK_DOWN -> 0x51
        VK_UP -> 0x52
        VK_DIVIDE -> 0x54
        VK_MULTIPLY -> 0x55
        VK_SUBTRACT -> 0x56
        VK_ADD -> 0x57
        in VK_F14..VK_F20 -> 0x69 + (virtualKey - VK_F14)
        else -> null
    }

    companion object {
        private const val RI_KEY_BREAK = 0x0001
        private const val RI_KEY_E0 = 0x0002
        private const val VK_SHIFT = 0x10
        private const val VK_CONTROL = 0x11
        private const val VK_MENU = 0x12
        private const val VK_RETURN = 0x0D
        private const val VK_ESCAPE = 0x1B
        private const val VK_LEFT = 0x25
        private const val VK_UP = 0x26
        private const val VK_RIGHT = 0x27
        private const val VK_DOWN = 0x28
        private const val VK_DELETE = 0x2E
        private const val VK_LSHIFT = 0xA0
        private const val VK_RSHIFT = 0xA1
        private const val VK_LCONTROL = 0xA2
        private const val VK_RCONTROL = 0xA3
        private const val VK_LMENU = 0xA4
        private const val VK_RMENU = 0xA5
        private const val VK_LWIN = 0x5B
        private const val VK_RWIN = 0x5C
        private const val VK_MULTIPLY = 0x6A
        private const val VK_ADD = 0x6B
        private const val VK_SUBTRACT = 0x6D
        private const val VK_DIVIDE = 0x6F
        private const val VK_F14 = 0x7D
        private const val VK_F20 = 0x83
    }
}

package com.inspyresoftworks.ti84evo.smartpad

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import java.util.concurrent.CopyOnWriteArrayList

data class SmartPadInputDevice(
    val id: String,
    val path: String,
    val vendorId: Int?,
    val productId: Int?,
    val interfaceNumber: Int?,
) {
    val isTi84EvoSmartPad: Boolean
        get() = vendorId == TI_VENDOR_ID && productId == EVO_PRODUCT_ID && interfaceNumber == HID_INTERFACE

    companion object {
        const val TI_VENDOR_ID = 0x0451
        const val EVO_PRODUCT_ID = 0xE018
        const val HID_INTERFACE = 3

        private val vid = Regex("(?i)(?:^|[#&])VID_([0-9A-F]{4})(?:[#&]|$)")
        private val pid = Regex("(?i)(?:^|[#&])PID_([0-9A-F]{4})(?:[#&]|$)")
        private val mi = Regex("(?i)(?:^|[#&])MI_([0-9A-F]{2})(?:[#&]|$)")

        fun fromWindowsPath(path: String): SmartPadInputDevice {
            fun value(regex: Regex): Int? = regex.find(path)?.groupValues?.get(1)?.toIntOrNull(16)
            return SmartPadInputDevice(path.lowercase(), path, value(vid), value(pid), value(mi))
        }
    }
}

data class SmartPadRawInputReport(
    val device: SmartPadInputDevice,
    val rawReport: ByteArray,
    val timestampNanos: Long = System.nanoTime(),
)

enum class SmartPadInputOrigin { PHYSICAL_SMARTPAD, HOST_INJECTED, REPLAY }

data class SmartPadInputEvent(
    val device: SmartPadInputDevice,
    val keyEvent: SmartPadKeyEvent,
    val origin: SmartPadInputOrigin,
) {
    val rawHidChord: SmartPadHidChord?
        get() = keyEvent.hidChord
}

enum class SmartPadBackendSupport { SUPPORTED, UNSUPPORTED_PLATFORM }

interface SmartPadInputBackend : AutoCloseable {
    val support: SmartPadBackendSupport

    fun start(sink: Sink)
    fun stop()
    override fun close() = stop()

    interface Sink {
        fun onReport(report: SmartPadRawInputReport)
        fun onError(error: Throwable)
    }
}

enum class SmartPadInputState { DISABLED, STARTING, LISTENING, UNSUPPORTED, FAILED }

/**
 * Opt-in application service for device-specific SmartPad input.
 *
 * It never installs a generic IDE shortcut listener. The Windows backend uses
 * Raw Input to retain keyboard device identity and does not suppress normal OS
 * keyboard handling. Call [start] only for a user-enabled SmartPad session.
 */
@Service(Service.Level.APP)
class SmartPadInputService(
    private val backendFactory: () -> SmartPadInputBackend = ::platformSmartPadInputBackend,
) : Disposable {
    fun interface Listener {
        fun onInput(event: SmartPadInputEvent)
    }

    fun interface StateListener {
        fun onStateChanged(state: SmartPadInputState, error: Throwable?)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val stateListeners = CopyOnWriteArrayList<StateListener>()
    private val decoders = mutableMapOf<String, SmartPadReportDecoder>()

    @Volatile
    var state: SmartPadInputState = SmartPadInputState.DISABLED
        private set

    @Volatile
    var lastError: Throwable? = null
        private set

    private var backend: SmartPadInputBackend? = null

    @Synchronized
    fun start(): SmartPadInputState {
        if (state == SmartPadInputState.STARTING || state == SmartPadInputState.LISTENING) return state
        transition(SmartPadInputState.STARTING)
        val candidate = backendFactory()
        if (candidate.support != SmartPadBackendSupport.SUPPORTED) {
            candidate.close()
            transition(SmartPadInputState.UNSUPPORTED)
            return state
        }
        return try {
            backend = candidate
            candidate.start(object : SmartPadInputBackend.Sink {
                override fun onReport(report: SmartPadRawInputReport) = accept(report)
                override fun onError(error: Throwable) = fail(error)
            })
            if (state == SmartPadInputState.STARTING) transition(SmartPadInputState.LISTENING)
            state
        } catch (error: Throwable) {
            candidate.close()
            backend = null
            transition(SmartPadInputState.FAILED, error)
            state
        }
    }

    @Synchronized
    fun stop() {
        backend?.close()
        backend = null
        decoders.clear()
        transition(SmartPadInputState.DISABLED)
    }

    fun addListener(listener: Listener): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    fun addStateListener(listener: StateListener): AutoCloseable {
        stateListeners += listener
        return AutoCloseable { stateListeners -= listener }
    }

    override fun dispose() = stop()

    @Synchronized
    private fun accept(report: SmartPadRawInputReport) {
        if (state != SmartPadInputState.LISTENING || !report.device.isTi84EvoSmartPad) return
        val decoder = decoders.getOrPut(report.device.id) { SmartPadReportDecoder() }
        decoder.accept(report.rawReport, report.timestampNanos)
            .asSequence()
            .filter { it.evoKey != null }
            .map { SmartPadInputEvent(report.device, it, SmartPadInputOrigin.PHYSICAL_SMARTPAD) }
            .forEach { event -> listeners.forEach { it.onInput(event) } }
    }

    @Synchronized
    private fun fail(error: Throwable) {
        backend?.close()
        backend = null
        decoders.clear()
        transition(SmartPadInputState.FAILED, error)
    }

    private fun transition(next: SmartPadInputState, error: Throwable? = null) {
        state = next
        lastError = error
        stateListeners.forEach { it.onStateChanged(next, error) }
    }
}

internal fun platformSmartPadInputBackend(): SmartPadInputBackend =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) WindowsRawInputSmartPadBackend()
    else UnsupportedSmartPadInputBackend

private object UnsupportedSmartPadInputBackend : SmartPadInputBackend {
    override val support = SmartPadBackendSupport.UNSUPPORTED_PLATFORM
    override fun start(sink: SmartPadInputBackend.Sink) = Unit
    override fun stop() = Unit
}

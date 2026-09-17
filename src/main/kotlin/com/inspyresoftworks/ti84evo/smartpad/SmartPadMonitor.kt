package com.inspyresoftworks.ti84evo.smartpad

import java.time.Instant

data class SmartPadReportFrame(
    val timestamp: Instant,
    val interfaceNumber: Int,
    val endpointAddress: Int,
    val raw: ByteArray,
    val decoded: SmartPadDecodeResult,
    val events: List<SmartPadKeyEvent>,
)

/** UI-independent report monitor suitable for CLI, tests, or a future tool window. */
class SmartPadMonitor(
    private val decoder: SmartPadReportDecoder = SmartPadReportDecoder(),
) {
    fun accept(
        raw: ByteArray,
        timestamp: Instant = Instant.now(),
        interfaceNumber: Int = 3,
        endpointAddress: Int = 0x84,
    ): SmartPadReportFrame {
        val decoded = decoder.decode(raw)
        val events = if (decoded is SmartPadDecodeResult.Valid) {
            decoder.accept(raw, timestamp.toEpochMilli() * 1_000_000L)
        } else {
            emptyList()
        }
        return SmartPadReportFrame(
            timestamp = timestamp,
            interfaceNumber = interfaceNumber,
            endpointAddress = endpointAddress,
            raw = raw.copyOf(),
            decoded = decoded,
            events = events,
        )
    }

    fun format(frame: SmartPadReportFrame): String {
        val direction = if (frame.endpointAddress and 0x80 != 0) "IN" else "OUT"
        val reportId = (frame.decoded as? SmartPadDecodeResult.Valid)?.state?.reportId ?: -1
        val state = when {
            frame.decoded is SmartPadDecodeResult.Malformed -> "MALFORMED: ${frame.decoded.reason}"
            frame.events.isEmpty() -> "UNCHANGED"
            else -> frame.events.joinToString(",") {
                val key = it.calculatorKey?.let { calculator -> "$calculator/${it.hostKey}" } ?: it.hostKey
                "$key:${it.transition}"
            }
        }
        return "%s IF%02d %s EP%02X id=%s raw=%s %s".format(
            frame.timestamp,
            frame.interfaceNumber,
            direction,
            frame.endpointAddress,
            if (reportId < 0) "?" else reportId,
            frame.raw.toHex(),
            state,
        )
    }
}

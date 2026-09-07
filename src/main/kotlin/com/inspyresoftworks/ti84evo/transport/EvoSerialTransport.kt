package com.inspyresoftworks.ti84evo.transport

import com.fazecast.jSerialComm.SerialPort
import com.inspyresoftworks.ti84evo.protocol.EvoFrameException
import com.inspyresoftworks.ti84evo.protocol.EvoProtocolException
import com.inspyresoftworks.ti84evo.protocol.EvoTimeoutException
import com.inspyresoftworks.ti84evo.protocol.KermitPacketCodec
import kotlin.math.max

/**
 * jSerialComm-backed CDC transport for the TI-84 Evo.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoSerialTransport(
    private val port: SerialPort,
    private val timeoutMillis: Int = 5_000,
) : EvoTransport {
    override val description: String
        get() = "${port.systemPortName} — ${port.descriptivePortName}"

    override fun open() {
        if (port.isOpen) return

        port.setComPortParameters(
            115200,
            8,
            SerialPort.ONE_STOP_BIT,
            SerialPort.NO_PARITY,
        )
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
        port.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
            timeoutMillis,
            timeoutMillis,
        )

        if (!port.openPort()) {
            throw EvoProtocolException("failed to open ${port.systemPortName}")
        }

        if (!port.setDTRandRTS(true, true)) {
            port.closePort()
            throw EvoProtocolException("failed to assert DTR/RTS on ${port.systemPortName}")
        }
        port.flushIOBuffers()
    }

    override fun close() {
        if (port.isOpen) {
            port.closePort()
        }
    }

    override fun write(data: ByteArray) {
        require(port.isOpen) { "transport is not open" }
        val written = port.writeBytes(data, data.size)
        if (written != data.size) {
            throw EvoProtocolException("short serial write: $written/${data.size} bytes")
        }
    }

    private fun readExact(size: Int, deadlineNanos: Long): ByteArray {
        require(port.isOpen) { "transport is not open" }
        val output = ByteArray(size)
        var offset = 0

        while (offset < size) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) {
                throw EvoTimeoutException("timed out after $offset/$size bytes")
            }

            val remainingMillis = max(1, (remainingNanos / 1_000_000L).toInt())
            port.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
                remainingMillis,
                timeoutMillis,
            )

            val count = port.readBytes(output, size - offset, offset)
            if (count < 0) {
                throw EvoProtocolException("serial read failed on ${port.systemPortName}")
            }
            if (count == 0) continue
            offset += count
        }

        return output
    }

    override fun readPacketBytes(): ByteArray {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var first: Int
        do {
            first = readExact(1, deadline)[0].toInt() and 0xFF
        } while (first != KermitPacketCodec.SOH)

        val lengthMarker = readExact(1, deadline)[0].toInt() and 0xFF
        val prefix = mutableListOf(KermitPacketCodec.SOH.toByte(), lengthMarker.toByte())
        val total = if (lengthMarker != 0x20) {
            if (lengthMarker !in 0x20..0x7E) {
                throw EvoFrameException("invalid short Kermit length byte: 0x%02X".format(lengthMarker))
            }
            lengthMarker - 0x20 + 3
        } else {
            val extendedHeader = readExact(4, deadline)
            extendedHeader.forEach(prefix::add)
            val dataAndCheckLength = KermitPacketCodec.decodeLongPacketLength(
                extendedHeader[2].toInt() and 0xFF,
                extendedHeader[3].toInt() and 0xFF,
            )
            dataAndCheckLength + 8
        }
        if (total > 16 * 1024) {
            throw EvoFrameException("Kermit packet exceeded sane maximum size")
        }

        val prefixBytes = prefix.toByteArray()
        return prefixBytes + readExact(total - prefixBytes.size, deadline)
    }

    companion object {
        const val TI_VENDOR_ID = 0x0451
        const val TI84_EVO_PRODUCT_ID = 0xE018

        fun detectedPorts(): List<SerialPort> = SerialPort.getCommPorts()
            .filter { it.vendorID == TI_VENDOR_ID && it.productID == TI84_EVO_PRODUCT_ID }

        fun auto(timeoutMillis: Int = 5_000): EvoSerialTransport {
            val ports = detectedPorts()
            if (ports.isEmpty()) {
                throw EvoProtocolException("no TI-84 Evo CDC serial port found (VID 0451, PID E018)")
            }
            if (ports.size > 1) {
                throw EvoProtocolException(
                    "multiple TI-84 Evo ports found: ${ports.joinToString { it.systemPortName }}",
                )
            }
            return EvoSerialTransport(ports.single(), timeoutMillis)
        }
    }
}

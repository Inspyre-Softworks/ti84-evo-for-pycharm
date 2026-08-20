package com.inspyresoftworks.ti84evo.transport

import com.fazecast.jSerialComm.SerialPort
import com.inspyresoftworks.ti84evo.protocol.EvoFrameCodec
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

        port.setDTRandRTS(false, false)
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
        val output = mutableListOf<Byte>()

        var value: Int
        do {
            value = readExact(1, deadline)[0].toInt() and 0xFF
        } while (value != EvoFrameCodec.SOH)

        output += value.toByte()
        while (true) {
            value = readExact(1, deadline)[0].toInt() and 0xFF
            output += value.toByte()
            if (value == KermitPacketCodec.CR) break
            if (output.size > 16 * 1024) {
                throw EvoFrameException("Kermit packet exceeded sane maximum size")
            }
        }

        return output.toByteArray()
    }

    override fun readFrameBytes(): ByteArray {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var first: Int
        do {
            first = readExact(1, deadline)[0].toInt() and 0xFF
        } while (first != EvoFrameCodec.SOH)

        val lengthMarker = readExact(1, deadline)[0].toInt() and 0xFF
        val prefix = mutableListOf(EvoFrameCodec.SOH.toByte(), lengthMarker.toByte())

        val total = if (lengthMarker != EvoFrameCodec.PRINTABLE_BASE) {
            if (lengthMarker !in EvoFrameCodec.PRINTABLE_BASE..EvoFrameCodec.PRINTABLE_MAX) {
                throw EvoFrameException("invalid short length byte: 0x%02X".format(lengthMarker))
            }
            lengthMarker - EvoFrameCodec.PRINTABLE_BASE + 3
        } else {
            val extendedHeader = readExact(4, deadline)
            extendedHeader.forEach(prefix::add)
            val span = EvoFrameCodec.decodeBase95Pair(
                extendedHeader[2].toInt() and 0xFF,
                extendedHeader[3].toInt() and 0xFF,
            )
            span + 8
        }

        val prefixBytes = prefix.toByteArray()
        return EvoFrameCodec.concat(prefixBytes, readExact(total - prefixBytes.size, deadline))
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

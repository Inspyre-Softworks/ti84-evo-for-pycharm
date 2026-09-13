package com.inspyresoftworks.ti84evo.cli

import com.inspyresoftworks.ti84evo.transport.EvoSerialTransport
import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant

/** CLI transport factory with opt-in, packet-framed acceptance traces. */
internal object CliTransport {
    const val TRACE_DIRECTORY_PROPERTY = "ti84.evo.trace.dir"

    fun auto(): EvoTransport {
        val serial = EvoSerialTransport.auto()
        val traceDirectory = System.getProperty(TRACE_DIRECTORY_PROPERTY)
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: return serial
        return TracingTransport(serial, traceDirectory)
    }
}

private class TracingTransport(
    private val delegate: EvoTransport,
    private val directory: Path,
) : EvoTransport {
    override val description: String get() = delegate.description

    init {
        Files.createDirectories(directory)
    }

    override fun open() {
        recordEvent("OPEN")
        delegate.open()
    }

    override fun close() {
        runCatching { delegate.close() }
        recordEvent("CLOSE")
    }

    override fun write(data: ByteArray) {
        recordPacket("TX", data)
        delegate.write(data)
    }

    override fun readPacketBytes(): ByteArray = delegate.readPacketBytes().also { recordPacket("RX", it) }

    private fun recordEvent(event: String) = synchronized(TRACE_LOCK) {
        Files.writeString(
            directory.resolve("protocol.log"),
            "${Instant.now()} $event ${delegate.description}\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    private fun recordPacket(direction: String, data: ByteArray) = synchronized(TRACE_LOCK) {
        val digest = MessageDigest.getInstance("SHA-256").digest(data).toHex()
        val summary = buildString {
            append(Instant.now()).append(' ').append(direction)
            append(" bytes=").append(data.size)
            if (data.size >= 4) {
                append(" seq=").append((data[2].toInt() and 0xff) - 0x20)
                append(" type=").append((data[3].toInt() and 0xff).toChar())
            }
            append(" sha256=").append(digest).append('\n')
        }
        Files.writeString(
            directory.resolve("protocol.log"),
            summary,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
        DataOutputStream(
            Files.newOutputStream(
                directory.resolve("${direction.lowercase()}-packets.bin"),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            ),
        ).use { output ->
            output.writeInt(data.size)
            output.write(data)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val TRACE_LOCK = Any()
    }
}

package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport
import java.nio.charset.StandardCharsets

/**
 * Host-to-calculator Python program transfer.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoPythonTransfer(private val transport: EvoTransport) {
    data class Program(
        val programName: String,
        val source: String,
        val archived: Boolean = false,
    )

    data class Result(
        val programName: String,
        val sourceBytes: Int,
        val payloadBytes: Int,
        val packets: Int,
        val archived: Boolean,
    )

    data class ProjectResult(val uploads: List<Result>) {
        val sourceBytes: Int = uploads.sumOf { it.sourceBytes }
        val payloadBytes: Int = uploads.sumOf { it.payloadBytes }
        val packets: Int = uploads.sumOf { it.packets }
    }

    class ProjectUploadException(
        val completedUploads: List<Result>,
        val failedProgramName: String,
        cause: Throwable,
    ) : EvoProtocolException(
        "Project upload stopped at $failedProgramName after ${completedUploads.size} successful file(s): " +
            (cause.message ?: cause.javaClass.simpleName),
        cause,
    )

    private val sendInit = byteArrayOf(
        0x7E, 0x30, 0x20, 0x40, 0x2D, 0x23, 0x59,
        0x31, 0x7E, 0x2E, 0x22, 0x35, 0x4D,
    )

    fun upload(
        programName: String,
        source: String,
        archive: Boolean = false,
        overwrite: Boolean = true,
    ): Result {
        val built = EvoPythonPayload.build(programName, source)
        val packetCount = uploadPayload(
            EvoPythonPayload.transferUrl(built.programName, archive, overwrite),
            built.bytes,
        )

        return Result(
            programName = built.programName,
            sourceBytes = built.sourceBytes,
            payloadBytes = built.bytes.size,
            packets = packetCount,
            archived = archive,
        )
    }

    /** Sends an already-built Evo variable envelope over a complete Kermit transaction. */
    internal fun uploadPayload(url: String, payload: ByteArray): Int =
        uploadPayloads(listOf(url to payload))

    /** Sends one or more payloads in a shared Kermit session. */
    internal fun uploadPayloads(
        payloads: List<Pair<String, ByteArray>>,
        delayBetweenMillis: Long = 0,
    ): Int {
        require(payloads.isNotEmpty()) { "at least one upload payload is required" }
        require(delayBetweenMillis >= 0) { "upload delay cannot be negative" }
        val session = KermitPacketCodec.Session()
        var sequence = 0
        var packetCount = 0

        fun send(type: Char, data: ByteArray = byteArrayOf()) {
            sendPacket(sequence, type, data, session)
            sequence = (sequence + 1) % 64
            packetCount++
        }

        send('S', sendInit)
        payloads.forEachIndexed { index, (url, payload) ->
            send('F', url.toByteArray(StandardCharsets.UTF_8))
            send('A', KermitPacketCodec.buildFileAttributes(payload.size))
            for (chunk in KermitPacketCodec.encodeDataChunks(payload, session.dataChunkSize)) send('D', chunk)
            send('Z')
            if (delayBetweenMillis > 0 && index < payloads.lastIndex) Thread.sleep(delayBetweenMillis)
        }
        send('B')
        return packetCount
    }

    /** Uploads every declared program over the already-open transport. */
    fun uploadProject(
        programs: List<Program>,
        archive: Boolean? = null,
        overwrite: Boolean = true,
        onProgress: (Result, Int, Int) -> Unit = { _, _, _ -> },
    ): ProjectResult {
        require(programs.isNotEmpty()) { "Evo project must contain at least one Python program" }
        require(programs.map { it.programName.uppercase() }.distinct().size == programs.size) {
            "Evo project calculator program names must be unique"
        }

        val completed = mutableListOf<Result>()
        for (program in programs) {
            val result = try {
                upload(
                    programName = program.programName,
                    source = program.source,
                    archive = archive ?: program.archived,
                    overwrite = overwrite,
                )
            } catch (error: Throwable) {
                throw ProjectUploadException(completed.toList(), program.programName, error)
            }
            completed += result
            runCatching { onProgress(result, completed.size, programs.size) }
        }
        return ProjectResult(completed)
    }

    private fun sendPacket(
        sequence: Int,
        type: Char,
        data: ByteArray,
        session: KermitPacketCodec.Session,
    ) {
        val raw = KermitPacketCodec.makePacket(sequence, type, data, session)
        var lastResponse: KermitPacketCodec.Packet? = null

        repeat(3) {
            transport.write(raw)
            val response = KermitPacketCodec.parsePacket(transport.readPacketBytes(), session)
            lastResponse = response

            when (response.type) {
                'Y' -> {
                    if (response.sequence != sequence) {
                        throw EvoUnexpectedFrameException(
                            "ack sequence mismatch for $type: expected $sequence, got ${response.sequence}",
                        )
                    }
                    if (type == 'S') {
                        session.updateFromSendInit(response.data)
                    }
                    return
                }
                'E' -> throw EvoProtocolException(
                    "calculator rejected $type packet: ${transferError(response.data)}",
                )
                'N' -> Unit
                else -> throw EvoUnexpectedFrameException(
                    "expected Y/N/E response to $type, got ${response.type}",
                )
            }
        }

        throw EvoProtocolException(
            "packet $type sequence $sequence was not acknowledged after 3 attempts; " +
                "last response=${lastResponse?.type}",
        )
    }

    private fun transferError(data: ByteArray): String {
        val text = data.toString(StandardCharsets.UTF_8).trim()
        if (text.isNotEmpty() && text.all { !it.isISOControl() }) {
            val meanings = mapOf(
                "PM" to "invalid parameter",
                "NM" to "not enough memory",
                "FL" to "flash/storage error",
                "IN" to "invalid request",
                "NV" to "version too new",
                "VE" to "variable already exists",
                "DP" to "invalid data payload",
                "BZ" to "calculator busy",
                "LB" to "low battery",
                "WT" to "waiting for user",
            )
            val meaning = meanings[text]
            return if (meaning == null) text else "$text ($meaning)"
        }
        return data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }
}

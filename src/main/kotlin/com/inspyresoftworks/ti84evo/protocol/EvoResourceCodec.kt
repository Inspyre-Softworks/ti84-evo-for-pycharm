package com.inspyresoftworks.ti84evo.protocol

/**
 * Resource reconstruction for the confirmed Evo transfer modes.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
object EvoResourceCodec {
    data class ResolvedResource(val bytes: ByteArray, val mode: String)

    fun resolve(expectedLength: Int, decodedData: ByteArray, wireData: ByteArray): ResolvedResource {
        if (decodedData.size == expectedLength) {
            return ResolvedResource(decodedData, "D-unescaped")
        }
        if (wireData.size == expectedLength) {
            return ResolvedResource(wireData, "D-literal")
        }

        decodeScreenCbor(decodedData, expectedLength)?.let { return it }

        throw EvoProtocolException(
            "resource length mismatch: announced $expectedLength, " +
                "unescaped ${decodedData.size}, wire ${wireData.size}",
        )
    }

    fun decodeEvoScreenFfRuns(encoded: ByteArray, expectedLength: Int): ByteArray {
        val output = ArrayList<Byte>(expectedLength)
        var index = 0

        while (index < encoded.size) {
            if (
                index + 2 < encoded.size &&
                (encoded[index].toInt() and 0xFF) == 0x7E &&
                (encoded[index + 1].toInt() and 0xFF) in 0x21..0x7E &&
                (encoded[index + 2].toInt() and 0xFF) == 0xFF
            ) {
                val count = (encoded[index + 1].toInt() and 0xFF) - 0x20
                repeat(count) { output += 0xFF.toByte() }
                index += 3
            } else {
                output += encoded[index]
                index += 1
            }
        }

        if (output.size != expectedLength) {
            throw EvoProtocolException(
                "Evo screen run decode length mismatch: got ${output.size}, expected $expectedLength",
            )
        }
        return output.toByteArray()
    }

    private fun decodeScreenCbor(payload: ByteArray, expectedResourceLength: Int): ResolvedResource? {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != 0xBF) return null

        val marker = byteArrayOf(0x64, 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 0x5A)
        val markerIndex = payload.indexOfSubsequence(marker, limit = 128)
        if (markerIndex < 0) return null

        val lengthOffset = markerIndex + marker.size
        if (lengthOffset + 4 > payload.size) return null

        val framebufferLength =
            ((payload[lengthOffset].toInt() and 0xFF) shl 24) or
                ((payload[lengthOffset + 1].toInt() and 0xFF) shl 16) or
                ((payload[lengthOffset + 2].toInt() and 0xFF) shl 8) or
                (payload[lengthOffset + 3].toInt() and 0xFF)

        val dataStart = lengthOffset + 4
        val prefix = payload.copyOfRange(0, dataStart)
        val expectedTailLength = framebufferLength + 1
        if (prefix.size + expectedTailLength != expectedResourceLength) return null

        val decodedTail = try {
            decodeEvoScreenFfRuns(payload.copyOfRange(dataStart, payload.size), expectedTailLength)
        } catch (_: EvoProtocolException) {
            return null
        }

        if ((decodedTail.lastOrNull()?.toInt()?.and(0xFF)) != 0xFF) return null
        return ResolvedResource(EvoFrameCodec.concat(prefix, decodedTail), "D-unescaped:evo-ff-runs")
    }

    private fun ByteArray.indexOfSubsequence(needle: ByteArray, limit: Int): Int {
        val maxStart = minOf(size - needle.size, limit - needle.size)
        if (maxStart < 0) return -1
        for (start in 0..maxStart) {
            var matches = true
            for (index in needle.indices) {
                if (this[start + index] != needle[index]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }
}

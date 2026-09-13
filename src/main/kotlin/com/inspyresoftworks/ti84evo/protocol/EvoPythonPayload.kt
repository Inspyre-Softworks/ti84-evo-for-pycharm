package com.inspyresoftworks.ti84evo.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Builds the type-15 Evo variable payload used for Python programs.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
object EvoPythonPayload {
    private const val PYTHON_TYPE = 15
    private const val MAX_NAME_LENGTH = 8

    data class Built(
        val programName: String,
        val sourceBytes: Int,
        val bytes: ByteArray,
    )

    data class Decoded(
        val programName: String,
        val source: String,
        val sourceBytes: Int,
        val payloadBytes: Int,
    )

    fun isValidProgramName(name: String): Boolean =
        name.length in 1..MAX_NAME_LENGTH && name.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }

    fun defaultProgramName(fileStem: String): String {
        val cleaned = fileStem.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(MAX_NAME_LENGTH)
        return cleaned.ifEmpty { "PYSCRIPT" }
    }

    fun build(programName: String, source: String): Built {
        require(isValidProgramName(programName)) {
            "Evo Python program name must contain 1–8 letters or digits"
        }

        val normalizedName = programName.uppercase()
        val sourceBytes = source.toByteArray(StandardCharsets.UTF_8)
        val appVar = buildAppVar(normalizedName, sourceBytes)
        val tokenName = tokenEncodeName(normalizedName)

        val payload = ByteArrayOutputStream().apply {
            write(0xBF) // Indefinite map.
            write(cborText("metaData"))
            write(0xBF)
            write(cborText("type"))
            write(cborUnsigned(PYTHON_TYPE))
            write(cborText("version"))
            write(cborUnsigned(1))
            write(cborText("name"))
            write(cborBytes(tokenName))
            write(0xFF)
            write(cborText("version"))
            write(cborUnsigned(1))
            write(cborText("size"))
            write(cborUnsigned(appVar.size))
            write(cborText("data"))
            write(cborBytes(appVar))
            write(0xFF)
        }.toByteArray()

        return Built(normalizedName, sourceBytes.size, payload)
    }

    /** Decode a downloaded type-15 Evo variable envelope back into Python source. */
    fun decode(raw: ByteArray): Decoded {
        val inspection = EvoVariableFile.inspect(raw)
        val type = (inspection.metadata["type"] as? Number)?.toInt()
            ?: throw EvoProtocolException("Python variable metadata is missing its type")
        if (type != PYTHON_TYPE) {
            throw EvoProtocolException("expected Python variable type $PYTHON_TYPE, got $type")
        }

        val appVar = inspection.data
        if (appVar.size < 18 || u(appVar[0]) != 0x13 || u(appVar[1]) != 0x01) {
            throw EvoProtocolException("unrecognized Evo Python AppVar header")
        }
        val declaredTotal = readUInt32Le(appVar, 4)
        val alignmentPadding = appVar.size.toLong() - declaredTotal
        if (alignmentPadding !in 0..MAX_NATIVE_ALIGNMENT_BYTES.toLong()) {
            throw EvoProtocolException(
                "Python AppVar length mismatch: declared $declaredTotal, got ${appVar.size}",
            )
        }

        val nameLength = u(appVar[8])
        val nameStart = 12
        val nameEnd = nameStart + nameLength
        if (nameEnd + 6 > appVar.size || u(appVar[nameEnd]) != 0) {
            throw EvoProtocolException("truncated Evo Python program name")
        }
        val programName = String(appVar, nameStart, nameLength, StandardCharsets.US_ASCII)
        if (!isValidProgramName(programName)) {
            throw EvoProtocolException("invalid Evo Python program name in downloaded payload")
        }

        val sourceLength = readUInt16Le(appVar, nameEnd + 1)
        if (u(appVar[nameEnd + 3]) != 0 || u(appVar[nameEnd + 4]) != 2) {
            throw EvoProtocolException("unrecognized Evo Python source marker")
        }
        val sourceStart = nameEnd + 5
        val sourceEnd = sourceStart + sourceLength
        val trailerLength = appVar.size - sourceEnd
        val declaredSourceEnd = sourceEnd.toLong() + 1
        if (
            declaredTotal != declaredSourceEnd ||
            trailerLength !in 1..MAX_NATIVE_TRAILER_BYTES ||
            appVar[sourceEnd] != 0.toByte()
        ) {
            throw EvoProtocolException("Python source length does not match downloaded payload")
        }
        val sourceBytes = appVar.copyOfRange(sourceStart, sourceEnd)
        val source = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(sourceBytes))
                .toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw EvoProtocolException("downloaded Python source is not valid UTF-8", error)
        }
        return Decoded(programName.uppercase(), source, sourceBytes.size, raw.size)
    }

    fun transferUrl(programName: String, archive: Boolean = false, overwrite: Boolean = true): String {
        require(isValidProgramName(programName)) { "invalid Evo Python program name" }
        val target = if (archive) 1 else 0
        val policy = if (overwrite) 1 else 0
        return "hh01/xfr/var?name=${urlEncodeName(programName.uppercase())}&type=$PYTHON_TYPE&memtarget=$target&policy=$policy"
    }

    private fun buildAppVar(programName: String, source: ByteArray): ByteArray {
        val name = programName.toByteArray(StandardCharsets.US_ASCII)
        val total = 18 + name.size + source.size
        require(source.size <= 0xFFFF) { "Python source exceeds the Evo 16-bit source-length field" }
        require(total <= 0xFFFF) { "Python program is too large for this Evo transfer format" }

        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x13, 0x01, 0x00, 0x00))
            writeUInt32Le(total)
            write(name.size)
            write(byteArrayOf(0x00, 0x00, 0x00))
            write(name)
            write(0x00)
            writeUInt16Le(source.size)
            write(byteArrayOf(0x00, 0x02))
            write(source)
            write(0x00)
        }.toByteArray()
    }

    private fun tokenEncodeName(name: String): ByteArray = ByteArrayOutputStream().apply {
        for (char in name) {
            val token = when (char) {
                in 'A'..'Z' -> 0xE800 + (char - 'A')
                in '0'..'9' -> 0xE401 + (char - '0')
                else -> error("unsupported Python program name character: $char")
            }
            writeUInt16Le(token)
        }
    }.toByteArray()

    private fun urlEncodeName(name: String): String = buildString {
        for (char in name) {
            val token = when (char) {
                in 'A'..'Z' -> 0xE800 + (char - 'A')
                in '0'..'9' -> 0xE401 + (char - '0')
                else -> error("unsupported Python program name character: $char")
            }
            val utf8 = token.toChar().toString().toByteArray(StandardCharsets.UTF_8)
            for (byte in utf8) {
                append("%%%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    private fun cborText(value: String): ByteArray {
        val data = value.toByteArray(StandardCharsets.UTF_8)
        return KermitPacketCodec.concat(cborLength(3, data.size), data)
    }

    private fun cborBytes(value: ByteArray): ByteArray =
        KermitPacketCodec.concat(cborLength(2, value.size), value)

    private fun cborUnsigned(value: Int): ByteArray {
        require(value >= 0)
        return when {
            value < 24 -> byteArrayOf(value.toByte())
            value <= 0xFF -> byteArrayOf(0x18, value.toByte())
            value <= 0xFFFF -> byteArrayOf(0x19, (value shr 8).toByte(), value.toByte())
            else -> byteArrayOf(
                0x1A,
                (value shr 24).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte(),
            )
        }
    }

    private fun cborLength(majorType: Int, size: Int): ByteArray = when {
        size < 24 -> byteArrayOf(((majorType shl 5) or size).toByte())
        size <= 0xFF -> byteArrayOf(((majorType shl 5) or 24).toByte(), size.toByte())
        size <= 0xFFFF -> byteArrayOf(
            ((majorType shl 5) or 25).toByte(),
            (size shr 8).toByte(),
            size.toByte(),
        )
        else -> byteArrayOf(
            ((majorType shl 5) or 26).toByte(),
            (size shr 24).toByte(),
            (size shr 16).toByte(),
            (size shr 8).toByte(),
            size.toByte(),
        )
    }

    private fun ByteArrayOutputStream.writeUInt16Le(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeUInt32Le(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun readUInt16Le(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > data.size) throw EvoProtocolException("truncated 16-bit field")
        return u(data[offset]) or (u(data[offset + 1]) shl 8)
    }

    private fun readUInt32Le(data: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > data.size) throw EvoProtocolException("truncated 32-bit field")
        return u(data[offset]).toLong() or
            (u(data[offset + 1]).toLong() shl 8) or
            (u(data[offset + 2]).toLong() shl 16) or
            (u(data[offset + 3]).toLong() shl 24)
    }

    private fun u(value: Byte): Int = value.toInt() and 0xFF

    private const val MAX_NATIVE_ALIGNMENT_BYTES = 3
    private const val MAX_NATIVE_TRAILER_BYTES = 4
}

package com.inspyresoftworks.ti84evo.protocol

import java.io.ByteArrayOutputStream
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
}

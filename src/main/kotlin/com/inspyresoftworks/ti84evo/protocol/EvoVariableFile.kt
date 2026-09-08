package com.inspyresoftworks.ti84evo.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Inspection and export helpers for downloaded Evo variable envelopes. */
object EvoVariableFile {
    data class Inspection(
        val metadata: Map<String, Any?>,
        val fields: Map<String, Any?>,
        val data: ByteArray,
        val rawBytes: Int,
    )

    fun inspect(raw: ByteArray): Inspection {
        val decoded = CborReader(raw).readComplete() as? Map<*, *>
            ?: throw EvoProtocolException("variable transfer did not decode to a CBOR map")
        val metadata = (decoded["metaData"] as? Map<*, *>)?.entries?.associate { (key, value) ->
            key.toString() to value
        }.orEmpty()
        val fields = decoded.entries
            .filter { (key, _) -> key != "metaData" }
            .associate { (key, value) -> key.toString() to value }
        val data = decoded["data"] as? ByteArray ?: byteArrayOf()
        return Inspection(metadata, fields, data, raw.size)
    }

    fun addChecksum(raw: ByteArray): ByteArray {
        val adjusted = raw.size - 3
        var wordCount = adjusted.coerceAtLeast(0) shr 1
        if ((adjusted and 1) != 0 && wordCount > 0) wordCount--
        var checksum = 0
        repeat(wordCount) { index ->
            checksum = checksum xor ((raw[index * 2].toInt() and 0xFF) or ((raw[index * 2 + 1].toInt() and 0xFF) shl 8))
        }
        return raw + byteArrayOf((checksum shr 8).toByte(), checksum.toByte())
    }

    /** Rebuilds a native variable envelope with a different calculator-tokenized name. */
    fun retargetName(raw: ByteArray, tokenName: ByteArray): ByteArray {
        require(tokenName.isNotEmpty()) { "target variable name cannot be empty" }
        val decoded = CborReader(raw).readComplete() as? Map<*, *>
            ?: throw EvoProtocolException("variable transfer did not decode to a CBOR map")
        val metadata = decoded["metaData"] as? Map<*, *>
            ?: throw EvoProtocolException("variable transfer is missing its metadata map")
        val renamedMetadata = LinkedHashMap<Any?, Any?>(metadata).apply {
            this["name"] = tokenName.copyOf()
        }
        val renamed = LinkedHashMap<Any?, Any?>(decoded).apply {
            this["metaData"] = renamedMetadata
        }
        return encodeCbor(renamed)
    }

    /** Rebuilds a native list envelope as an empty list while preserving its identity metadata. */
    fun clearList(raw: ByteArray): ByteArray {
        val decoded = CborReader(raw).readComplete() as? Map<*, *>
            ?: throw EvoProtocolException("variable transfer did not decode to a CBOR map")
        val metadata = decoded["metaData"] as? Map<*, *>
            ?: throw EvoProtocolException("variable transfer is missing its metadata map")
        val type = (metadata["type"] as? Number)?.toInt()
        if (type != LIST_TYPE) throw EvoProtocolException("expected native list type $LIST_TYPE, got $type")

        val cleared = LinkedHashMap<Any?, Any?>(decoded).apply {
            this["len"] = 0L
            remove("arraylen")
            remove("size")
            remove("data")
        }
        return encodeCbor(cleared)
    }

    private fun encodeCbor(value: Any?): ByteArray = ByteArrayOutputStream().apply {
        when (value) {
            null -> write(0xF6)
            is Boolean -> write(if (value) 0xF5 else 0xF4)
            is ByteArray -> {
                write(cborLength(2, value.size.toLong()))
                write(value)
            }
            is String -> {
                val bytes = value.toByteArray(StandardCharsets.UTF_8)
                write(cborLength(3, bytes.size.toLong()))
                write(bytes)
            }
            is Number -> {
                val number = value.toLong()
                if (number >= 0) {
                    write(cborLength(0, number))
                } else {
                    write(cborLength(1, -1L - number))
                }
            }
            is List<*> -> {
                write(cborLength(4, value.size.toLong()))
                value.forEach { write(encodeCbor(it)) }
            }
            is Map<*, *> -> {
                // Native Evo variable envelopes use indefinite-length maps. The
                // firmware rejects an otherwise equivalent definite-length rebuild.
                write(0xBF)
                value.forEach { (key, item) ->
                    write(encodeCbor(key))
                    write(encodeCbor(item))
                }
                write(0xFF)
            }
            else -> throw EvoProtocolException("cannot encode CBOR value ${value.javaClass.simpleName}")
        }
    }.toByteArray()

    private fun cborLength(majorType: Int, value: Long): ByteArray = when {
        value < 24 -> byteArrayOf(((majorType shl 5) or value.toInt()).toByte())
        value <= 0xFF -> byteArrayOf(((majorType shl 5) or 24).toByte(), value.toByte())
        value <= 0xFFFF -> byteArrayOf(
            ((majorType shl 5) or 25).toByte(),
            (value shr 8).toByte(),
            value.toByte(),
        )
        value <= 0xFFFF_FFFFL -> byteArrayOf(
            ((majorType shl 5) or 26).toByte(),
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte(),
        )
        else -> byteArrayOf(
            ((majorType shl 5) or 27).toByte(),
            (value shr 56).toByte(),
            (value shr 48).toByte(),
            (value shr 40).toByte(),
            (value shr 32).toByte(),
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte(),
        )
    }

    private const val LIST_TYPE = 1
}

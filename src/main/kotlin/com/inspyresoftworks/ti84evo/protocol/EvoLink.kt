package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoScreenCapture
import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport

/**
 * High-level TI-84 Evo resource client.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoLink(transport: EvoTransport) {
    private val transactions = EvoTransactionEngine(transport)

    fun getResource(uri: String): ByteArray {
        val request = buildGetRequest(uri)
        return try {
            transactions.sendSmallTransaction(request, byteArrayOf('h'.code.toByte()))
            transactions.receiveTransaction().second
        } catch (error: EvoProtocolException) {
            throw EvoProtocolException(
                "resource request ${request.decodeToString()} failed: ${error.message}",
                error,
            )
        }
    }

    fun getAttributes(): Map<String, Any?> = decodeStringMap(getResource("sys/attributes"))

    fun getDirectory(): List<EvoDirectoryEntry> = EvoDirectoryCodec.decode(
        getResource("hh01/inf/res?name=directory&gotohome=1"),
    )

    fun deleteVariables(entries: List<EvoDirectoryEntry>): List<EvoDirectoryEntry> {
        val deletedEntries = mutableListOf<EvoDirectoryEntry>()

        for (entry in entries) {
            try {
                deleteVariable(entry)
                deletedEntries += entry
            } catch (error: RuntimeException) {
                throw EvoVariableDeleteException(entry, deletedEntries.toList(), error)
            }
        }

        return deletedEntries
    }

    fun getScreenCapture(): EvoScreenCapture {
        val screen = decodeStringMap(getResource("sys/screen"))
        val width = screen.requireInt("width")
        val height = screen.requireInt("height")
        val bpp = screen.requireInt("bpp")
        val framebuffer = screen["data"] as? ByteArray
            ?: throw EvoProtocolException("sys/screen data field is not a byte string")

        val expected = width * height * ((bpp + 7) / 8)
        if (framebuffer.size != expected) {
            throw EvoProtocolException(
                "framebuffer length mismatch: got ${framebuffer.size}, expected $expected for ${width}x${height}x$bpp",
            )
        }

        return EvoScreenCapture(width, height, bpp, framebuffer, screen - "data")
    }

    private fun deleteVariable(entry: EvoDirectoryEntry) {
        val request = buildDeleteRequest(entry)
        try {
            transactions.sendSmallTransaction(request, byteArrayOf(0))
        } catch (error: EvoProtocolException) {
            throw EvoProtocolException(
                "variable delete request ${request.decodeToString()} failed: ${error.message}",
                error,
            )
        }
    }

    private fun decodeStringMap(raw: ByteArray): Map<String, Any?> {
        val value = CborReader(raw).readComplete()
        val map = value as? Map<*, *> ?: throw EvoProtocolException("CBOR resource did not decode to a map")
        return map.entries.associate { (key, item) ->
            (key as? String ?: throw EvoProtocolException("CBOR map contains a non-string key")) to item
        }
    }

    private fun Map<String, Any?>.requireInt(key: String): Int {
        val number = this[key] as? Number ?: throw EvoProtocolException("missing or non-numeric $key")
        return number.toInt()
    }
}

internal fun buildGetRequest(uri: String): ByteArray {
    val clean = uri.trim('/')
    val resource = if (clean.startsWith("hh01/")) clean else "hh01/$clean"
    return "hh01/get/$resource".encodeToByteArray()
}

internal fun buildDeleteRequest(entry: EvoDirectoryEntry): ByteArray {
    val encodedName = encodeTokenName(entry.tokenName)
    if (encodedName.isEmpty()) {
        throw EvoProtocolException("cannot delete ${entry.name}: calculator returned an empty tokenized name")
    }
    return "hh01/del/var?name=$encodedName&type=${entry.type}".encodeToByteArray()
}

private fun encodeTokenName(tokenName: ByteArray): String = buildString {
    var index = 0
    while (index + 1 < tokenName.size) {
        val word = (tokenName[index].toInt() and 0xFF) or
            ((tokenName[index + 1].toInt() and 0xFF) shl 8)
        if (word == 0) break
        word.toChar().toString().encodeToByteArray().forEach { byte ->
            append("%%%02X".format(byte.toInt() and 0xFF))
        }
        index += 2
    }
}

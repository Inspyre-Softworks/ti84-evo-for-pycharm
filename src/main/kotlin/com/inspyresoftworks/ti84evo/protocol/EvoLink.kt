package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoScreenCapture
import com.inspyresoftworks.ti84evo.transport.EvoTransport

/**
 * High-level TI-84 Evo resource client.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoLink(transport: EvoTransport) {
    private val transactions = EvoTransactionEngine(transport)

    private fun normalizeResource(uri: String): String {
        val clean = uri.trim('/')
        return if (clean.startsWith("hh01/")) clean else "hh01/$clean"
    }

    fun getResource(uri: String): ByteArray {
        val resource = normalizeResource(uri)
        val request = "hh01/get/$resource".encodeToByteArray()
        transactions.sendSmallTransaction(request, byteArrayOf('h'.code.toByte()))
        return transactions.receiveTransaction().second
    }

    fun getAttributes(): Map<String, Any?> = decodeStringMap(getResource("hh01/sys/attributes"))

    fun getScreenCapture(): EvoScreenCapture {
        val screen = decodeStringMap(getResource("hh01/sys/screen"))
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

package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoScreenCapture
import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.transport.EvoTransport

/**
 * High-level TI-84 Evo resource client.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoLink(private val transport: EvoTransport) {
    private val transactions = EvoTransactionEngine(transport)
    private val sender = EvoPythonTransfer(transport)

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

    fun getVariable(entry: EvoDirectoryEntry): ByteArray =
        getResource("hh01/xfr/${buildVariableResourceName(entry)}")

    fun deleteVariables(
        entries: List<EvoDirectoryEntry>,
        onProgress: (EvoDirectoryEntry, Int, Int) -> Unit = { _, _, _ -> },
    ): List<EvoDirectoryEntry> {
        val completedEntries = mutableListOf<EvoDirectoryEntry>()

        for (entry in entries) {
            try {
                val completedEntry = if (isPersistentBuiltInList(entry)) {
                    clearBuiltInList(entry)
                } else {
                    deleteVariable(entry)
                    entry
                }
                completedEntries += completedEntry
                runCatching { onProgress(completedEntry, completedEntries.size, entries.size) }
            } catch (error: RuntimeException) {
                throw EvoVariableDeleteException(entry, completedEntries.toList(), error)
            }
        }

        return completedEntries
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
        var lastFailure: RuntimeException? = null

        repeat(DELETE_ATTEMPTS) { attempt ->
            reconnect()
            try {
                // Delete uses the same negotiated Kermit data encoding as the
                // calculator-tested variable upload path. A raw NUL payload can be
                // acknowledged without the firmware actually applying the request.
                sender.uploadPayload(request.decodeToString(), byteArrayOf(0))
            } catch (error: RuntimeException) {
                // The calculator can apply a delete without returning the final ACK.
                // Verify the directory before deciding whether this attempt failed.
                lastFailure = error
            }

            val remains = try {
                reconnect()
                getDirectory().any { candidate -> candidate.sameVariableAs(entry) }
            } catch (error: RuntimeException) {
                lastFailure = error
                if (attempt == DELETE_ATTEMPTS - 1) {
                    throw EvoProtocolException(
                        "could not verify deletion of ${entry.name}: ${error.message}",
                        error,
                    )
                }
                return@repeat
            }

            if (!remains) return
            lastFailure = EvoProtocolException("calculator still reports ${entry.name} after the delete request")
        }

        throw EvoProtocolException(
            "calculator still reports ${entry.name} after $DELETE_ATTEMPTS delete attempts",
            lastFailure,
        )
    }

    private fun clearBuiltInList(entry: EvoDirectoryEntry): EvoDirectoryEntry {
        reconnect()
        val raw = getVariable(entry)
        val currentValue = EvoVariableDecoder.decode(raw, entry.type, entry.name, entry.archived)
        if (currentValue.value.isBlank()) {
            restoreDefaultListEditor()
            return entry
        }

        val builtInTokenName = entry.tokenName.dropLastWhile { it == 0.toByte() }.toByteArray()
        val clearedFile = EvoVariableFile.addChecksum(
            EvoVariableFile.retargetName(EvoVariableFile.clearList(raw), builtInTokenName),
        )
        deleteVariable(entry)
        var lastFailure: RuntimeException? = null

        repeat(DELETE_ATTEMPTS) { attempt ->
            reconnect()
            try {
                sender.uploadPayload(EvoVariablePayload.transferUrl(entry.archived), clearedFile)
            } catch (error: RuntimeException) {
                lastFailure = error
            }

            try {
                reconnect()
                val current = getDirectory().singleOrNull { candidate -> candidate.sameVariableAs(entry) }
                if (current != null) {
                    reconnect()
                    val decoded = EvoVariableDecoder.decode(
                        getVariable(current),
                        current.type,
                        current.name,
                        current.archived,
                    )
                    if (decoded.value.isBlank()) {
                        restoreDefaultListEditor()
                        return current
                    }
                    lastFailure = EvoProtocolException("calculator still reports values in ${entry.name}")
                } else {
                    lastFailure = EvoProtocolException("calculator no longer reports built-in list ${entry.name}")
                }
            } catch (error: RuntimeException) {
                lastFailure = error
                if (attempt == DELETE_ATTEMPTS - 1) {
                    throw EvoProtocolException(
                        "could not verify that ${entry.name} was cleared: ${error.message}",
                        error,
                    )
                }
            }
        }

        throw EvoProtocolException("calculator did not retain ${entry.name} as an empty list", lastFailure)
    }

    private fun restoreDefaultListEditor() {
        reconnect()
        EvoListEditor(transport).restoreDefaultColumns()
    }

    private fun EvoDirectoryEntry.sameVariableAs(other: EvoDirectoryEntry): Boolean {
        if (type != other.type) return false
        return if (tokenName.isNotEmpty() && other.tokenName.isNotEmpty()) {
            tokenName.contentEquals(other.tokenName)
        } else {
            name.equals(other.name, ignoreCase = true)
        }
    }

    private fun reconnect() {
        transport.close()
        transport.open()
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

    private companion object {
        const val DELETE_ATTEMPTS = 2
    }
}

internal fun buildGetRequest(uri: String): ByteArray {
    val clean = uri.trim('/')
    val resource = if (clean.startsWith("hh01/")) clean else "hh01/$clean"
    return "hh01/get/$resource".encodeToByteArray()
}

internal fun buildDeleteRequest(entry: EvoDirectoryEntry): ByteArray {
    val resource = buildVariableResourceName(entry)
    return DELETE_ROUTE.format(resource).encodeToByteArray()
}

internal fun buildVariableResourceName(entry: EvoDirectoryEntry): String {
    val encodedName = encodeTokenName(entry.tokenName)
    if (encodedName.isEmpty()) {
        throw EvoProtocolException("calculator returned an empty tokenized variable name for ${entry.name}")
    }
    return "var?name=$encodedName&type=${entry.type}"
}

private const val DELETE_ROUTE = "hh01/del/%s"

fun isPersistentBuiltInList(entry: EvoDirectoryEntry): Boolean =
    entry.type == 1 && isPersistentBuiltInListName(entry.name)

internal fun encodeTokenName(tokenName: ByteArray): String = buildString {
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

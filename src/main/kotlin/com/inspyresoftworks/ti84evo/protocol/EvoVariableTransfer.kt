package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport

/** Uploads editable data envelopes and complete Evo variable files. */
class EvoVariableTransfer(transport: EvoTransport) {
    data class Result(
        val description: String,
        val payloadBytes: Int,
        val packets: Int,
        val archived: Boolean,
        val preservedListEditor: Boolean = false,
        val restoredBuiltInListEditor: Boolean = false,
    )

    data class ArchiveResult(
        val entry: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry,
        val payloadBytes: Int,
        val packets: Int,
    )

    private val transport = transport
    private val sender = EvoPythonTransfer(transport)
    private val link = EvoLink(transport)

    fun uploadEditable(value: EvoVariablePayload.EditableValue): Result {
        val result = if (value.kind == EvoVariablePayload.Kind.LIST) {
            val directory = link.getDirectory()
            val existing = directory.singleOrNull {
                it.type == EvoVariablePayload.Kind.LIST.typeId && it.name.equals(value.name, ignoreCase = true)
            }
            if (existing != null) {
                replaceListNatively(value, existing, directory.map { it.name })
            } else {
                reconnect()
                uploadEditableDirect(value)
            }
        } else {
            uploadEditableDirect(value)
        }
        if (value.kind == EvoVariablePayload.Kind.LIST && isPersistentBuiltInListName(value.name)) {
            reconnect()
            val editorPackets = EvoListEditor(transport).restoreDefaultColumns()
            return result.copy(
                packets = result.packets + editorPackets,
                restoredBuiltInListEditor = true,
            )
        }
        return result
    }

    private fun uploadEditableDirect(value: EvoVariablePayload.EditableValue): Result {
        val payload = EvoVariablePayload.build(value)
        val packets = sender.uploadPayload(EvoVariablePayload.transferUrl(value.archived), payload)
        return Result(
            description = "${value.kind.wireName} ${value.name.uppercase()}",
            payloadBytes = payload.size,
            packets = packets,
            archived = value.archived,
        )
    }

    private fun replaceListNatively(
        value: EvoVariablePayload.EditableValue,
        existing: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry,
        namesInUse: List<String>,
    ): Result {
        val temporaryName = temporaryListName(namesInUse)
        val temporaryValue = value.copy(name = temporaryName, archived = false)
        reconnect()
        val temporaryUpload = uploadEditableDirect(temporaryValue)
        var temporaryEntry: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry? = null
        var temporaryRemoved = false

        try {
            reconnect()
            temporaryEntry = link.getDirectory().singleOrNull {
                it.type == EvoVariablePayload.Kind.LIST.typeId && it.name.equals(temporaryName, ignoreCase = true)
            } ?: throw EvoProtocolException("temporary list $temporaryName was not returned by the calculator")
            reconnect()
            val nativeTemporary = link.getVariable(temporaryEntry)
            val nativeTarget = EvoVariableFile.addChecksum(
                EvoVariableFile.retargetName(nativeTemporary, existing.tokenName),
            )

            // Remove only the unregistered scratch list before replacing the target. The
            // native type-1 replacement keeps the calculator's List Editor column binding.
            deleteTemporaryList(temporaryEntry)
            temporaryEntry = null
            temporaryRemoved = true
            reconnect()
            val packets = sender.uploadPayload(EvoVariablePayload.transferUrl(value.archived), nativeTarget)
            return Result(
                description = "${value.kind.wireName} ${value.name.uppercase()}",
                payloadBytes = nativeTarget.size,
                packets = temporaryUpload.packets + packets,
                archived = value.archived,
                preservedListEditor = true,
            )
        } finally {
            if (!temporaryRemoved) {
                val cleanupEntry = temporaryEntry ?: runCatching {
                    reconnect()
                    link.getDirectory().singleOrNull {
                        it.type == EvoVariablePayload.Kind.LIST.typeId &&
                            it.name.equals(temporaryName, ignoreCase = true)
                    }
                }.getOrNull()
                cleanupEntry?.let { runCatching { deleteTemporaryList(it) } }
            }
        }
    }

    fun uploadImage(image: EvoImagePayload.Built, archived: Boolean): Result {
        val packets = sender.uploadPayload(EvoImagePayload.transferUrl(image.name, archived), image.bytes)
        return Result("Image ${image.name}", image.bytes.size, packets, archived)
    }

    fun uploadFile(fileName: String, bytes: ByteArray, archived: Boolean): Result {
        require(bytes.isNotEmpty()) { "Calculator variable file is empty" }
        val url = "hh01/xfr/var?memtarget=${if (archived) 1 else 0}&policy=1"
        val packets = sender.uploadPayload(url, bytes)
        return Result(fileName, bytes.size, packets, archived)
    }

    /** Re-uploads existing RAM variables to Archive and verifies their new location. */
    fun archiveVariables(
        entries: List<com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry>,
        onProgress: (ArchiveResult, Int, Int) -> Unit = { _, _, _ -> },
    ): List<ArchiveResult> {
        val candidates = entries.filterNot { it.archived }
        val completed = mutableListOf<ArchiveResult>()
        for (entry in candidates) {
            val result = try {
                val raw = link.getVariable(entry)
                val file = EvoVariableFile.addChecksum(raw)
                reconnect()
                val packets = sender.uploadPayload(archiveTransferUrl(), file)
                reconnect()
                val archivedEntry = link.getDirectory().singleOrNull {
                    it.type == entry.type &&
                        it.archived &&
                        it.tokenName.contentEquals(entry.tokenName)
                } ?: throw EvoProtocolException(
                    "calculator did not report ${entry.name} in Archive after the transfer",
                )
                ArchiveResult(archivedEntry, file.size, packets)
            } catch (error: Throwable) {
                throw EvoVariableArchiveException(entry, completed.map { it.entry }, error)
            }
            completed += result
            runCatching { onProgress(result, completed.size, candidates.size) }
        }
        return completed
    }

    internal fun archiveTransferUrl(): String = "hh01/xfr/var?memtarget=1&policy=1"

    internal fun temporaryListName(namesInUse: Collection<String>): String {
        val occupied = namesInUse.mapTo(mutableSetOf()) { it.uppercase() }
        return (0..9999)
            .asSequence()
            .map { "Z%04d".format(it) }
            .firstOrNull { it !in occupied }
            ?: throw EvoProtocolException("no temporary calculator list name is available")
    }

    private fun deleteTemporaryList(entry: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry) {
        link.deleteVariables(listOf(entry))
    }

    private fun reconnect() {
        transport.close()
        transport.open()
    }
}

internal fun isPersistentBuiltInListName(name: String): Boolean =
    name.uppercase() in PERSISTENT_BUILT_IN_LIST_NAMES

private val PERSISTENT_BUILT_IN_LIST_NAMES = (1..6).mapTo(mutableSetOf()) { "L$it" }
